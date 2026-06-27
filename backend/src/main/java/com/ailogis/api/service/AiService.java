package com.ailogis.api.service;

import com.ailogis.api.dto.*;
import com.ailogis.api.entity.AiConversation;
import com.ailogis.api.entity.User;
import com.ailogis.api.repository.AiConversationRepository;
import com.ailogis.api.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
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

    @Transactional
    public void saveConversation(Long userId, SaveConversationRequestDTO dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại!"));
        AiConversation conversation = AiConversation.builder()
                .user(user)
                .criteria(dto.criteria() != null ? dto.criteria() : "{}")
                .message(dto.messages() != null ? dto.messages() : "[]")
                .totalInputTokens(dto.totalInputTokens() != null ? dto.totalInputTokens() : 0)
                .totalOutputTokens(dto.totalOutputTokens() != null ? dto.totalOutputTokens() : 0)
                .build();
        aiConversationRepository.save(conversation);
    }

    public List<AiConversationResponseDTO> getMyConversations(Long userId) {
        return aiConversationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(c -> new AiConversationResponseDTO(
                        c.getId(),
                        c.getMessage(),
                        c.getCriteria(),
                        c.getTotalInputTokens(),
                        c.getTotalOutputTokens(),
                        c.getCreatedAt(),
                        c.getUpdatedAt()))
                .toList();
    }

    /**
     * Search warehouses using the AI criteria format:
     * <pre>
     * {
     *   "location": [{"province": "Hồ Chí Minh"}],
     *   "minPrice": 4050000,
     *   "maxPrice": 4950000,
     *   "priceType": ["month"],
     *   "areaUnit": "m3",
     *   "name": null,
     *   "tempMin": null,
     *   "tempMax": null,
     *   "availableCapacity": null,
     *   "totalCapacity": null,
     *   "rating": null,
     *   "sort": null
     * }
     * </pre>
     * Prices are normalized to monthly equivalents before querying the DB.
     *
     * @param criteria search criteria in AI JSON format
     * @return list of matching warehouses (no pagination)
     */
    public List<WarehouseResponseDTO> searchWarehouse(SearchCriteriaDTO criteria) {
        log.info("[AiService/searchWarehouse] raw criteria — province='{}' priceType={} minPrice={} maxPrice={}",
                criteria.location() != null && !criteria.location().isEmpty()
                        ? criteria.location().get(0).province() : "null",
                criteria.priceType(), criteria.minPrice(), criteria.maxPrice());

        SearchCriteriaDTO normalized = normalizeCriteriaForSearch(criteria);

        log.info("[AiService/searchWarehouse] normalized — minPrice={} maxPrice={}",
                normalized.minPrice(), normalized.maxPrice());

        return warehouseService.searchWarehousesByCriteria(normalized, Pageable.unpaged()).getContent();
    }

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

        log.info("[AI/chat] userId={} query='{}' isHandshake={}", userId, query, isInitialHandshake);

        if (isInitialHandshake || query == null || query.isBlank()) {
            // Initial handshake: FE already searched — just summarize and return its
            // candidates (capped at 12)
            warehousePage = toPageFromFeCandidates(matchingWarehouses, 12);
            criteriaJson = feCriteria != null ? safeToJson(feCriteria) : "{\"initial_handshake\": true}";
            run1OutputTokens = 0;
            log.info("[AI/chat] handshake — FE candidates: {}", warehousePage.getTotalElements());

            // ── Fallback: FE race-condition (mount fetch not yet complete) ──────────
            // If this is a handshake BUT the FE sent 0 candidates AND the user actually
            // typed a query, do a full criteria-extraction + DB search so we never
            // return "no warehouses" on the very first message.
            if (warehousePage.getTotalElements() == 0 && query != null && !query.isBlank()) {
                log.info("[AI/chat] handshake with 0 FE candidates and non-blank query — falling back to DB search");
                AiFilterMetaResponseDTO meta = warehouseService.getAiFilterMeta();
                String run1Prompt = buildCriteriaExtractionPrompt(query, conversationHistory, meta);
                log.info("[AI/chat] fallback — calling agent for criteria extraction...");
                String rawJson = callAgent(run1Prompt, conversationHistory);
                log.info("[AI/chat] fallback — raw criteria JSON: {}", rawJson);
                String cleanedJson = extractJsonFromMarkdown(rawJson);
                criteriaJson = cleanedJson;
                run1OutputTokens = rawJson.length() / 4;

                SearchCriteriaDTO criteria;
                try {
                    String normalizedJson = normalizeAiJson(cleanedJson);
                    log.info("[AI/chat] fallback — normalized JSON: {}", normalizedJson);
                    criteria = objectMapper.readValue(normalizedJson, SearchCriteriaDTO.class);
                } catch (JsonProcessingException e) {
                    log.warn("[AI/chat] fallback — failed to parse criteria JSON, using empty criteria: {}", e.getMessage());
                    criteria = new SearchCriteriaDTO(null, null, null, null, "m3", null, null, null, null, null, null, null);
                }
                criteria = sanitizeCriteria(query, criteria);
                log.info("[AI/chat] fallback — sanitized criteria: province={} minPrice={} maxPrice={}",
                        criteria.location() != null && !criteria.location().isEmpty() ? criteria.location().get(0).province() : "null",
                        criteria.minPrice(), criteria.maxPrice());

                List<WarehouseResponseDTO> results = searchWarehouse(criteria);
                warehousePage = new PageImpl<>(results, Pageable.unpaged(), results.size());
                log.info("[AI/chat] fallback — DB returned {} warehouses", warehousePage.getTotalElements());
            }
        } else if (isConversationalQuery(query)) {
            // ── Conversational / informational turn — no warehouse search needed ──
            // The user is asking a follow-up question about details, certifications, etc.
            // Skip criteria extraction and return the AI's plain-text answer directly.
            log.info("[AI/chat] conversational query detected — skipping DB search");
            String convPrompt = buildConversationalPrompt(query, conversationHistory);
            criteriaJson = "{\"conversational\": true}";
            run1OutputTokens = 0;
            // Return empty warehouse page so the FE keeps whatever list it already shows.
            warehousePage = new PageImpl<>(Collections.emptyList(), Pageable.unpaged(), 0);
            String convAnswer = callAgent(convPrompt, conversationHistory);
            log.info("[AI/chat] conversational answer length={}", convAnswer.length());
            int convOutputTokens = convAnswer.length() / 4;
            int remaining = balance - convOutputTokens;
            boolean tokenExhausted = remaining < 0;
            if (tokenExhausted) remaining = 0;
            user.getAiTier().setTokenOutput(remaining);
            userRepository.save(user);
            int inputTokens = query.length() / 4;
            AiConversation convo = AiConversation.builder()
                    .user(user).criteria(query).message(convAnswer)
                    .totalInputTokens(inputTokens).totalOutputTokens(convOutputTokens)
                    .build();
            aiConversationRepository.save(convo);
            return new AiSearchResponseDTO(convAnswer, warehousePage, List.of(), criteriaJson,
                    inputTokens, convOutputTokens, tokenExhausted);
        } else {
            // Follow-up: extract criteria via AI and search DB
            AiFilterMetaResponseDTO meta = warehouseService.getAiFilterMeta();
            String run1Prompt = buildCriteriaExtractionPrompt(query, conversationHistory, meta);
            log.info("[AI/chat] calling agent for criteria extraction...");
            String rawJson = callAgent(run1Prompt, conversationHistory);
            log.info("[AI/chat] raw criteria JSON: {}", rawJson);
            String cleanedJson = extractJsonFromMarkdown(rawJson);
            criteriaJson = cleanedJson;
            run1OutputTokens = rawJson.length() / 4;

            SearchCriteriaDTO criteria;
            try {
                String normalizedJson = normalizeAiJson(cleanedJson);
                log.info("[AI/chat] normalized JSON: {}", normalizedJson);
                criteria = objectMapper.readValue(normalizedJson, SearchCriteriaDTO.class);
            } catch (JsonProcessingException e) {
                // ── Safety net: AI returned plain text instead of JSON ────────────
                // Treat the raw response as a conversational answer and return it
                // directly (keep the current FE warehouse list unchanged).
                log.warn("[AI/chat] JSON parse failed — returning raw AI text as conversational answer");
                String fallbackText = cleanedJson.startsWith("{") ? cleanedJson : rawJson.trim();
                warehousePage = new PageImpl<>(Collections.emptyList(), Pageable.unpaged(), 0);
                int fbOutputTokens = rawJson.length() / 4;
                int remaining = balance - fbOutputTokens;
                boolean tokenExhausted = remaining < 0;
                if (tokenExhausted) remaining = 0;
                user.getAiTier().setTokenOutput(remaining);
                userRepository.save(user);
                int inputTokens = query.length() / 4;
                AiConversation convo = AiConversation.builder()
                        .user(user).criteria(query).message(fallbackText)
                        .totalInputTokens(inputTokens).totalOutputTokens(fbOutputTokens)
                        .build();
                aiConversationRepository.save(convo);
                return new AiSearchResponseDTO(fallbackText, warehousePage, List.of(),
                        "{\"conversational\": true}", inputTokens, fbOutputTokens, tokenExhausted);
            }
            criteria = sanitizeCriteria(query, criteria);
            log.info("[AI/chat] sanitized criteria: province={} minPrice={} maxPrice={}",
                    criteria.location() != null && !criteria.location().isEmpty() ? criteria.location().get(0).province() : "null",
                    criteria.minPrice(), criteria.maxPrice());

            List<WarehouseResponseDTO> results = searchWarehouse(criteria);
            warehousePage = new PageImpl<>(results, Pageable.unpaged(), results.size());
            log.info("[AI/chat] DB returned {} warehouses", warehousePage.getTotalElements());
        }

        String run2Prompt = buildSummaryPrompt(query, warehousePage);
        log.info("[AI/chat] calling agent for summary ({} warehouses in prompt)...", warehousePage.getNumberOfElements());
        String summary = callAgent(run2Prompt, null);
        log.info("[AI/chat] summary: {}", summary);
        int run2OutputTokens = summary.length() / 4;
        int totalOutputTokens = run1OutputTokens + run2OutputTokens;

        int remaining = balance - totalOutputTokens;
        boolean tokenExhausted = remaining < 0;
        if (tokenExhausted) {
            remaining = 0;
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
                totalOutputTokens, tokenExhausted);
    }

    /**
     * Context search: the FE sends full warehouse detail for every candidate.
     * One AI call reasons over ALL the data and returns a ranked list of IDs.
     * Much more token-costly than standard search — caller should pre-filter.
     */
    @Transactional
    public AiSearchResponseDTO processContextChat(
            Long userId,
            String query,
            String conversationHistory,
            Object warehouses) {

        log.info("[AI/contextChat] userId={} query='{}'", userId, query);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại!"));

        if (user.getAiTier() == null) {
            throw new RuntimeException("Truy cập bị từ chối: Bạn cần đăng ký Gói AI để sử dụng Trợ lý ảo!");
        }
        int balance = user.getAiTier().getTokenOutput() != null ? user.getAiTier().getTokenOutput() : 0;
        if (balance <= 0) {
            throw new RuntimeException("Không đủ token. Vui lòng nạp thêm gói AI!");
        }

        // Convert FE warehouse objects → typed DTOs so we can build the prompt
        List<WarehouseResponseDTO> warehouseList = toListFromFeCandidates(warehouses);
        log.info("[AI/contextChat] received {} warehouse candidates from FE", warehouseList.size());

        String prompt = buildContextSearchPrompt(query, conversationHistory, warehouseList);
        log.info("[AI/contextChat] calling agent for context processing...");
        String rawResponse = callAgent(prompt, conversationHistory);
        log.info("[AI/contextChat] raw agent response: {}", rawResponse);
        String cleaned = extractJsonFromMarkdown(rawResponse);

        String responseText;
        List<Long> refinedIds;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(cleaned, Map.class);
            responseText = parsed.getOrDefault("response", "").toString();
            @SuppressWarnings("unchecked")
            List<Object> idList = (List<Object>) parsed.getOrDefault("refinedWarehouseIds", List.of());
            refinedIds = idList.stream()
                    .map(v -> Long.parseLong(v.toString()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[AI/contextChat] JSON parse failed — returning raw AI text as conversational answer");
            // AI returned plain text — fall back to the original candidate order
            responseText = rawResponse.trim();
            refinedIds = warehouseList.stream().map(WarehouseResponseDTO::id).collect(Collectors.toList());
        }

        // Fetch authoritative warehouse data from DB in ranked order
        List<WarehouseResponseDTO> rankedWarehouses = warehouseService.getWarehousesByIds(refinedIds);
        log.info("[AI/context] AI returned {} ids, DB resolved {} warehouses", refinedIds.size(), rankedWarehouses.size());

        int outputTokens = rawResponse.length() / 4;
        int inputTokens = (query == null ? 0 : query.length()) / 4;
        int remaining = balance - outputTokens;
        boolean tokenExhausted = remaining < 0;
        if (tokenExhausted) remaining = 0;

        user.getAiTier().setTokenOutput(remaining);
        userRepository.save(user);

        AiConversation conversation = AiConversation.builder()
                .user(user)
                .criteria(query == null ? "" : query)
                .message(responseText)
                .totalInputTokens(inputTokens)
                .totalOutputTokens(outputTokens)
                .build();
        aiConversationRepository.save(conversation);

        Page<WarehouseResponseDTO> warehousePage = new PageImpl<>(rankedWarehouses, Pageable.unpaged(), rankedWarehouses.size());
        return new AiSearchResponseDTO(responseText, warehousePage, refinedIds, null, inputTokens, outputTokens, tokenExhausted);
    }

    private String buildContextSearchPrompt(String query, String history, List<WarehouseResponseDTO> warehouses) {
        String historyBlock = (history != null && !history.isBlank())
                ? "\n## Lịch sử hội thoại\n" + history + "\n"
                : "";

        StringBuilder sb = new StringBuilder();
        sb.append("Bạn là trợ lý tư vấn kho lạnh cho người dùng Việt Nam.\n")
          .append("QUAN TRỌNG: Không bắt đầu phản hồi bằng lời chào (\'Chào bạn!\', \'Xin chào!\', ...). Hãy đi thẳng vào nội dung.\n\n");
        sb.append("Yêu cầu người dùng: \"").append(query == null ? "" : query).append("\"\n\n");
        sb.append("Phân tích danh sách ").append(warehouses.size())
          .append(" kho lạnh dưới đây và trả lời theo định dạng JSON sau:\n");
        sb.append("{\n");
        sb.append("  \"response\": \"<đoạn tóm tắt tiếng Việt 2-4 câu giới thiệu kho phù hợp nhất, KHÔNG chào hỏi>\",\n");
        sb.append("  \"refinedWarehouseIds\": [<danh sách ID kho, sắp xếp từ phù hợp nhất đến ít phù hợp nhất>]\n");
        sb.append("}\n\n");
        sb.append("Chỉ trả về JSON hợp lệ — không markdown, không giải thích thêm.\n\n");
        sb.append("## Danh sách kho\n\n");

        for (int i = 0; i < warehouses.size(); i++) {
            WarehouseResponseDTO w = warehouses.get(i);
            sb.append(i + 1).append(". ID: ").append(w.id())
              .append(" | Tên: ").append(w.name())
              .append(" | Tỉnh/TP: ").append(w.locationProvince())
              .append(", ").append(w.locationCommune())
              .append(" | Trạng thái: ").append(w.status());
            if (w.averageRating() != null && w.averageRating() > 0) {
                sb.append(" | Đánh giá: ").append(String.format("%.1f", w.averageRating()))
                  .append("/5 (").append(w.totalReviews()).append(" lượt)");
            }
            sb.append("\n");

            if (w.sections() != null && !w.sections().isEmpty()) {
                sb.append("   Phòng kho:\n");
                for (WarehouseSectionDTO s : w.sections()) {
                    sb.append("   - Nhiệt độ: ").append(s.tempMin()).append("~").append(s.tempMax()).append("°C");
                    sb.append(", Còn trống: ").append(s.availableCapacity()).append("m³");
                    sb.append(", Tổng: ").append(s.totalCapacity()).append("m³");
                    if (s.priceTiers() != null && !s.priceTiers().isEmpty()) {
                        sb.append(", Giá: ");
                        s.priceTiers().forEach(pt ->
                            sb.append(pt.label()).append(": ").append(pt.value())
                              .append(" ").append(pt.unit()).append("/").append(pt.areaUnit()).append(" "));
                    }
                    sb.append("\n");
                }
            }

            if (w.certificates() != null && !w.certificates().isEmpty()) {
                sb.append("   Chứng nhận: ");
                w.certificates().forEach(c -> sb.append(c.label()).append(" "));
                sb.append("\n");
            }

            if (w.description() != null && !w.description().isBlank()) {
                String desc = w.description().length() > 200 ? w.description().substring(0, 200) + "..." : w.description();
                sb.append("   Mô tả: ").append(desc).append("\n");
            }
            sb.append("\n");
        }

        sb.append(historyBlock);
        return sb.toString();
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

    private List<WarehouseResponseDTO> toListFromFeCandidates(Object obj) {
        if (!(obj instanceof List<?> list)) return Collections.emptyList();
        
        // Use a lenient mapper so FE-specific fields don't break the mapping
        ObjectMapper lenientMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                
        List<WarehouseResponseDTO> result = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> raw)) continue;
            try {
                // FE uses different field names than the backend DTO — normalise before
                // converting so rating, id, and certifications map correctly.
                Map<String, Object> m = new HashMap<>();
                raw.forEach((k, v) -> m.put(String.valueOf(k), v));
                m.putIfAbsent("id",             m.remove("id_warehouse"));
                m.putIfAbsent("averageRating",  m.remove("ratingScore"));
                m.putIfAbsent("totalReviews",   m.remove("ratingCount"));
                m.putIfAbsent("certificates",   m.remove("certifications"));
                m.putIfAbsent("locationProvince", m.remove("location_province"));
                m.putIfAbsent("locationCommune",  m.remove("location_commune"));
                m.putIfAbsent("locationAddressText", m.remove("location_address_text"));
                // stats sub-object — pull aggregated rating if top-level is still missing
                if (m.get("averageRating") == null && m.get("stats") instanceof Map<?,?> stats) {
                    m.put("averageRating", stats.get("rating"));
                    m.put("totalReviews",  stats.get("reviews"));
                }
                result.add(lenientMapper.convertValue(m, WarehouseResponseDTO.class));
            } catch (Exception e) {
                log.warn("[AI/normalize] Failed to convert warehouse candidate: {}", e.getMessage());
            }
        }
        return result;
    }

    private String safeToJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String buildCriteriaExtractionPrompt(String userQuery, String history, AiFilterMetaResponseDTO meta) {
        String provinceList = meta.provinces().stream()
                .map(p -> "\"" + p + "\"")
                .collect(Collectors.joining(", "));

        String certList = meta.certifications().stream()
                .map(c -> "label=\"" + c.label() + "\"" + (c.description() != null ? " description=\"" + c.description() + "\"" : ""))
                .collect(Collectors.joining(", "));

        String historyBlock = (history != null && !history.isBlank())
                ? "\n## Conversation History\n" + history + "\n"
                : "";

        return "You are a data extraction engine for a Vietnamese cold warehouse rental platform.\n\n"

                + "## Available Database Values\n"
                + "Provinces (use EXACT string): [" + provinceList + "]\n"
                + "Certifications: [" + certList + "]\n"
                + "Temperature range in DB: " + meta.tempMin() + "°C to " + meta.tempMax() + "°C\n"
                + "Available capacity range in DB: " + meta.capacityMin() + " to " + meta.capacityMax() + " m³\n"
                + "Price range in DB: " + meta.priceMin() + " to " + meta.priceMax() + " VND/month\n\n"

                + "## Region → Province Mapping (apply when user mentions a region, not a specific city)\n"
                + "When the user says a REGION word, return ALL MATCHING provinces from the province list above.\n"
                + "Use these mappings as guidance (only use provinces that appear in the list above):\n"
                + "- North / miền Bắc / phía Bắc / northern → include: Hà Nội, Hải Phòng, Quảng Ninh, Hải Dương, Bắc Ninh, Hưng Yên\n"
                + "- South / miền Nam / phía Nam / southern → include: Hồ Chí Minh, Bình Dương, Đồng Nai, Long An, Tiền Giang\n"
                + "- Central / miền Trung / phía Trung → include: Đà Nẵng, Thừa Thiên Huế, Quảng Nam, Bình Định, Khánh Hòa\n"
                + "- Example: If the user says 'north', return ALL northern provinces that exist in the DB list inside the \"location\" array of the JSON Schema.\n\n"

                + "## Warehouse Data Schema (WarehouseResponseDTO)\n"
                + "The warehouses being filtered have these fields:\n"
                + "- id: Long\n"
                + "- name: String (warehouse name)\n"
                + "- description: String\n"
                + "- locationProvince: String (must match province list exactly)\n"
                + "- locationCommune: String\n"
                + "- status: 'ACTIVE' | 'RENTED'\n"
                + "- averageRating: Double (0.0–5.0)\n"
                + "- totalReviews: Integer\n"
                + "- isSponsor: Boolean\n"
                + "- sections: List of WarehouseSection with:\n"
                + "    - tempMin, tempMax: Double (°C)\n"
                + "    - availableCapacity, totalCapacity: Double (m³)\n"
                + "    - humidity: Double (%)\n"
                + "    - priceTiers: List with:\n"
                + "        - value: Double (VND)\n"
                + "        - unit: 'month' | 'week' | 'day' | 'year'\n"
                + "        - areaUnit: 'm3' | 'pallet' | 'chuyến'\n"
                + "- certificates: List of verified certifications (matches cert list above)\n\n"

                + "## Output Requirements\n"
                + "- Return ONLY valid JSON — no markdown, no explanations.\n"
                + "- Every field must be present (use null if not applicable).\n\n"

                + "## JSON Schema\n"
                + "{\n"
                + "  \"location\": [{\"province\": \"<exact province string from list | null>\"}, {\"province\": \"<another province>\"}],\n"
                + "  \"minPrice\": <number in VND | null>,\n"
                + "  \"maxPrice\": <number in VND | null>,\n"
                + "  \"priceType\": [\"month\" | \"week\" | \"year\" | \"day\"] | null,\n"
                + "  \"areaUnit\": \"m3\",\n"
                + "  \"name\": \"<warehouse name keyword | null>\",\n"
                + "  \"tempMin\": <number °C | null>,\n"
                + "  \"tempMax\": <number °C | null>,\n"
                + "  \"availableCapacity\": {\"min_range\": <number | null>, \"max_range\": <number | null>},\n"
                + "  \"totalCapacity\": {\"min_range\": <number | null>, \"max_range\": <number | null>},\n"
                + "  \"rating\": {\"min_range\": <number 0-5 | null>, \"max_range\": <number 0-5 | null>},\n"
                + "  \"sort\": {\"type\": \"price\" | \"rating\" | null}\n"
                + "}\n\n"

                + "## Field Rules\n"
                + "1. location — pick the EXACT province string from the list above.\n"
                + "   - If user mentions a SPECIFIC city/province: use that exact string.\n"
                + "   - If user mentions a REGION (north/south/central/miền Bắc/miền Nam/miền Trung): use the Region→Province Mapping above to return MULTIPLE province objects for all matching provinces FROM THE LIST.\n"
                + "   - If no location mentioned at all: [{\"province\": null}].\n"
                + "2. minPrice/maxPrice — VND. null if not mentioned.\n"
                + "3. priceType — e.g. [\"month\"]. null if not mentioned.\n"
                + "4. areaUnit — always \"m3\".\n"
                + "5. name — only if user mentions a specific warehouse name. null otherwise.\n"
                + "6. tempMin/tempMax — °C. Use DB range as guide. null if not mentioned.\n"
                + "7. availableCapacity/totalCapacity — m³. null fields if not mentioned.\n"
                + "8. rating — 0–5 range. null if not mentioned.\n"
                + "9. sort — \"price\" when user wants cheapest, \"rating\" when user wants highest rated, null otherwise.\n\n"

                + historyBlock
                + "\nUser: \"" + (userQuery == null ? "" : userQuery) + "\"";
    }

    private String buildSummaryPrompt(String userQuery, Page<WarehouseResponseDTO> warehouses) {
        StringBuilder sb = new StringBuilder();
        sb.append(
                "Bạn là trợ lý tư vấn kho lạnh. Dựa trên kết quả tìm kiếm dưới đây, hãy viết một đoạn tóm tắt ngắn gọn (2-4 câu) bằng tiếng Việt giới thiệu các kho phù hợp nhất với yêu cầu của người dùng.\n"
                + "QUAN TRỌNG: Không bắt đầu bằng lời chào (\'Chào bạn!\', \'Xin chào!\', ...). Hãy đi thẳng vào nội dung.\n\n");
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
                "\nViết phản hồi bằng tiếng Việt, tự nhiên, thân thiện. Không bắt đầu bằng lời chào. Nếu không có kho nào phù hợp, hãy thông báo lịch sự.");
        return sb.toString();
    }

    // ─── Conversational query detection ───────────────────────────────────────
    /**
     * Conversational intent keywords — Vietnamese + English.
     * A query matching any of these is treated as an informational follow-up
     * and routed to a direct AI answer instead of a DB search.
     */
    private static final List<String> CONVERSATIONAL_KEYWORDS = List.of(
            "giải thích", "explain", "là gì", "là sao", "nghĩa là", "ý nghĩa",
            "tại sao", "why", "how", "như thế nào",
            "hướng dẫn", "guide", "help", "giúp");

    /**
     * Returns true when the query looks like an informational/conversational
     * message rather than a new warehouse-search request.
     */
    private boolean isConversationalQuery(String query) {
        if (query == null || query.isBlank()) return false;
        String q = query.toLowerCase();
        return CONVERSATIONAL_KEYWORDS.stream().anyMatch(q::contains);
    }

    /**
     * Build a prompt for a conversational (non-search) follow-up turn.
     * The AI answers the user's question in Vietnamese without producing JSON.
     */
    private String buildConversationalPrompt(String query, String history) {
        String historyBlock = (history != null && !history.isBlank())
                ? "\n## Lịch sử hội thoại\n" + history + "\n"
                : "";
        return "Bạn là trợ lý tư vấn kho lạnh của nền tảng Logicha (Việt Nam).\n"
                + "Hãy trả lời câu hỏi sau của người dùng bằng tiếng Việt, ngắn gọn và thân thiện.\n"
                + "Không cần tìm kho, chỉ cần trả lời thông tin người dùng hỏi.\n"
                + "QUAN TRỌNG: Không bắt đầu bằng lời chào (\'Chào bạn!\', \'Xin chào!\', ...). Hãy đi thẳng vào câu trả lời.\n\n"
                + historyBlock
                + "\nNgười dùng: \"" + query + "\"";
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

    // ─── Price/capacity hallucination guard ───────────────────────────────────
    // Vietnamese keywords that indicate the user actually specified a price.
    private static final List<String> PRICE_KEYWORDS = List.of(
            "giá", "tiền", "vnđ", "vnd", "đồng", "đ/", "rẻ", "rẻ nhất",
            "đắt", "phí", "ngân sách", "budget", "price", "cost",
            "triệu", "nghìn", "ngàn", "tháng", "tuần", "ngày", "năm");
    private static final List<String> TEMP_KEYWORDS = List.of(
            "nhiệt độ", "°c", "lạnh", "đông", "mát", "ấm", "temp", "temperature",
            "âm", "độ c");
    private static final List<String> CAPACITY_KEYWORDS = List.of(
            "diện tích", "sức chứa", "m3", "m²", "mét", "pallet", "tấn",
            "capacity", "area", "rộng", "lớn", "nhỏ");

    /**
     * Remove AI-hallucinated filter values that have no basis in the user's query.
     * If the query contains no price-related words, clear minPrice/maxPrice (and
     * likewise for temperature and capacity). This prevents an overly narrow
     * AI-inferred range from killing all results for simple queries like
     * "Tìm kho ở HCM".
     */
    private SearchCriteriaDTO sanitizeCriteria(String query, SearchCriteriaDTO c) {
        if (query == null || query.isBlank()) return c;
        String q = query.toLowerCase();

        boolean mentionsPrice    = PRICE_KEYWORDS.stream().anyMatch(q::contains);
        boolean mentionsTemp     = TEMP_KEYWORDS.stream().anyMatch(q::contains);
        boolean mentionsCapacity = CAPACITY_KEYWORDS.stream().anyMatch(q::contains);

        Double minPrice = c.minPrice();
        Double maxPrice = c.maxPrice();
        Double tempMin  = c.tempMin();
        Double tempMax  = c.tempMax();
        SearchCriteriaDTO.CapacityRange avail = c.availableCapacity();
        SearchCriteriaDTO.CapacityRange total = c.totalCapacity();

        if (!mentionsPrice) {
            log.info("[AI/sanitize] no price keyword in query — clearing minPrice/maxPrice ({}/{})", minPrice, maxPrice);
            minPrice = null;
            maxPrice = null;
        } else {
            // Even when user mentions price, treat 0/0 as hallucinated (no real warehouse is free)
            if (minPrice != null && minPrice == 0.0) minPrice = null;
            if (maxPrice != null && maxPrice == 0.0) maxPrice = null;
        }
        if (!mentionsTemp) {
            log.info("[AI/sanitize] no temp keyword in query — clearing tempMin/tempMax ({}/{})", tempMin, tempMax);
            tempMin = null;
            tempMax = null;
        }
        if (!mentionsCapacity) {
            log.info("[AI/sanitize] no capacity keyword in query — clearing capacity ranges");
            avail = null;
            total = null;
        }

        return new SearchCriteriaDTO(
                c.location(), minPrice, maxPrice,
                null, c.areaUnit(), c.name(),
                tempMin, tempMax, avail, total,
                c.rating(), c.sort());
    }

    /**
     * Normalise common AI schema deviations before Jackson deserialisation.
     * <ul>
     *   <li>Unwraps array wrapper: {@code [{...}]} → {@code {...}}</li>
     *   <li>{@code "sort": "price"}  → {@code "sort": {"type": "price"}}</li>
     *   <li>{@code "sort": "rating"} → {@code "sort": {"type": "rating"}}</li>
     *   <li>{@code "sort": null}     → {@code "sort": {"type": null}}</li>
     *   <li>{@code "rating": null}   → {@code "rating": {"min_range": null, "max_range": null}}</li>
     * </ul>
     */
    private String normalizeAiJson(String json) {
        if (json == null) return json;
        String trimmed = json.trim();

        // Unwrap array: AI sometimes returns [{...}] instead of {...}
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            if (trimmed.contains("\"location\"") || trimmed.contains("\"minPrice\"")) {
                trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
                log.info("[AI/normalize] unwrapped array wrapper");
            }
        }

        // Fix "sort": "<value>" or "sort": null  → "sort": {"type": "<value>"} / {"type": null}
        trimmed = trimmed.replaceAll(
                "\"sort\"\\s*:\\s*\"([^\"]+)\"",
                "\"sort\": {\"type\": \"$1\"}");
        trimmed = trimmed.replaceAll(
                "\"sort\"\\s*:\\s*null",
                "\"sort\": {\"type\": null}");

        // Fix "rating": null  → proper object (only when it's a plain null, not already {}
        trimmed = trimmed.replaceAll(
                "\"rating\"\\s*:\\s*null",
                "\"rating\": {\"min_range\": null, \"max_range\": null}");

        return trimmed;

    }

    private String callAgent(String prompt, String conversationHistory) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("query", prompt);
        if (conversationHistory != null && !conversationHistory.isBlank()) {
            body.put("conversationHistory", conversationHistory);
        }

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            log.info("[AI/agent] POST {} promptLen={}", AI_AGENT_URL, prompt.length());
            ResponseEntity<String> response = restTemplate.postForEntity(AI_AGENT_URL, request, String.class);
            String result = response.getBody();
            log.info("[AI/agent] status={} bodyLen={}", response.getStatusCode(), result == null ? 0 : result.length());
            if (result == null || result.isBlank()) {
                log.warn("[AI/agent] empty response — returning fallback criteria JSON");
                return "{\"location\":[{\"province\":null}],\"minPrice\":null,\"maxPrice\":null,\"priceType\":null,\"areaUnit\":\"m3\",\"name\":null,\"tempMin\":null,\"tempMax\":null,\"availableCapacity\":{\"min_range\":null,\"max_range\":null},\"totalCapacity\":{\"min_range\":null,\"max_range\":null},\"rating\":{\"min_range\":null,\"max_range\":null},\"sort\":{\"type\":null}}";
            }
            return result.trim();
        } catch (Exception e) {
            log.error("[AI/agent] connection error: {}", e.getMessage());
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