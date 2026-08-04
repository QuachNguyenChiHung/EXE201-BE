package com.ailogis.api.ws;

import com.ailogis.api.security.CustomUserDetails;
import com.ailogis.api.security.CustomUserDetailsService;
import com.ailogis.api.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtils jwtUtils;
    private final CustomUserDetailsService customUserDetailsService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {
        try {
            if (!(request instanceof ServletServerHttpRequest servletRequest)) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            String token = servletRequest.getServletRequest().getParameter("token");
            if (!StringUtils.hasText(token)) {
                log.warn("WebSocket handshake bị từ chối: thiếu token, uri={}", request.getURI());
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            String username = jwtUtils.extractUsername(token);
            CustomUserDetails userDetails = (CustomUserDetails) customUserDetailsService.loadUserByUsername(username);

            if (!jwtUtils.isTokenValid(token, userDetails)) {
                log.warn("WebSocket handshake bị từ chối: token không hợp lệ, user={}", username);
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            log.info("WebSocket handshake thành công: userId={}, email={}", userDetails.getUser().getId(), username);
            attributes.put("userId", userDetails.getUser().getId());
            return true;
        } catch (Exception e) {
            log.warn("WebSocket handshake bị từ chối: {}", e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}
