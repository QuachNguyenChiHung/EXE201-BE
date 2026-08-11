package com.ailogis.api.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private static final int SEND_TIME_LIMIT_MS = 10000;
    private static final int BUFFER_SIZE_LIMIT_BYTES = 50000;

    // Bump thu cong moi khi co thay doi dang chu y - dung de FE xac nhan dang ket noi dung backend build nao.
    private static final String APP_VERSION = "1.0.0";
    private static final String APP_UPDATED_DATE = "2026-08-11";

    private final ObjectMapper objectMapper;

    private final Map<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();
    private final Set<WebSocketSession> allSessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get("userId");
        WebSocketSession wrapped = new ConcurrentWebSocketSessionDecorator(
                session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT_BYTES);

        allSessions.add(wrapped);
        if (userId != null) {
            userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(wrapped);
        }
        log.info("WebSocket kết nối: sessionId={}, userId={}, tổng session={}, tổng user online={}",
                session.getId(), userId, allSessions.size(), userSessions.size());

        sendServerInfo(wrapped);
    }

    // Gửi một lần ngay khi session mở, để FE xác nhận đang kết nối đúng backend build nào.
    private void sendServerInfo(WebSocketSession session) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "SERVER_INFO");
        message.put("version", APP_VERSION);
        message.put("updatedDate", APP_UPDATED_DATE);

        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
        } catch (IOException e) {
            log.warn("Không thể gửi SERVER_INFO tới session {}: {}", session.getId(), e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        allSessions.removeIf(s -> s.getId().equals(session.getId()));

        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            userSessions.computeIfPresent(userId, (id, sessions) -> {
                sessions.removeIf(s -> s.getId().equals(session.getId()));
                return sessions.isEmpty() ? null : sessions;
            });
        }
        log.info("WebSocket đóng: sessionId={}, userId={}, status={}, tổng session còn lại={}",
                session.getId(), userId, status, allSessions.size());
    }

    public void sendNotificationToUser(Long userId, Object payload) {
        runAfterCommit(() -> {
            Set<WebSocketSession> sessions = userSessions.getOrDefault(userId, Set.of());
            if (sessions.isEmpty()) {
                log.info("Bỏ qua push NOTIFICATION cho userId={}: không có session nào đang mở", userId);
                return;
            }

            Map<String, Object> message = new LinkedHashMap<>();
            message.put("type", "NOTIFICATION");
            message.put("payload", payload);

            log.info("Gửi NOTIFICATION tới userId={} ({} session)", userId, sessions.size());
            broadcast(sessions, message);
        });
    }

    public void broadcastWarehouseStatusChanged(Long warehouseId, String status) {
        runAfterCommit(() -> {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("type", "WAREHOUSE_STATUS_CHANGED");
            message.put("warehouseId", warehouseId);
            message.put("status", status);

            log.info("Broadcast WAREHOUSE_STATUS_CHANGED: warehouseId={}, status={}, tổng session={}, userId online={}",
                    warehouseId, status, allSessions.size(), userSessions.keySet());
            broadcast(allSessions, message);
        });
    }

    /**
     * Signals that a warehouse's details (name, description, sections, price
     * tiers, images, certificates, etc.) changed — as opposed to just its
     * status. Carries no field-level diff; clients that care (e.g. a renter
     * viewing that warehouse's detail page) should refetch the full detail.
     */
    public void broadcastWarehouseUpdated(Long warehouseId) {
        runAfterCommit(() -> {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("type", "WAREHOUSE_UPDATED");
            message.put("warehouseId", warehouseId);

            log.info("Broadcast WAREHOUSE_UPDATED: warehouseId={}, tổng session={}, userId online={}",
                    warehouseId, allSessions.size(), userSessions.keySet());
            broadcast(allSessions, message);
        });
    }

    /**
     * Callers of the push methods above are almost always still inside an
     * open {@code @Transactional} method (the proxy commits only after the
     * method returns). Sending immediately would let a client's refetch race
     * ahead of the commit and read stale (pre-update) data under
     * READ_COMMITTED isolation — silently, with no error. Deferring the
     * actual send until the enclosing transaction commits (or running it
     * immediately if there is none) closes that race for every push.
     */
    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private void broadcast(Set<WebSocketSession> sessions, Map<String, Object> message) {
        String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (IOException e) {
            log.error("Không thể serialize thông điệp WebSocket: {}", e.getMessage());
            return;
        }

        TextMessage textMessage = new TextMessage(json);
        int sent = 0;
        int stale = 0;
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                // Server hasn't been notified this session died yet (no clean
                // close frame received) — drop it here instead of silently
                // skipping, so it stops being counted as "connected" and
                // future broadcasts don't keep trying it.
                stale++;
                removeSession(session);
                continue;
            }
            try {
                session.sendMessage(textMessage);
                sent++;
            } catch (IOException e) {
                log.warn("Không thể gửi thông điệp WebSocket tới session {}: {}", session.getId(), e.getMessage());
                removeSession(session);
            }
        }
        log.info("Kết quả gửi: {} thành công, {} session đã đóng (đã dọn dẹp)", sent, stale);
    }

    private void removeSession(WebSocketSession session) {
        allSessions.removeIf(s -> s.getId().equals(session.getId()));
        userSessions.forEach((userId, set) -> set.removeIf(s -> s.getId().equals(session.getId())));
        userSessions.entrySet().removeIf(e -> e.getValue().isEmpty());
    }
}
