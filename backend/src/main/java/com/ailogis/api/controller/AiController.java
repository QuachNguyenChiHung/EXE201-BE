package com.ailogis.api.controller;

import com.ailogis.api.dto.AiChatRequestDTO;
import com.ailogis.api.dto.AiSearchRequestDTO;
import com.ailogis.api.dto.AiSearchResponseDTO;
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

    /**
     * Two-step AI chat: extracts search criteria → searches DB → summarizes results.
     * Accepts the full payload from the frontend (criteria, candidate warehouses, history).
     * Returns summary text + warehouse page + refined warehouse IDs.
     */
    @PostMapping("/chat")
    public ResponseEntity<AiSearchResponseDTO> chatWithAgent(
            @RequestBody AiSearchRequestDTO dto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(aiService.processChat(
                userDetails.getUser().getId(),
                dto.query(),
                dto.conversationHistory(),
                dto.criteria(),
                dto.matchingWarehouses(),
                dto.isInitialHandshake() != null && dto.isInitialHandshake()));
    }

    /**
     * Legacy endpoint — kept for backward compatibility with existing clients.
     * Delegates to the new two-step chat (returns summary + warehouse list).
     */
    @PostMapping("/chat-legacy")
    public ResponseEntity<AiSearchResponseDTO> chatLegacy(
            @RequestBody AiChatRequestDTO dto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(aiService.processChat(
                userDetails.getUser().getId(),
                dto.query(),
                dto.conversationHistory(),
                null, null, false));
    }
}