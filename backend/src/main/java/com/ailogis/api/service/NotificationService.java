package com.ailogis.api.service;

import com.ailogis.api.dto.NotificationResponseDTO;
import com.ailogis.api.entity.Notification;
import com.ailogis.api.repository.NotificationRepository;
import com.ailogis.api.ws.NotificationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationWebSocketHandler notificationWebSocketHandler;

    public void saveAndNotify(Long userId, String message) {
        Notification notification = Notification.builder()
                .userId(userId)
                .message(message)
                .createdAt(LocalDateTime.now())
                .read(false)
                .build();
        Notification saved = notificationRepository.save(notification);

        notificationWebSocketHandler.sendNotificationToUser(userId,
                new NotificationResponseDTO(saved.getId(), saved.getMessage(), saved.getCreatedAt(), saved.getRead()));
    }

    public Page<NotificationResponseDTO> getNotifications(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(n -> new NotificationResponseDTO(n.getId(), n.getMessage(), n.getCreatedAt(), n.getRead()));
    }

    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    public void markAllRead(Long userId) {
        List<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        for (Notification n : notifications) {
            if (!Boolean.TRUE.equals(n.getRead())) {
                n.setRead(true);
            }
        }
        notificationRepository.saveAll(notifications);
    }
}
