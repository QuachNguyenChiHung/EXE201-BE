package com.ailogis.api.controller;

import com.ailogis.api.dto.*;
import com.ailogis.api.security.CustomUserDetails;
import com.ailogis.api.service.AiService;
import com.ailogis.api.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;
    private final WarehouseService warehouseService;

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
     * Context search: FE sends full warehouse detail for every candidate.
     * AI reasons over all data and returns a ranked list of IDs + summary text.
     * More token-costly than standard search — caller should pre-filter candidates.
     */
    @PostMapping("/context-chat")
    public ResponseEntity<AiSearchResponseDTO> contextChat(
            @RequestBody AiContextSearchRequestDTO dto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(aiService.processContextChat(
                userDetails.getUser().getId(),
                dto.query(),
                dto.conversationHistory(),
                dto.warehouses()));
    }

    @GetMapping("/conversations/my")
    public ResponseEntity<List<AiConversationResponseDTO>> getMyConversations(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(aiService.getMyConversations(userDetails.getUser().getId()));
    }

    @GetMapping("/filter-meta")
    public ResponseEntity<AiFilterMetaResponseDTO> getAiFilterMeta() {
        return ResponseEntity.ok(warehouseService.getAiFilterMeta());
    }

    /**
     * Persist a full conversation (all messages as JSON) from the frontend.
     * Called at end-of-conversation or when tokens are exhausted.
     */
    @PostMapping("/conversations")
    public ResponseEntity<Void> saveConversation(
            @RequestBody SaveConversationRequestDTO dto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        aiService.saveConversation(userDetails.getUser().getId(), dto);
        return ResponseEntity.ok().build();
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