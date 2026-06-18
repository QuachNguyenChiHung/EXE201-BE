package com.ailogis.api.service;

import com.ailogis.api.dto.AiChatRequestDTO;
import com.ailogis.api.dto.AiChatResponseDTO;
import com.ailogis.api.entity.AiConversation;
import com.ailogis.api.entity.User;
import com.ailogis.api.repository.AiConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiService {

    private final AiConversationRepository aiConversationRepository;
    private final RestTemplate restTemplate;

    @Transactional
    public AiChatResponseDTO processChat(User user, AiChatRequestDTO dto) {
        // 1. Kiểm tra phân quyền: Khách phải có gói AI mới được dùng
        if (user.getAiTier() == null) {
            throw new RuntimeException("Truy cập bị từ chối: Bạn cần đăng ký Gói AI để sử dụng Trợ lý ảo!");
        }

        // 2. Chuẩn bị gọi sang con Agent Render
        String renderApiUrl = "https://ai-agent-exe.onrender.com/api/chat";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = new HashMap<>();
        body.put("query", dto.query());

        HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(body, headers);
        String botResponseText = "";

        // 3. Bắn Request và hứng dữ liệu
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(renderApiUrl, requestEntity, String.class);
            botResponseText = response.getBody();

            // Nếu Render trả về chuỗi rỗng
            if (botResponseText == null || botResponseText.isBlank()) {
                botResponseText = "Xin lỗi, AI Agent hiện tại không có phản hồi.";
            }
        } catch (Exception e) {
            throw new RuntimeException("Lỗi kết nối đến máy chủ AI Backend: " + e.getMessage());
        }

        // 4. Lưu lịch sử hội thoại vào Database
        // Giả lập tính token đơn giản: 1 token ~ 4 ký tự
        int inputTokens = dto.query().length() / 4;
        int outputTokens = botResponseText.length() / 4;

        AiConversation conversation = AiConversation.builder()
                .user(user)
                .criteria(dto.query())     // User hỏi gì
                .message(botResponseText)  // Bot trả lời gì
                .totalInputTokens(inputTokens)
                .totalOutputTokens(outputTokens)
                .build();

        aiConversationRepository.save(conversation);

        // 5. Trả về cho UI
        return new AiChatResponseDTO(botResponseText);
    }
}