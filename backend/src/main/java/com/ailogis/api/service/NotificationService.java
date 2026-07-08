package com.ailogis.api.service;

import com.ailogis.api.dto.NotificationResponseDTO;
import com.ailogis.api.entity.Notification;
import com.ailogis.api.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public void saveAndNotify(Long userId, String message) {
        Notification notification = Notification.builder()
                .userId(userId)
                .message(message)
                .createdAt(LocalDateTime.now())
                .read(false)
                .build();
        notificationRepository.save(notification);
    }

    public List<NotificationResponseDTO> getNotifications(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(n -> new NotificationResponseDTO(n.getId(), n.getMessage(), n.getCreatedAt(), n.getRead()))
                .toList();
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
