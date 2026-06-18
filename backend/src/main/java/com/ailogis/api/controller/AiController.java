package com.ailogis.api.controller;

import com.ailogis.api.dto.AiChatRequestDTO;
import com.ailogis.api.dto.AiChatResponseDTO;
import com.ailogis.api.security.CustomUserDetails;
import com.ailogis.api.service.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @PostMapping("/chat")
    public ResponseEntity<AiChatResponseDTO> chatWithAgent(
            @RequestBody AiChatRequestDTO dto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ResponseEntity.ok(aiService.processChat(userDetails.getUser(), dto));
    }
}