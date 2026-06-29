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
import java.util.Set;
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

    // Maps English priceType (from AI JSON) to the Vietnamese label stored in DB
    private static final Map<String, String> PRICE_TYPE_TO_TIER_LABEL = Map.of(
            "day", "Giá theo ngày",
            "week", "Giá theo tuần",
            "month", "Giá theo tháng",
            "year", "Giá theo năm");

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
     * 
     * <pre>
     * {
     *   "location": [{"province": "Hồ Chí Minh"}],
     *   "minPrice": null,
     *   "maxPrice": null,
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
     * 
     * Prices are normalized to monthly equivalents before querying the DB.
     *
     * @param criteria search criteria in AI JSON format
     * @return list of matching warehouses (no pagination)
     */
    public List<WarehouseResponseDTO> searchWarehouse(SearchCriteriaDTO criteria) {
        log.info(
                "[AiService/searchWarehouse] raw criteria — province='{}' priceType={} priceTier='{}' minPrice={} maxPrice={}",
                criteria.location() != null && !criteria.location().isEmpty()
                        ? criteria.location().get(0).province()
                        : "null",
                criteria.priceType(), criteria.priceTier(), criteria.minPrice(), criteria.maxPrice());

        SearchCriteriaDTO normalized = normalizeCriteriaForSearch(criteria);

        log.info("[AiService/searchWarehouse] normalized — priceTier='{}' minPrice={} maxPrice={}",
                normalized.priceTier(), normalized.minPrice(), normalized.maxPrice());

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

        // ── Upfront price-type guard ───────────────────────────────────────────────
        // Ask before any branching so BOTH initial-handshake and follow-up queries
        // are caught. Only fires when the user mentions price without a duration unit.
        if (isMissingPriceType(query)) {
            log.info("[AI/chat] no price type in query — returning clarification");
            String clarification = "Trước khi tìm kiếm, cho mình hỏi — bạn muốn xem giá theo đơn vị nào: theo ngày, theo tuần, theo tháng, hay theo năm?";
            Page<WarehouseResponseDTO> emptyPage = new PageImpl<>(Collections.emptyList(), Pageable.unpaged(), 0);
            int outputTokens = clarification.length() / 4;
            int remaining = balance - outputTokens;
            boolean tokenExhausted = remaining < 0;
            if (tokenExhausted)
                remaining = 0;
            user.getAiTier().setTokenOutput(remaining);
            userRepository.save(user);
            AiConversation convo = AiConversation.builder()
                    .user(user).criteria(query).message(clarification)
                    .totalInputTokens(0).totalOutputTokens(outputTokens)
                    .build();
            aiConversationRepository.save(convo);
            return new AiSearchResponseDTO(clarification, emptyPage, List.of(),
                    "{\"clarification\": \"price_type\"}", 0, outputTokens, tokenExhausted);
        }

        Page<WarehouseResponseDTO> warehousePage;
        String criteriaJson;
        int run1OutputTokens;

        log.info("[AI/chat] userId={} query='{}' isHandshake={}", userId, query, isInitialHandshake);

        if (isInitialHandshake || query == null || query.isBlank()) {
            // Initial handshake: FE already searched — just summarize and return its
            // candidates (capped at 12)
            warehousePage = toPageFromFeCandidates(matchingWarehouses, 9999);
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
                log.info("[AI/chat] fallback — prompt ({} chars):\n{}", run1Prompt.length(), run1Prompt);
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
                    log.warn("[AI/chat] fallback — failed to parse criteria JSON, using empty criteria: {}",
                            e.getMessage());
                    criteria = new SearchCriteriaDTO(null, null, null, null, "m3", null, null, null, null, null, null,
                            null, null, null, null);
                }
                criteria = sanitizeCriteria(query, criteria);
                log.info("[AI/chat] fallback — sanitized criteria: province={} priceTier='{}' minPrice={} maxPrice={}",
                        criteria.location() != null && !criteria.location().isEmpty()
                                ? criteria.location().get(0).province()
                                : "null",
                        criteria.priceTier(),
                        criteria.minPrice(), criteria.maxPrice());

                // Merge FE pre-filtered criteria (mount state) on top of AI criteria
                // so user-selected filters (e.g. price range from slider) are never
                // silently dropped by AI hallucination.
                criteria = mergeFeCriteria(feCriteria, criteria);
                log.info("[AI/chat] fallback — merged criteria: province={} minPrice={} maxPrice={} priceTier='{}'",
                        criteria.location() != null && !criteria.location().isEmpty()
                                ? criteria.location().get(0).province()
                                : "null",
                        criteria.minPrice(), criteria.maxPrice(), criteria.priceTier());

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
            if (tokenExhausted)
                remaining = 0;
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
            log.info("[AI/chat] prompt ({} chars):\n{}", run1Prompt.length(), run1Prompt);
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
                // Instead of giving up, fall back to FE-supplied criteria (which
                // represents the user's actual filter state from the UI) so we
                // still return meaningful warehouse results.
                log.warn("[AI/chat] JSON parse failed — falling back to FE criteria: {}", e.getMessage());
                criteria = new SearchCriteriaDTO(null, null, null, null, "m3", null, null, null, null, null,
                        null, null, null, null, null);
                run1OutputTokens = rawJson.length() / 4;
            }
            criteria = sanitizeCriteria(query, criteria);
            criteria = mergeFeCriteria(feCriteria, criteria);
            log.info("[AI/chat] sanitized+merged criteria: province={} minPrice={} maxPrice={}",
                    criteria.location() != null && !criteria.location().isEmpty()
                            ? criteria.location().get(0).province()
                            : "null",
                    criteria.minPrice(), criteria.maxPrice());

            List<WarehouseResponseDTO> results = searchWarehouse(criteria);
            warehousePage = new PageImpl<>(results, Pageable.unpaged(), results.size());
            log.info("[AI/chat] DB returned {} warehouses", warehousePage.getTotalElements());
        }

        String run2Prompt = buildSummaryPrompt(query, warehousePage);
        log.info("[AI/chat] calling agent for summary ({} warehouses in prompt)...",
                warehousePage.getNumberOfElements());
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

        // ── Out-of-coverage check: skip AI call entirely for unsupported cities
        // ───────
        String outOfCoverageCity = detectOutOfCoverageCity(query);
        if (outOfCoverageCity != null) {
            log.info("[AI/contextChat] query targets unsupported city '{}' — returning friendly message",
                    outOfCoverageCity);
            int inputTokens = (query == null ? 0 : query.length()) / 4;
            int outputTokens = 0;
            int remaining = balance - outputTokens;
            boolean tokenExhausted = remaining < 0;
            if (tokenExhausted)
                remaining = 0;
            user.getAiTier().setTokenOutput(remaining);
            userRepository.save(user);
            aiConversationRepository.save(AiConversation.builder()
                    .user(user).criteria(query == null ? "" : query)
                    .message("Xin lỗi bạn, hiện tại chúng mình chưa có kho lạnh tại " + outOfCoverageCity
                            + ". Bạn có thể thử tìm ở các khu vực lân cận như: "
                            + "Hồ Chí Minh, Bình Dương, Đồng Nai, Hà Nội, Hải Phòng, Đà Nẵng hoặc Cần Thơ nhé!")
                    .totalInputTokens(inputTokens).totalOutputTokens(outputTokens).build());
            Page<WarehouseResponseDTO> emptyPage = new PageImpl<>(List.of(), Pageable.unpaged(), 0);
            return new AiSearchResponseDTO(
                    "Xin lỗi bạn, hiện tại chúng mình chưa có kho lạnh tại " + outOfCoverageCity
                            + ". Bạn có thể thử tìm ở các khu vực lân cận như: "
                            + "Hồ Chí Minh, Bình Dương, Đồng Nai, Hà Nội, Hải Phòng, Đà Nẵng hoặc Cần Thơ nhé!",
                    emptyPage, List.of(), null, inputTokens, outputTokens, tokenExhausted);
        }

        // ── Pre-filter warehouses before sending to AI ──────────────────────────────
        // Apply hard constraints (capacity minimum, required certs) so the AI works on
        // a candidate set that definitely satisfies the user's non-negotiable
        // requirements.
        List<WarehouseResponseDTO> filteredWarehouses = applyHardFilters(query, warehouseList);
        log.info("[AI/contextChat] hard filters: {} of {} warehouses remain",
                filteredWarehouses.size(), warehouseList.size());

        String prompt = buildContextSearchPrompt(query, conversationHistory, filteredWarehouses);
        log.info("[AI/contextChat] calling agent...");
        String rawResponse = callAgent(prompt, conversationHistory);
        log.info("[AI/contextChat] raw response length={}", rawResponse.length());
        String cleaned = extractJsonFromMarkdown(rawResponse);

        // ── Parse AI response ───────────────────────────────────────────────────────
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
            log.warn("[AI/contextChat] JSON parse failed — friendly fallback: {}", e.getMessage());
            responseText = cleaned;
            if (cleaned.trim().startsWith("{") || cleaned.trim().startsWith("[")) {
                responseText = "Xin lỗi bạn, hệ thống đang gặp lỗi khi xử lý yêu cầu. Bạn có thể thử lại không?";
            }
            refinedIds = List.of();
        }

        // Fetch authoritative warehouse data from DB in ranked order
        List<WarehouseResponseDTO> rankedWarehouses = warehouseService.getWarehousesByIds(refinedIds);
        log.info("[AI/context] AI returned {} ids, DB resolved {} warehouses", refinedIds.size(),
                rankedWarehouses.size());

        int outputTokens = rawResponse.length() / 4;
        int inputTokens = (query == null ? 0 : query.length()) / 4;
        int remaining = balance - outputTokens;
        boolean tokenExhausted = remaining < 0;
        if (tokenExhausted)
            remaining = 0;

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

        // ── No results → return friendly message, NOT all warehouses ───────────────
        Page<WarehouseResponseDTO> warehousePage;
        if (refinedIds.isEmpty()) {
            String noResults = "Không tìm thấy kho nào phù hợp với yêu cầu của bạn. "
                    + "Bạn có thể thử điều chỉnh khu vực, nhiệt độ hoặc ngân sách nhé!";
            warehousePage = new PageImpl<>(List.of(), Pageable.unpaged(), 0);
            return new AiSearchResponseDTO(noResults, warehousePage, List.of(), null,
                    inputTokens, outputTokens, tokenExhausted);
        }

        warehousePage = new PageImpl<>(rankedWarehouses, Pageable.unpaged(), rankedWarehouses.size());
        return new AiSearchResponseDTO(responseText, warehousePage, refinedIds, null,
                inputTokens, outputTokens, tokenExhausted);
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
        sb.append(
                "  \"response\": \"<đoạn tóm tắt tiếng Việt 2-4 câu giới thiệu kho phù hợp nhất, KHÔNG chào hỏi>\",\n");
        sb.append("  \"refinedWarehouseIds\": [<danh sách ID kho, sắp xếp từ phù hợp nhất đến ít phù hợp nhất>]\n");
        sb.append("}\n\n");
        sb.append("Chỉ trả về JSON hợp lệ — không markdown, không giải thích thêm.\n");
        sb.append("Khi sắp xếp refinedWarehouseIds, ưu tiên các kho có Sponsor (hạng càng thấp càng cao cấp). "
                + "Nếu nhiều kho có cùng mức độ phù hợp, ưu tiên kho có hạng Sponsor tốt hơn.\n\n");
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
            if (w.sponsorTier() != null) {
                sb.append(" | Sponsor: ").append(w.sponsorTier().label())
                        .append(" (Hạng ").append(w.sponsorTier().priorityLevel()).append(")");
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
                        s.priceTiers().forEach(pt -> sb.append(pt.label()).append(": ").append(pt.value())
                                .append(" VND/").append(labelToPriceType(pt.label()))
                                .append("/").append(pt.areaUnit()).append(" "));
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
                String desc = w.description().length() > 200 ? w.description().substring(0, 200) + "..."
                        : w.description();
                sb.append("   Mô tả: ").append(desc).append("\n");
            }
            sb.append("\n");
        }

        sb.append(historyBlock);
        return sb.toString();
    }

    // ─── Out-of-coverage detection ───────────────────────────────────────────────

    private static final Set<String> OUT_OF_COVERAGE_CITIES = Set.of(
            "nha trang", "nhatrang", "khánh hòa", "khanh hoa",
            "vũng tàu", "vung tau", "bà rịa vũng tàu", "ba ria vung tau",
            "huế", "hue", "thừa thiên huế", "thua thien hue",
            "thanh hóa", "thanhhoa",
            "lâm đồng", "lam dong",
            "cà mau", "ca mau",
            "vĩnh long", "vinh long",
            "quảng ninh", "quangninh",
            "bình thuận", "binh thuan");

    /**
     * Returns the display name of the out-of-coverage city mentioned in the query,
     * or null if no unsupported city is detected.
     */
    private String detectOutOfCoverageCity(String query) {
        if (query == null || query.isBlank())
            return null;
        String q = query.toLowerCase();
        for (String city : OUT_OF_COVERAGE_CITIES) {
            if (q.contains(city)) {
                // Capitalize first letter for friendly display
                return city.substring(0, 1).toUpperCase() + city.substring(1);
            }
        }
        return null;
    }

    // ─── Hard filter for Context mode ──────────────────────────────────────────

    /**
     * Pre-filter warehouses by hard constraints (capacity minimum, required certs)
     * BEFORE sending to the AI. This ensures the AI only reasons over warehouses
     * that truly satisfy the user's non-negotiable requirements.
     */
    private List<WarehouseResponseDTO> applyHardFilters(String query, List<WarehouseResponseDTO> warehouses) {
        if (warehouses == null || warehouses.isEmpty())
            return warehouses;

        Double minCapacity = extractMinCapacity(query);
        Set<String> requiredCerts = extractRequiredCerts(query);

        if (minCapacity == null && requiredCerts.isEmpty())
            return warehouses;

        return warehouses.stream().filter(w -> {
            // Capacity check
            if (minCapacity != null) {
                double totalAvail = w.sections() == null ? 0
                        : w.sections().stream()
                                .mapToDouble(s -> s.availableCapacity() != null ? s.availableCapacity() : 0)
                                .sum();
                if (totalAvail < minCapacity)
                    return false;
            }

            // Cert check
            if (!requiredCerts.isEmpty()) {
                if (w.certificates() == null || w.certificates().isEmpty())
                    return false;
                Set<String> warehouseCerts = w.certificates().stream()
                        .map(cert -> cert.label().toLowerCase())
                        .collect(Collectors.toSet());
                boolean hasAll = requiredCerts.stream().allMatch(warehouseCerts::contains);
                if (!hasAll)
                    return false;
            }

            return true;
        }).collect(Collectors.toList());
    }

    private Double extractMinCapacity(String query) {
        if (query == null)
            return null;
        String q = query.toLowerCase();
        if (!q.contains("m³") && !q.contains("m3") && !q.contains("diện tích")
                && !q.contains("sức chứa") && !q.contains("mét"))
            return null;

        // Match patterns like:
        // "trên 500m³", "hơn 500 m³", "above 500 m³"
        // "500m³ trở lên", "500 m³ trở nên"
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "(?:trên|hơn|above|over)\\s*(\\d+(?:[.,]\\d+)?)\\s*m[³3]|"
                        + "(\\d+(?:[.,]\\d+)?)\\s*m[³3]\\s*(?:trở lên|trở nên)",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher m = p.matcher(q);
        if (m.find()) {
            String num = m.group(1) != null ? m.group(1) : m.group(2);
            if (num != null) {
                num = num.replace(",", ".");
                return Double.parseDouble(num);
            }
        }
        return null;
    }

    private Set<String> extractRequiredCerts(String query) {
        if (query == null)
            return Set.of();
        String q = query.toLowerCase();
        Set<String> required = new java.util.HashSet<>();
        // Map common cert abbreviations/names
        if (q.contains("haccp"))
            required.add("haccp");
        if (q.contains("iso 22000"))
            required.add("iso 22000");
        if (q.contains("iso 9001"))
            required.add("iso 9001");
        if (q.contains("gmp"))
            required.add("gmp");
        if (q.contains("gsp"))
            required.add("gsp");
        if (q.contains("cos") || q.contains("certificate of origin"))
            required.add("certificate of origin");
        if (q.contains("fda"))
            required.add("fda");
        return required;
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
        if (!(obj instanceof List<?> list))
            return Collections.emptyList();

        // Use a lenient mapper so FE-specific fields don't break the mapping
        ObjectMapper lenientMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        List<WarehouseResponseDTO> result = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> raw))
                continue;
            try {
                // FE uses different field names than the backend DTO — normalise before
                // converting so rating, id, and certifications map correctly.
                Map<String, Object> m = new HashMap<>();
                raw.forEach((k, v) -> m.put(String.valueOf(k), v));
                m.putIfAbsent("id", m.remove("id_warehouse"));
                m.putIfAbsent("averageRating", m.remove("ratingScore"));
                m.putIfAbsent("totalReviews", m.remove("ratingCount"));
                m.putIfAbsent("certificates", m.remove("certifications"));
                m.putIfAbsent("locationProvince", m.remove("location_province"));
                m.putIfAbsent("locationCommune", m.remove("location_commune"));
                m.putIfAbsent("locationAddressText", m.remove("location_address_text"));
                // stats sub-object — pull aggregated rating if top-level is still missing
                if (m.get("averageRating") == null && m.get("stats") instanceof Map<?, ?> stats) {
                    m.put("averageRating", stats.get("rating"));
                    m.put("totalReviews", stats.get("reviews"));
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
                .map(c -> "label=\"" + c.label() + "\""
                        + (c.description() != null ? " description=\"" + c.description() + "\"" : ""))
                .collect(Collectors.joining(", "));

        String sectionLabelList = meta.warehouseSectionLabels() != null && !meta.warehouseSectionLabels().isEmpty()
                ? meta.warehouseSectionLabels().stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(", "))
                : "(none available)";

        String priceTierLabelList = meta.priceTierLabels() != null && !meta.priceTierLabels().isEmpty()
                ? meta.priceTierLabels().stream().map(p -> "\"" + p + "\"").collect(Collectors.joining(", "))
                : "Giá theo ngày, Giá theo tháng, Giá theo tuần, Giá theo năm";

        String historyBlock = (history != null && !history.isBlank())
                ? "\n## Conversation History\n" + history + "\n"
                : "";

        return "You are a data extraction engine for a Vietnamese cold warehouse rental platform.\n\n"

                + "## Available Database Values\n"
                + "Provinces (use EXACT string): [" + provinceList + "]\n"
                + "Warehouse section labels: [" + sectionLabelList + "]\n"
                + "Price tier labels: [" + priceTierLabelList + "]\n"
                + "Certifications: [" + certList + "]\n"
                + "Temperature range in DB: " + meta.tempMin() + "°C to " + meta.tempMax() + "°C\n"
                + "Price range in DB: " + meta.priceMin() + " to " + meta.priceMax() + " VND\n"
                + "Available capacity range in DB: " + meta.capacityMin() + " to " + meta.capacityMax() + " m³\n\n"

                + "## Region → Province Mapping (apply when user mentions a region, not a specific city)\n"
                + "When the user says a REGION word, return ALL MATCHING provinces from the province list above.\n"
                + "Use these mappings as guidance (only use provinces that appear in the province list above):\n"
                + "Note: The city and province may have prefix Tỉnh/Thành phố like: Thành Phố Hồ Chí Minh, TP Hồ Chí Minh, Tỉnh Cà Mau \n"
                + "COMMON DIRECTIONS (East/West/North/South of Vietnam):\n"
                + "- North / miền Bắc / phía Bắc / northern / bắc / miền bắc → include: Hà Nội, Hải Phòng, Quảng Ninh, Hải Dương, Bắc Ninh, Hưng Yên, Thái Nguyên, Bắc Giang, Hà Nam, Ninh Bình, Nam Định, Thái Bình\n"
                + "- South / miền Nam / phía Nam / southern / nam / miền nam → include: Hồ Chí Minh, Bình Dương, Đồng Nai, Long An, Bà Rịa - Vũng Tàu, Cần Thơ, Bình Phước, Tây Ninh, Tiền Giang, Đồng Tháp, An Giang, Kiên Giang\n"
                + "- Central / miền Trung / phía Trung / central / trung → include: Đà Nẵng, Thừa Thiên Huế, Quảng Nam, Bình Định, Khánh Hòa, Quảng Ngãi, Gia Lai, Kon Tum, Đắk Lắk, Đắk Nông, Lâm Đồng, Phú Yên, Nghệ An, Thanh Hóa, Hà Tĩnh, Quảng Bình, Quảng Trị\n"
                + "- East / miền Đông / phía Đông / eastern / đông → include: Bình Dương, Đồng Nai, Bà Rịa - Vũng Tàu, Bình Phước, Hồ Chí Minh, Cần Thơ\n"
                + "- West / miền Tây / phía Tây / western / tây → include: Long An, Tiền Giang, Đồng Tháp, An Giang, Kiên Giang, Cần Thơ, Bạc Liêu, Hậu Giang, Sóc Trăng, Trà Vinh, Vĩnh Long, Bến Tre\n"
                + "VIETNAM SUB-REGIONS:\n"
                + "- Đông Bắc Bộ / Northeastern → include: Quảng Ninh, Hải Dương, Bắc Giang, Thái Nguyên, Bắc Ninh, Hưng Yên, Hải Phòng, Hà Nội, Hà Nam, Nam Định, Thái Bình, Ninh Bình\n"
                + "- Tây Bắc Bộ / Northwestern → include: Lào Cai, Yên Bái, Điện Biên, Hòa Bình, Sơn La, Lai Châu, Hà Giang, Cao Bằng, Lạng Sơn, Bắc Kạn, Tuyên Quang, Phú Thọ\n"
                + "- Đồng bằng sông Hồng / Red River Delta → include: Hà Nội, Hải Phòng, Bắc Ninh, Hưng Yên, Hải Dương, Nam Định, Thái Bình, Hà Nam, Ninh Bình, Quảng Ninh\n"
                + "- Bắc Trung Bộ / North Central Coast → include: Thanh Hóa, Nghệ An, Hà Tĩnh, Quảng Bình, Quảng Trị, Thừa Thiên Huế\n"
                + "- Duyên hải Nam Trung Bộ / South Central Coast → include: Đà Nẵng, Quảng Nam, Quảng Ngãi, Bình Định, Phú Yên, Khánh Hòa\n"
                + "- Tây Nguyên / Central Highlands → include: Gia Lai, Kon Tum, Đắk Lắk, Đắk Nông, Lâm Đồng\n"
                + "- Đông Nam Bộ / Southeastern → include: Hồ Chí Minh, Bình Dương, Đồng Nai, Bà Rịa - Vũng Tàu, Bình Phước, Tây Ninh\n"
                + "- Đồng bằng sông Cửu Long / Mekong Delta / ĐBSCL / Cửu Long → include: Cần Thơ, Long An, Tiền Giang, Đồng Tháp, An Giang, Kiên Giang, Bạc Liêu, Hậu Giang, Sóc Trăng, Trà Vinh, Vĩnh Long, Bến Tre\n"
                + "- Example 1: If the user says 'north', return ALL northern provinces that exist in the DB province list.\n"
                + "- Example 2: If the user says 'Đông Nam Bộ', return all southeastern provinces that exist in the DB province list.\n\n"

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
                + "        - label: String in Vietnamese, e.g. 'Giá theo tháng', 'Giá theo ngày', 'Giá theo tuần', 'Giá theo năm' (determines time period)\n"
                + "        - value: Double (price in VND)\n"
                + "        - unit: String currency unit (always 'VND')\n"
                + "        - timeUnit (derived from label): 'month' | 'week' | 'day' | 'year'\n"
                + "        - areaUnit: 'm3' | 'pallet' | 'chuyến'\n"
                + "- warehouseSection: List of section labels from the warehouse's sections (e.g. 'Phòng Đông Lạnh A1', 'Kho Mát Tầng 2'). Filter warehouses that have ALL listed section labels.\n"
                + "- priceTier: The rental price tier label — one of the priceTierLabels values from metadata (e.g. 'Giá theo ngày', 'Giá theo tháng', 'Giá theo tuần', 'Giá theo năm'). When the user mentions 'theo ngày', 'theo tháng', etc., map to this field.\n"

                + "## Output Requirements\n"
                + "⚠️  YOU MUST RETURN ONLY A VALID JSON OBJECT. No Vietnamese text, no explanations, no markdown, no apologies.\n"
                + "Even if you cannot determine specific values, return the JSON schema with null fields.\n"
                + "Example of a valid response (for a generic query):\n"
                + "{\"location\":[{\"province\":null}],\"minPrice\":null,\"maxPrice\":null,\"priceType\":null,\"areaUnit\":\"m3\",\"name\":null,\"tempMin\":null,\"tempMax\":null,\"availableCapacity\":{\"min_range\":null,\"max_range\":null},\"totalCapacity\":{\"min_range\":null,\"max_range\":null},\"rating\":{\"min_range\":null,\"max_range\":null},\"certificates\":null,\"sort\":{\"type\":null},\"warehouseSection\":null,\"priceTier\":\"Giá theo tháng\"}\n\n"

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
                + "  \"certificates\": [\"<certificate label string>\"],\n"
                + "  \"sort\": {\"type\": \"price\" | \"rating\" | null},\n"
                + "  \"warehouseSection\": [\"<section label from metadata>\"],\n"
                + "  \"priceTier\": \"<price tier label from metadata — Giá theo ngày | Giá theo tháng | Giá theo tuần | Giá theo năm | null>\"\n"
                + "}\n\n"

                + "## Field Rules\n"
                + "1. location — ONLY include provinces if the user explicitly names a city or region. The default is [{\"province\": null}].\n"
                + "   - If user mentions a SPECIFIC city/province: use that exact string.\n"
                + "   - If user mentions a REGION (north/south/central/miền Bắc/miền Nam/miền Trung): use the Region→Province Mapping above to return MULTIPLE province objects for all matching provinces FROM THE LIST.\n"
                + "   - If no location mentioned at all: [{\"province\": null}]. Do NOT guess or infer provinces based on the topic (e.g. \"cold storage\" or \"kho lạnh\" does not imply any particular province).\n"
                + "2. minPrice/maxPrice — ALWAYS null UNLESS the user explicitly mentions a price value (e.g. 'giá dưới 5 triệu', 'rẻ nhất', 'budget 3 triệu', 'giá 4-5 triệu'). Price range in DB is for YOUR REFERENCE ONLY — do NOT copy it into the output. Do NOT set these fields from the DB metadata range unless the user asks for it.\n"
                + "3. priceType — only if user explicitly mentions a time period for pricing (e.g. 'theo tháng', 'theo ngày'). null otherwise.\n"
                + "4. areaUnit — always \"m3\".\n"
                + "5. name — only if user mentions a specific warehouse name. null otherwise.\n"
                + "6. tempMin/tempMax — ALWAYS null unless the user explicitly mentions temperature (e.g. 'dưới -20°C', 'kho lạnh', 'nhiệt độ thấp'). The DB temperature range is for YOUR REFERENCE ONLY — do NOT copy it into the output.\n"
                + "7. availableCapacity/totalCapacity — ALWAYS null unless the user explicitly mentions capacity (e.g. 'hơn 1000m³', 'dưới 500m³'). The DB capacity range is for YOUR REFERENCE ONLY.\n"
                + "8. rating — 0–5 range. null if not mentioned.\n"
                + "9. certificates — list of certification labels mentioned by user (MUST exactly match labels from Certifications list above). null if not mentioned.\n"
                + "10. sort — \"price\" when user wants cheapest, \"rating\" when user wants highest rated, null otherwise.\n"
                + "11. warehouseSection — filter by section labels (e.g. 'Phòng Đông Lạnh A1'). null if user did not mention a specific section.\n"
                + "12. priceTier — when the user mentions a rental duration in ANY form — whether compact (e.g. 'tuần', 'tháng', 'ngày', 'năm', 'cho thuê theo tháng', 'thuê theo ngày') or a full phrase ('theo ngày', 'theo tháng', 'theo tuần', 'theo năm') — map it to the matching label from priceTierLabels (e.g. 'Giá theo ngày', 'Giá theo tháng', 'Giá theo tuần', 'Giá theo năm'). null if no duration is specified.\n\n"

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
            String sponsorInfo = (w.sponsorTier() != null)
                    ? " | Sponsor: " + w.sponsorTier().label() + " (Hạng " + w.sponsorTier().priorityLevel() + ")"
                    : "";
            sb.append(i + 1).append(". ").append(w.name()).append(" — ")
                    .append(w.locationProvince()).append(", ")
                    .append(w.locationCommune())
                    .append(sponsorInfo).append("\n");
            if (w.sections() != null && !w.sections().isEmpty()) {
                sb.append("   Phòng kho:\n");
                for (WarehouseSectionDTO s : w.sections()) {
                    sb.append("   - Nhiệt độ: ").append(s.tempMin()).append("~").append(s.tempMax()).append("°C");
                    sb.append(", Còn trống: ").append(s.availableCapacity()).append("m³");
                    if (s.priceTiers() != null && !s.priceTiers().isEmpty()) {
                        sb.append(", Giá: ");
                        s.priceTiers().forEach(pt -> sb.append(pt.label()).append(": ").append(pt.value())
                                .append(" VND/").append(labelToPriceType(pt.label()))
                                .append("/").append(pt.areaUnit()).append(" "));
                    }
                    sb.append("\n");
                }
            }
            if (w.averageRating() != null && w.averageRating() > 0) {
                sb.append("   Rating: ").append(String.format("%.1f", w.averageRating()))
                        .append("/5 (").append(w.totalReviews()).append(" đánh giá)\n");
            }
            sb.append("   ").append(w.description()).append("\n\n");
        }

        sb.append(
                "\nKhi đề xuất kho, ưu tiên các kho có Sponsor (hạng càng thấp càng cao cấp). "
                        + "Nếu nhiều kho có cùng mức độ phù hợp, ưu tiên kho có hạng Sponsor tốt hơn.\n"
                        + "Viết phản hồi bằng tiếng Việt, tự nhiên, thân thiện. Không bắt đầu bằng lời chào. Nếu không có kho nào phù hợp, hãy thông báo lịch sự.");
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
     * Keywords that signal the user is asking about price/affordability.
     * Must be SPECIFIC to price — generic rental intent is NOT included.
     */
    private static final List<String> AFFORDABILITY_KEYWORDS = List.of(
            "giá rẻ", "rẻ nhất", "giá thấp", "giá bình dân", "giá mềm",
            "kho lạnh rẻ", "kho giá rẻ", "tìm kho giá", "tìm kho rẻ", "giá mắc", "gia mac", "kho mac", "mac tien",
            "mắc tiền", "gia re", "gia re nhat", "gia thap", "gia binh dan", "gia mem",
            "kho bình dân", "kho giá thấp", "kho giá mềm", "kho gia re", "kho gia re nhat", "kho gia thap",
            "kho gia binh dan", "kho gia mem", "kho gia re", "kho gia re nhat", "kho gia thap",
            "kho gia binh dan", "kho gia mem");

    /**
     * Time-unit keywords that make a price query specific enough to search
     * directly.
     */
    private static final List<String> TIME_UNIT_KEYWORDS = List.of(
            "ngày", "day", "days",
            "tuần", "week", "weeks",
            "tháng", "month", "months",
            "năm", "year", "years",
            "/ngày", "/day", "/days",
            "/tuần", "/week", "/weeks",
            "/tháng", "/month", "/months",
            "/năm", "/year", "/years",
            "mỗi ngày", "mỗi tuần", "mỗi tháng", "mỗi năm",
            "theo ngày", "theo tuần", "theo tháng", "theo năm",
            "tính theo ngày", "tính theo tuần", "tính theo tháng", "tính theo năm",
            "trên ngày", "trên tuần", "trên tháng", "trên năm",
            "vnd/ngày", "vnd/day", "vnd/ngày",
            "vnd/tuần", "vnd/week", "vnd/tháng", "vnd/month", "vnd/năm", "vnd/year",
            "đ/ngày", "đ/day", "đ/tuần", "đ/week", "đ/tháng", "đ/month", "đ/năm", "đ/year",
            "m3/ngày", "m3/day", "m3/tuần", "m3/week", "m3/tháng", "m3/month", "m3/năm", "m3/year",
            "triệu/ngày", "triệu/day", "triệu/tuần", "triệu/tháng", "triệu/năm",
            "k/ngày", "k/day", "k/tuần", "k/week", "k/tháng", "k/month", "k/năm", "k/year");

    /**
     * Returns true when the query looks like an informational/conversational
     * message rather than a new warehouse-search request.
     */
    private boolean isConversationalQuery(String query) {
        if (query == null || query.isBlank())
            return false;
        String q = query.toLowerCase();
        return CONVERSATIONAL_KEYWORDS.stream().anyMatch(q::contains);
    }

    /**
     * Returns true when the query mentions price (affordability keyword or numeric
     * amount)
     * but does NOT specify a rental duration unit (day/week/month/year).
     * Fires for:
     * - Affordability: "kho lạnh giá rẻ", "tìm kho giá thấp" (no time unit)
     * - Numeric price: "giá dưới 30 triệu", "dưới 500k" (no time unit)
     * Does NOT fire when the query already specifies ngày/tuần/tháng/năm.
     */
    private boolean isMissingPriceType(String query) {
        if (query == null || query.isBlank())
            return false;
        String q = query.toLowerCase();

        // Check 1: contains an affordability keyword
        boolean hasAffordability = AFFORDABILITY_KEYWORDS.stream().anyMatch(q::contains);

        // Check 2: contains a numeric price amount (e.g. "30 triệu", "500k", "1 triệu
        // đồng")
        boolean hasNumericPrice = PRICE_AMOUNT_PATTERN.matcher(q).find();

        if (!hasAffordability && !hasNumericPrice)
            return false;

        // Check 3: contains a time-unit keyword as a standalone word → clarification
        // not needed
        for (String unit : TIME_UNIT_KEYWORDS) {
            int idx = q.indexOf(unit);
            if (idx >= 0) {
                boolean validBoundaryBefore = (idx == 0) || Character.isWhitespace(q.charAt(idx - 1));
                boolean validBoundaryAfter = (idx + unit.length() == q.length())
                        || Character.isWhitespace(q.charAt(idx + unit.length()));
                if (validBoundaryBefore && validBoundaryAfter)
                    return false; // time unit found → clarification not needed
            }
        }
        return true; // price mentioned, no duration unit → ask user
    }

    private static final java.util.regex.Pattern PRICE_AMOUNT_PATTERN = java.util.regex.Pattern.compile(
            "(\\d[\\d.,]*\\s*(?:triệu|tỷ|nghìn|ngàn|k)\\b|\\d[\\d.,]*\\s*đồng|\\d[\\d.,]*\\s*vnd)",
            java.util.regex.Pattern.CASE_INSENSITIVE);

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
            // Normalise Vietnamese priceType values to English before lookup
            String unit = vietnamesePriceTypeToEnglish(criteria.priceType().get(0));
            Double multiplier = PRICE_MULTIPLIER.getOrDefault(unit.toLowerCase(), 1.0);
            log.info("[AI/normalize] priceType='{}' → unit='{}' multiplier={}", criteria.priceType().get(0), unit,
                    multiplier);
            if (!"month".equalsIgnoreCase(unit)) {
                minPrice = minPrice != null ? minPrice * multiplier : null;
                maxPrice = maxPrice != null ? maxPrice * multiplier : null;
            }
        }

        // Rebuild with normalized prices and null priceType (backend always works in
        // monthly)
        String priceTier = criteria.priceTier();
        if (priceTier == null && criteria.priceType() != null && !criteria.priceType().isEmpty()) {
            priceTier = PRICE_TYPE_TO_TIER_LABEL.get(criteria.priceType().get(0).toLowerCase());
            log.info("[AI/normalize] priceTier derived from priceType '{}' → '{}'",
                    criteria.priceType().get(0), priceTier);
        }

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
                criteria.certificates(),
                criteria.sort(),
                criteria.warehouseSection(),
                priceTier);
    }

    // ─── Price/capacity hallucination guard ───────────────────────────────────
    // Vietnamese keywords that indicate the user actually specified a price.
    /**
     * Keywords that signal the user is asking about a PRICE AMOUNT (not just a
     * time-unit like "tháng" / "tuần"). Used to decide whether to keep or clear
     * minPrice/maxPrice after AI extraction.
     */
    private static final List<String> PRICE_VALUE_KEYWORDS = List.of(
            "giá", "tiền", "vnđ", "vnd", "đồng", "đ/",
            "rẻ", "rẻ nhất", "đắt",
            "phí", "ngân sách", "budget", "price", "cost",
            "triệu", "nghìn", "ngàn");

    /**
     * Time-unit keywords that pair with a price (e.g. "tháng", "tuần") to form
     * a complete price-filter signal, but on their own are NOT enough to keep
     * a hallucinated minPrice/maxPrice.
     */
    private static final List<String> PRICE_TIME_UNIT_KEYWORDS = List.of(
            "tháng", "tuần", "ngày", "năm",
            "/tháng", "/tuần", "/ngày", "/năm",
            "theo tháng", "theo tuần", "theo ngày", "theo năm",
            "vnd/tháng", "vnd/tuần", "vnd/ngày", "vnd/năm",
            "đ/tháng", "đ/tuần", "đ/ngày", "đ/năm");

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
        if (query == null || query.isBlank())
            return c;
        String q = query.toLowerCase();

        // A price filter should only be kept when the user mentions an actual
        // price value (e.g. "giá 5 triệu", "rẻ", "budget"). Time-unit words
        // alone ("tháng", "tuần", "ngày", "năm") signal priceTier/priceType
        // but do NOT imply a price range — clearing them prevents AI from
        // hallucinating minPrice/maxPrice from the DB price-range hint.
        boolean mentionsPriceValue = PRICE_VALUE_KEYWORDS.stream().anyMatch(q::contains);
        boolean mentionsTemp = TEMP_KEYWORDS.stream().anyMatch(q::contains);
        boolean mentionsCapacity = CAPACITY_KEYWORDS.stream().anyMatch(q::contains);

        Double minPrice = c.minPrice();
        Double maxPrice = c.maxPrice();
        Double tempMin = c.tempMin();
        Double tempMax = c.tempMax();
        SearchCriteriaDTO.CapacityRange avail = c.availableCapacity();
        SearchCriteriaDTO.CapacityRange total = c.totalCapacity();

        if (!mentionsPriceValue) {
            log.info("[AI/sanitize] no price VALUE in query — clearing minPrice/maxPrice ({}/{})", minPrice,
                    maxPrice);
            minPrice = null;
            maxPrice = null;
        } else {
            // Even when user mentions price, treat 0/0 as hallucinated (no real warehouse
            // is free)
            if (minPrice != null && minPrice == 0.0)
                minPrice = null;
            if (maxPrice != null && maxPrice == 0.0)
                maxPrice = null;
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

        // Location backstop: if the query does not reference any province / city /
        // region / location-hint token, drop any province values the AI invented.
        List<SearchCriteriaDTO.LocationDTO> location = c.location();
        if (!WarehouseService.queryMentionsLocation(query)
                && location != null && !location.isEmpty()) {
            log.info("[AI/sanitize] no location keyword in query — clearing location list (AI returned {})",
                    location.stream().map(SearchCriteriaDTO.LocationDTO::province).toList());
            location = null;
        }

        return new SearchCriteriaDTO(
                location, minPrice, maxPrice,
                null, c.areaUnit(), c.name(),
                tempMin, tempMax, avail, total,
                c.rating(), c.certificates(), c.sort(),
                c.warehouseSection(), c.priceTier());
    }

    /**
     * Merges FE-supplied criteria (from mount/filter state) into AI-extracted
     * criteria. FE values represent explicit user selections and therefore
     * take priority over AI-inferred values for sensitive filter fields
     * (price range, price tier, location, certificates, warehouse section).
     *
     * @param feCriteria raw FE object (may be null or a Map from the JSON body)
     * @param ai         criteria extracted (and sanitized) by the AI
     * @return a new SearchCriteriaDTO with FE overrides applied where present
     */
    private SearchCriteriaDTO mergeFeCriteria(Object feCriteria, SearchCriteriaDTO ai) {
        if (feCriteria == null)
            return ai;
        @SuppressWarnings("unchecked")
        Map<String, Object> feMap;
        try {
            feMap = objectMapper.convertValue(feCriteria, Map.class);
        } catch (Exception e) {
            log.warn("[AI/merge] failed to parse FE criteria, using AI-only: {}", e.getMessage());
            return ai;
        }
        if (feMap == null || feMap.isEmpty())
            return ai;

        Double minPrice = ai.minPrice();
        Double maxPrice = ai.maxPrice();
        List<String> priceType = ai.priceType();
        String priceTier = ai.priceTier();
        List<SearchCriteriaDTO.LocationDTO> location = ai.location();
        List<String> certificates = ai.certificates();
        List<String> warehouseSection = ai.warehouseSection();
        Double tempMin = ai.tempMin();
        Double tempMax = ai.tempMax();
        SearchCriteriaDTO.CapacityRange avail = ai.availableCapacity();
        SearchCriteriaDTO.CapacityRange total = ai.totalCapacity();
        SearchCriteriaDTO.RatingRange rating = ai.rating();
        SearchCriteriaDTO.SortType sort = ai.sort();
        String name = ai.name();
        String areaUnit = ai.areaUnit();

        // ── minPrice / maxPrice: FE wins if present (user explicitly set a range) ──
        Object feMin = feMap.get("minPrice");
        Object feMax = feMap.get("maxPrice");
        if (feMin instanceof Number n)
            minPrice = n.doubleValue();
        if (feMax instanceof Number n)
            maxPrice = n.doubleValue();

        // ── priceTier: FE wins if non-null/non-empty ──
        Object feTier = feMap.get("priceTier");
        if (feTier instanceof String s && !s.isBlank() && !"null".equalsIgnoreCase(s.trim())) {
            priceTier = s.trim();
        }

        // ── priceType: FE wins if non-null ──
        Object fePriceType = feMap.get("priceType");
        if (fePriceType instanceof List<?> l && !l.isEmpty()) {
            priceType = l.stream().map(Object::toString).toList();
        }

        // ── location: FE wins if list has at least one non-null province ──
        Object feLoc = feMap.get("location");
        if (feLoc instanceof List<?> locList && !locList.isEmpty()) {
            List<SearchCriteriaDTO.LocationDTO> feLocations = locList.stream()
                    .filter(o -> o instanceof Map)
                    .map(o -> (Map<String, Object>) o)
                    .map(m -> {
                        Object p = m.get("province");
                        String prov = (p instanceof String s) ? s.trim() : null;
                        if (prov != null && !prov.isBlank() && !"null".equalsIgnoreCase(prov))
                            return new SearchCriteriaDTO.LocationDTO(prov);
                        return null;
                    })
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (!feLocations.isEmpty())
                location = feLocations;
        }

        // ── certificates: FE wins if list is non-null and non-empty ──
        Object feCerts = feMap.get("certificates");
        if (feCerts instanceof List<?> certList && !certList.isEmpty()) {
            certificates = certList.stream().map(Object::toString).toList();
        }

        // ── warehouseSection: FE wins if list is non-null and non-empty ──
        Object feSection = feMap.get("warehouseSection");
        if (feSection instanceof List<?> secList && !secList.isEmpty()) {
            warehouseSection = secList.stream().map(Object::toString).toList();
        }

        // ── tempMin / tempMax: FE wins if present ──
        Object feTempMin = feMap.get("tempMin");
        Object feTempMax = feMap.get("tempMax");
        if (feTempMin instanceof Number n)
            tempMin = n.doubleValue();
        if (feTempMax instanceof Number n)
            tempMax = n.doubleValue();

        // ── rating: FE wins if present ──
        Object feRating = feMap.get("rating");
        if (feRating instanceof Map<?, ?> rMap) {
            Double rm = null, rx = null;
            Object rMin = rMap.get("min_range");
            Object rMax = rMap.get("max_range");
            if (rMin instanceof Number n)
                rm = n.doubleValue();
            if (rMax instanceof Number n)
                rx = n.doubleValue();
            if (rm != null || rx != null)
                rating = new SearchCriteriaDTO.RatingRange(rm, rx);
        }

        // ── sort: FE wins if present ──
        Object feSort = feMap.get("sort");
        if (feSort instanceof Map<?, ?> sMap) {
            Object st = sMap.get("type");
            if (st instanceof String s && !s.isBlank() && !"null".equalsIgnoreCase(s.trim())) {
                sort = new SearchCriteriaDTO.SortType(s.trim());
            }
        }

        return new SearchCriteriaDTO(
                location, minPrice, maxPrice,
                priceType, areaUnit, name,
                tempMin, tempMax, avail, total,
                rating, certificates, sort,
                warehouseSection, priceTier);
    }

    /**
     * Normalise common AI schema deviations before Jackson deserialisation.
     * <ul>
     * <li>Unwraps array wrapper: {@code [{...}]} → {@code {...}}</li>
     * <li>{@code "sort": "price"} → {@code "sort": {"type": "price"}}</li>
     * <li>{@code "sort": "rating"} → {@code "sort": {"type": "rating"}}</li>
     * <li>{@code "sort": null} → {@code "sort": {"type": null}}</li>
     * <li>{@code "rating": null} →
     * {@code "rating": {"min_range": null, "max_range": null}}</li>
     * </ul>
     */
    private String normalizeAiJson(String json) {
        if (json == null)
            return json;
        String trimmed = json.trim();

        // Unwrap array: AI sometimes returns [{...}] instead of {...}
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            if (trimmed.contains("\"location\"") || trimmed.contains("\"minPrice\"")) {
                trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
                log.info("[AI/normalize] unwrapped array wrapper");
            }
        }

        // Fix "sort": "<value>" or "sort": null → "sort": {"type": "<value>"} /
        // {"type": null}
        trimmed = trimmed.replaceAll(
                "\"sort\"\\s*:\\s*\"([^\"]+)\"",
                "\"sort\": {\"type\": \"$1\"}");
        trimmed = trimmed.replaceAll(
                "\"sort\"\\s*:\\s*null",
                "\"sort\": {\"type\": null}");

        // Fix "rating": null → proper object (only when it's a plain null, not already
        // {}
        trimmed = trimmed.replaceAll(
                "\"rating\"\\s*:\\s*null",
                "\"rating\": {\"min_range\": null, \"max_range\": null}");

        // Fix "priceType": "month" → "priceType": ["month"]
        trimmed = trimmed.replaceAll(
                "\"priceType\"\\s*:\\s*\"([^\"]+)\"",
                "\"priceType\": [\"$1\"]");

        // Safety net: if priceType is set but priceTier is missing, derive priceTier
        // from priceType. The AI often sets priceType="month" from "theo tháng"
        // but forgets to also set priceTier="Giá theo tháng".
        if (trimmed.contains("\"priceType\"") && !trimmed.contains("\"priceTier\"")) {
            String priceType = null;
            Matcher ptMatch = Pattern.compile("\"priceType\"\\s*:\\s*\\[\\s*\"([^\"]+)\"\\s*\\]").matcher(trimmed);
            if (ptMatch.find()) {
                priceType = ptMatch.group(1).toLowerCase();
            }
            if (priceType != null && PRICE_TYPE_TO_TIER_LABEL.containsKey(priceType)) {
                String derivedTier = PRICE_TYPE_TO_TIER_LABEL.get(priceType);
                trimmed = trimmed.replaceAll(
                        "\"priceTier\"\\s*:\\s*null",
                        "\"priceTier\": \"" + derivedTier + "\"");
                // If priceTier doesn't appear at all, inject it before the closing brace
                if (!trimmed.contains("\"priceTier\"")) {
                    int brace = trimmed.lastIndexOf('}');
                    if (brace > 0) {
                        trimmed = trimmed.substring(0, brace)
                                + ", \"priceTier\": \"" + derivedTier + "\""
                                + trimmed.substring(brace);
                    }
                }
                log.info("[AI/normalize] derived priceTier='{}' from priceType='{}'", derivedTier, priceType);
            }
        }

        // Fix "warehouseSection": [{...}] → unwrap to just the labels array
        trimmed = trimmed.replaceAll(
                "\"warehouseSection\"\\s*:\\s*\\[\\s*\\{\"label\"\\s*:\\s*\"([^\"]+)\"\\s*\\}\\s*\\]",
                "\"warehouseSection\": [\"$1\"]");

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
     * Derives the English time-unit key from a Vietnamese PriceTier label.
     * <ul>
     * <li>{@code "Thuê tháng"} → {@code "month"}</li>
     * <li>{@code "Thuê ngày"} → {@code "day"}</li>
     * <li>{@code "Thuê tuần"} → {@code "week"}</li>
     * <li>{@code "Thuê năm"} → {@code "year"}</li>
     * </ul>
     *
     * @param label the Vietnamese label stored in
     *              {@link com.ailogis.api.entity.PriceTier#getLabel()}
     * @return English time unit string compatible with {@link #PRICE_MULTIPLIER}
     */
    private String labelToPriceType(String label) {
        if (label == null)
            return "month";
        String l = label.toLowerCase();
        if (l.contains("ngày"))
            return "ngày";
        if (l.contains("tuần"))
            return "tuần";
        if (l.contains("năm"))
            return "năm";
        if (l.contains("tháng"))
            return "tháng";
        return "tháng"; // safe default
    }

    /**
     * Normalises a priceType value that may arrive in Vietnamese (from the AI or
     * FE)
     * to the English key expected by {@link #PRICE_MULTIPLIER}.
     *
     * @param priceType raw value, e.g. {@code "tháng"}, {@code "month"},
     *                  {@code "day"}
     * @return canonical English key: {@code "month"}, {@code "day"},
     *         {@code "week"}, or {@code "year"}
     */
    private String vietnamesePriceTypeToEnglish(String priceType) {
        if (priceType == null)
            return "month";
        String p = priceType.trim().toLowerCase();
        return switch (p) {
            case "tháng", "thang", "month" -> "tháng";
            case "ngày", "ngay", "day" -> "ngày";
            case "tuần", "tuan", "week" -> "tuần";
            case "năm", "nam", "year" -> "năm";
            default -> p; // pass through — PRICE_MULTIPLIER.getOrDefault will fallback to 1.0
        };
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