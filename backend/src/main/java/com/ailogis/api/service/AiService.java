package com.ailogis.api.service;

import com.ailogis.api.dto.*;
import com.ailogis.api.entity.AiConversation;
import com.ailogis.api.entity.User;
import com.ailogis.api.repository.AiConversationRepository;
import com.ailogis.api.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiService {

    private final AiConversationRepository aiConversationRepository;
    private final UserRepository userRepository;
    private final WarehouseService warehouseService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String AI_AGENT_URL = "https://ai-agent-exe.onrender.com/api/chat";

    // Price multipliers to convert to monthly equivalent
    private static final Map<String, Double> PRICE_MULTIPLIER = Map.of(
            "month", 1.0,
            "day", 30.0,
            "week", 4.0,
            "year", 1.0 / 12.0);

    /**
     * Two-step AI chat (hybrid search):
     * <ul>
     * <li>Initial handshake (empty query, FE has already filtered): summarize FE's
     * candidate list, return all IDs.</li>
     * <li>Follow-up query: extract criteria → search DB → summarize → return IDs of
     * top matches.</li>
     * </ul>
     *
     * @param userId              the authenticated user
     * @param query               natural-language follow-up question (blank for
     *                            initial handshake)
     * @param conversationHistory serialized previous conversation (nullable)
     * @param feCriteria          criteria from the FE wizard (optional hint)
     * @param matchingWarehouses  FE's already-filtered candidate list (used during
     *                            initial handshake)
     * @param isInitialHandshake  true on first call — skip criteria extraction,
     *                            just greet + summarize
     */
    @Transactional
    public AiSearchResponseDTO processChat(
            Long userId,
            String query,
            String conversationHistory,
            Object feCriteria,
            Object matchingWarehouses,
            boolean isInitialHandshake) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại!"));

        if (user.getAiTier() == null) {
            throw new RuntimeException("Truy cập bị từ chức: Bạn cần đăng ký Gói AI để sử dụng Trợ lý ảo!");
        }

        int balance = user.getAiTier().getTokenOutput() != null ? user.getAiTier().getTokenOutput() : 0;
        if (balance <= 0) {
            throw new RuntimeException("Không đủ token. Vui lòng nạp thêm gói AI!");
        }

        Page<WarehouseResponseDTO> warehousePage;
        String criteriaJson;
        int run1OutputTokens;

        if (isInitialHandshake || query == null || query.isBlank()) {
            // Initial handshake: FE already searched — just summarize and return its
            // candidates (capped at 12)
            warehousePage = toPageFromFeCandidates(matchingWarehouses, 12);
            criteriaJson = feCriteria != null ? safeToJson(feCriteria) : "{\"initial_handshake\": true}";
            run1OutputTokens = 0;
        } else {
            // Follow-up: extract criteria via AI and search DB
            String run1Prompt = buildCriteriaExtractionPrompt(query, conversationHistory);
            String rawJson = callAgent(run1Prompt);
            String cleanedJson = extractJsonFromMarkdown(rawJson);
            criteriaJson = cleanedJson;
            run1OutputTokens = rawJson.length() / 4;

            SearchCriteriaDTO criteria;
            try {
                criteria = objectMapper.readValue(cleanedJson, SearchCriteriaDTO.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("AI không trả về JSON hợp lệ: " + cleanedJson);
            }

            SearchCriteriaDTO normalized = normalizeCriteriaForSearch(criteria);
            warehousePage = warehouseService.searchWarehousesByCriteria(
                    normalized, PageRequest.of(0, 12));
        }

        String run2Prompt = buildSummaryPrompt(query, warehousePage);
        String summary = callAgent(run2Prompt);
        int run2OutputTokens = summary.length() / 4;
        int totalOutputTokens = run1OutputTokens + run2OutputTokens;

        int remaining = balance - totalOutputTokens;
        if (remaining < 0) {
            throw new RuntimeException("Không đủ token cho phản hồi này. Vui lòng nạp thêm gói AI!");
        }

        user.getAiTier().setTokenOutput(remaining);
        userRepository.save(user);

        int inputTokens = (query == null ? 0 : query.length()) / 4;
        AiConversation conversation = AiConversation.builder()
                .user(user)
                .criteria(query == null ? "" : query)
                .message(summary)
                .totalInputTokens(inputTokens)
                .totalOutputTokens(totalOutputTokens)
                .build();
        aiConversationRepository.save(conversation);

        List<Long> refinedIds = warehousePage.getContent().stream()
                .map(WarehouseResponseDTO::id)
                .collect(Collectors.toList());

        return new AiSearchResponseDTO(summary, warehousePage, refinedIds, criteriaJson, inputTokens,
                totalOutputTokens);
    }

    /**
     * Convert the FE's matchingWarehouse objects to a Page<WarehouseResponseDTO>.
     * Best-effort: maps common fields if present; falls back to empty page.
     */
    private Page<WarehouseResponseDTO> toPageFromFeCandidates(Object matchingWarehouses, int maxSize) {
        if (!(matchingWarehouses instanceof List<?> list) || list.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), Pageable.unpaged(), 0);
        }
        List<WarehouseResponseDTO> mapped = new ArrayList<>();
        int limit = Math.min(list.size(), maxSize);
        for (int i = 0; i < limit; i++) {
            Object o = list.get(i);
            if (!(o instanceof Map<?, ?> m))
                continue;
            try {
                WarehouseResponseDTO dto = objectMapper.convertValue(m, WarehouseResponseDTO.class);
                mapped.add(dto);
            } catch (Exception ignored) {
                // Skip malformed entries — best-effort conversion
            }
        }
        return new PageImpl<>(mapped, Pageable.unpaged(), mapped.size());
    }

    private String safeToJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String buildCriteriaExtractionPrompt(String userQuery, String history) {
        String historyBlock = (history != null && !history.isBlank())
                ? "\nLịch sử hội thoại trước đó:\n" + history + "\n"
                : "";

        return "You are a data extraction engine. Convert user search requests into a valid JSON object.\n\n"
                + "Output requirements:\n"
                + "- Return ONLY valid JSON.\n"
                + "- No markdown, no explanations, no extra text.\n"
                + "- All fields must always exist.\n\n"
                + "Schema:\n"
                + "{\n"
                + "  \"location\": [{\"province\": \"string | null\"}],\n"
                + "  \"minPrice\": \"number | null\",\n"
                + "  \"maxPrice\": \"number | null\",\n"
                + "  \"priceType\": [\"month\" | \"week\" | \"year\" | \"day\"] | null,\n"
                + "  \"areaUnit\": \"m3\",\n"
                + "  \"name\": \"string | null\",\n"
                + "  \"tempMin\": \"number | null\",\n"
                + "  \"tempMax\": \"number | null\",\n"
                + "  \"availableCapacity\": {\"min_range\": \"number | null\", \"max_range\": \"number | null\"},\n"
                + "  \"totalCapacity\": {\"min_range\": \"number | null\", \"max_range\": \"number | null\"},\n"
                + "  \"rating\": {\"min_range\": \"number | null\", \"max_range\": \"number | null\"},\n"
                + "  \"sort\": {\"type\": \"price | rating | null\"}\n"
                + "}\n\n"
                + "Rules:\n\n"
                + "1. location — extract province/city names. Return as array.\n"
                + "   If no province mentioned: [{\"province\": null}]\n"
                + "   Always use the canonical Vietnamese province name as stored in the database. Common aliases → canonical:\n"
                + "     - \"TP.HCM\", \"TPHCM\", \"HCMC\", \"Sài Gòn\" → \"Hồ Chí Minh\"\n"
                + "     - \"HN\", \"TP.Hà Nội\" → \"Hà Nội\"\n"
                + "     - \"ĐN\", \"Da Nang\" → \"Đà Nẵng\"\n\n"
                + "2. minPrice & maxPrice — price in VND (numbers). null if not mentioned.\n\n"
                + "3. priceType — array: [\"month\"] or [\"week\"] etc. null if not mentioned.\n\n"
                + "4. areaUnit — always \"m3\".\n\n"
                + "5. name — warehouse name keyword. null if not mentioned.\n\n"
                + "6. tempMin & tempMax — temperature in °C. null if not mentioned.\n\n"
                + "7. availableCapacity — available capacity range in m3.\n\n"
                + "8. totalCapacity — total capacity range in m3.\n\n"
                + "9. rating — min/max rating. Default to null if not mentioned.\n\n"
                + "10. sort — \"price\" for price sort, \"rating\" for rating sort, null for default.\n\n"
                + "Now parse this user request and return JSON only:\n"
                + historyBlock
                + "\nUser: \"" + (userQuery == null ? "" : userQuery) + "\"";
    }

    private String buildSummaryPrompt(String userQuery, Page<WarehouseResponseDTO> warehouses) {
        StringBuilder sb = new StringBuilder();
        sb.append(
                "Bạn là trợ lý tư vấn kho lạnh. Dựa trên kết quả tìm kiếm dưới đây, hãy viết một đoạn tóm tắt ngắn gọn (2-4 câu) bằng tiếng Việt giới thiệu các kho phù hợp nhất với yêu cầu của người dùng.\n\n");
        sb.append("Yêu cầu người dùng: \"").append(userQuery == null ? "" : userQuery).append("\"\n\n");
        sb.append("Kết quả tìm được (").append(warehouses.getTotalElements()).append(" kho, hiển thị ")
                .append(warehouses.getNumberOfElements()).append("):\n\n");

        for (int i = 0; i < warehouses.getNumberOfElements(); i++) {
            WarehouseResponseDTO w = warehouses.getContent().get(i);
            sb.append(i + 1).append(". ").append(w.name()).append(" — ")
                    .append(w.locationProvince()).append(", ")
                    .append(w.locationCommune()).append("\n");
            if (w.sections() != null && !w.sections().isEmpty()) {
                double minTemp = w.sections().stream().mapToDouble(s -> s.tempMin() != null ? s.tempMin() : 0).min()
                        .orElse(0);
                double maxTemp = w.sections().stream().mapToDouble(s -> s.tempMax() != null ? s.tempMax() : 0).max()
                        .orElse(0);
                double totalAvail = w.sections().stream()
                        .mapToDouble(s -> s.availableCapacity() != null ? s.availableCapacity() : 0).sum();
                sb.append("   Nhiệt độ: ").append(String.format("%.1f", minTemp))
                        .append("°~").append(String.format("%.1f", maxTemp)).append("°C | ");
                sb.append("Còn trống: ").append(String.format("%.0f", totalAvail))
                        .append("m³\n");
            }
            if (w.averageRating() != null && w.averageRating() > 0) {
                sb.append("   Rating: ").append(String.format("%.1f", w.averageRating()))
                        .append("/5 (").append(w.totalReviews()).append(" đánh giá)\n");
            }
            sb.append("   ").append(w.description()).append("\n\n");
        }

        sb.append(
                "\nViết phản hồi bằng tiếng Việt, tự nhiên, thân thiện. Nếu không có kho nào phù hợp, hãy thông báo lịch sự.");
        return sb.toString();
    }

    private SearchCriteriaDTO normalizeCriteriaForSearch(SearchCriteriaDTO criteria) {
        Double minPrice = criteria.minPrice();
        Double maxPrice = criteria.maxPrice();

        if (criteria.priceType() != null && !criteria.priceType().isEmpty() && (minPrice != null || maxPrice != null)) {
            String unit = criteria.priceType().get(0);
            Double multiplier = PRICE_MULTIPLIER.getOrDefault(unit.toLowerCase(), 1.0);
            if (!"month".equalsIgnoreCase(unit)) {
                minPrice = minPrice != null ? minPrice * multiplier : null;
                maxPrice = maxPrice != null ? maxPrice * multiplier : null;
            }
        }

        // Rebuild with normalized prices and null priceType (backend always works in
        // monthly)
        return new SearchCriteriaDTO(
                criteria.location(),
                minPrice, maxPrice,
                null, // priceType normalized — backend uses monthly
                criteria.areaUnit(),
                criteria.name(),
                criteria.tempMin(), criteria.tempMax(),
                criteria.availableCapacity(),
                criteria.totalCapacity(),
                criteria.rating(),
                criteria.sort());
    }

    private String callAgent(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = new HashMap<>();
        body.put("query", prompt);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(AI_AGENT_URL, request, String.class);
            String result = response.getBody();
            if (result == null || result.isBlank()) {
                return "{\"location\":[{\"province\":null}],\"minPrice\":null,\"maxPrice\":null,\"priceType\":null,\"areaUnit\":\"m3\",\"name\":null,\"tempMin\":null,\"tempMax\":null,\"availableCapacity\":{\"min_range\":null,\"max_range\":null},\"totalCapacity\":{\"min_range\":null,\"max_range\":null},\"rating\":{\"min_range\":null,\"max_range\":null},\"sort\":{\"type\":null}}";
            }
            return result.trim();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi kết nối đến máy chủ AI: " + e.getMessage());
        }
    }

    /**
     * Strip markdown code fences that the AI sometimes wraps around JSON.
     * Handles: ```json ... ``` and plain JSON.
     */
    private String extractJsonFromMarkdown(String raw) {
        String trimmed = raw.trim();
        // Remove triple-backtick wrapper (with optional "json" tag)
        Pattern p = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(trimmed);
        if (m.find()) {
            return m.group(1).trim();
        }
        return trimmed;
    }
}