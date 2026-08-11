package com.ailogis.api.service;

import com.ailogis.api.dto.*;
import com.ailogis.api.entity.*;
import com.ailogis.api.mapper.WarehouseMapper;
import com.ailogis.api.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RenterService {

        private final UserRepository userRepository;
        private final ContractRepository contractRepository;
        private final RentalRequestRepository rentalRequestRepository;
        private final AiConversationRepository aiConversationRepository;
        private final TransactionRepository transactionRepository;
        private final AiSubscriptionTierRepository aiTierRepository;
        private final PaymentService paymentService;
        private final BookmarkRepository bookmarkRepository;
        private final WarehouseRepository warehouseRepository;
        private final WarehouseMapper warehouseMapper;
        private final ReviewRepository reviewRepository;

        /**
         * The buyer's latest COMPLETED AI_SUBSCRIPTION transaction, only if its 1-month
         * window is still valid. Distinct from {@code User.aiTier}, which reflects the
         * tier the renter has *chosen* (possibly a not-yet-billed scheduled switch) —
         * this reflects what's actually granting token access right now.
         */
        private Optional<Transaction> findActiveAiTransaction(Long renterId) {
                return transactionRepository
                                .findFirstByBuyerIdAndTypeAndStatusOrderByCreatedAtDesc(renterId, "AI_SUBSCRIPTION",
                                                "COMPLETED")
                                .filter(tx -> LocalDateTime.now().isBefore(tx.getCreatedAt().plusMonths(1)));
        }

        public RenterStatisticResponseDTO getRenterStatistics(Long renterId, int expireDaysAlert) {
                User renter = userRepository.findById(renterId)
                                .orElseThrow(() -> new RuntimeException("Không tìm thấy Renter!"));

                Long totalWarehouse = contractRepository.countDistinctWarehousesByRenterId(renterId);
                Long totalAiConv = aiConversationRepository.countByUserId(renterId);

                Optional<Transaction> active = findActiveAiTransaction(renterId);

                long tokenUsage = 0L;
                if (active.isPresent()) {
                        LocalDateTime windowStart = active.get().getCreatedAt();
                        LocalDateTime windowEnd = windowStart.plusMonths(1);
                        Long usedInput = aiConversationRepository.sumInputTokensByUserIdAndDateRange(renterId, windowStart,
                                        windowEnd);
                        Long usedOutput = aiConversationRepository.sumOutputTokensByUserIdAndDateRange(renterId, windowStart,
                                        windowEnd);
                        tokenUsage = (usedInput != null ? usedInput : 0) + (usedOutput != null ? usedOutput : 0);
                }

                Double totalBilling = transactionRepository.sumTotalBillingByBuyerId(renterId);
                if (totalBilling == null)
                        totalBilling = 0.0;

                String aiTierName = (renter.getAiTier() != null) ? renter.getAiTier().getLabel() : "Chưa đăng ký";
                String activeAiTierLabel = active.map(tx -> tx.getSubscription().getLabel()).orElse(null);

                Long totalReq = rentalRequestRepository.countByRenterId(renterId);
                Long totalOwnerUpdated = rentalRequestRepository.countOwnerUpdatedRequests(renterId);
                Long totalActiveContract = contractRepository.countByRenterIdAndStatus(renterId,
                                com.ailogis.api.enums.ContractStatus.ACTIVE);

                LocalDate targetExpireDate = LocalDate.now().plusDays(expireDaysAlert);
                Long endOfContract = contractRepository.countExpiringContracts(renterId, targetExpireDate);

                return new RenterStatisticResponseDTO(
                                totalWarehouse, totalAiConv, tokenUsage, totalBilling, aiTierName,
                                totalReq, totalOwnerUpdated, totalActiveContract, endOfContract, activeAiTierLabel);
        }

        public List<AiTierDTO> getActiveAiTiers() {
                return aiTierRepository.findAll().stream()
                                .map(tier -> new AiTierDTO(tier.getId(), tier.getLabel(), tier.getDescription(),
                                                tier.getTokenInput(), tier.getTokenOutput(), tier.getPrice(),
                                                tier.getUnit(), null))
                                .toList();
        }

        @Transactional
        public PaymentResponseDTO buyAiSubscription(Long renterId, Long tierId,
                        jakarta.servlet.http.HttpServletRequest request, boolean immediate) {
                User renter = userRepository.findById(renterId)
                                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));
                AiSubscriptionTier aiTier = aiTierRepository.findById(tierId)
                                .orElseThrow(() -> new RuntimeException("Gói AI không tồn tại!"));

                // Đã có gói đang hoạt động và không ép buộc đổi ngay -> chỉ ghi nhận lựa chọn
                // mới lên User.aiTier, không tính phí, không tạo Transaction. Gói mới sẽ được
                // tính phí và áp dụng khi gói hiện tại hết hạn (qua luồng renewal có sẵn).
                if (!immediate && findActiveAiTransaction(renterId).isPresent()) {
                        renter.setAiTier(aiTier);
                        userRepository.save(renter);
                        return new PaymentResponseDTO(null);
                }

                if (aiTier.getPrice() == 0) {
                        renter.setAiTier(aiTier);
                        userRepository.save(renter);

                        Transaction freeGrant = Transaction.builder()
                                        .buyer(renter)
                                        .subscription(aiTier)
                                        .amount(0.0)
                                        .type("AI_SUBSCRIPTION")
                                        .status("COMPLETED")
                                        .createdAt(LocalDateTime.now())
                                        .invoiceDate(LocalDateTime.now())
                                        .build();
                        transactionRepository.save(freeGrant);

                        return new PaymentResponseDTO(null);
                }

                Transaction transaction = Transaction.builder()
                                .buyer(renter)
                                .subscription(aiTier)
                                .amount(aiTier.getPrice())
                                .type("AI_SUBSCRIPTION")
                                .status("PENDING")
                                .createdAt(LocalDateTime.now())
                                .invoiceDate(LocalDateTime.now())
                                .build();

                transaction = transactionRepository.save(transaction);
                String paymentUrl = paymentService.createPayOSPaymentLink(transaction);
                return new PaymentResponseDTO(paymentUrl);
        }

        /**
         * Resolves whether {@code user}'s AI subscription window has lapsed and, if so,
         * returns the tier id that should be offered for renewal. Returns null when the
         * user has cancelled (aiTier == null) or their current window is still active —
         * cancellation is an explicit opt-out and shouldn't be second-guessed by a prompt.
         */
        public Long getAiRenewalTierId(User user) {
                if (user.getAiTier() == null) {
                        return null;
                }

                return transactionRepository
                                .findFirstByBuyerIdAndTypeAndStatusOrderByCreatedAtDesc(user.getId(), "AI_SUBSCRIPTION",
                                                "COMPLETED")
                                .filter(tx -> !LocalDateTime.now().isBefore(tx.getCreatedAt().plusMonths(1)))
                                .map(tx -> user.getAiTier().getId())
                                .orElse(null);
        }

        @Transactional
    public void cancelAiSubscription(Long renterId) {
        User renter = userRepository.findById(renterId)
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));
        renter.setAiTier(null);
        userRepository.save(renter);
    }

        @Transactional
        public String toggleBookmark(Long renterId, Long warehouseId) {
                Optional<Bookmark> existing = bookmarkRepository.findByUserIdAndWarehouseId(renterId, warehouseId);
                if (existing.isPresent()) {
                        bookmarkRepository.delete(existing.get());
                        return "Đã gỡ kho bãi khỏi danh sách yêu thích!";
                } else {
                        User renter = userRepository.findById(renterId).orElseThrow();
                        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                                        .orElseThrow(() -> new RuntimeException("Không tìm thấy kho bãi!"));

                        bookmarkRepository.save(Bookmark.builder().user(renter).warehouse(warehouse).build());
                        return "Đã thêm kho bãi vào danh sách yêu thích!";
                }
        }

        public List<WarehouseResponseDTO> getMyBookmarks(Long renterId) {
                return bookmarkRepository.findByUserId(renterId).stream()
                                .map(b -> warehouseMapper.toWarehouseResponseDTO(b.getWarehouse()))
                                .toList();
        }

        @Transactional
        public ReviewResponseDTO createReview(Long renterId, Long warehouseId, ReviewCreateDTO dto) {
                User renter = userRepository.findById(renterId)
                                .orElseThrow(() -> new RuntimeException("Không tìm thấy Renter!"));
                Warehouse warehouse = warehouseRepository.findById(warehouseId)
                                .orElseThrow(() -> new RuntimeException("Không tìm thấy kho bãi!"));

                boolean hasRented = contractRepository.findByWarehouseIdWithFilter(warehouseId, null)
                                .stream().anyMatch(c -> c.getRenter().getId().equals(renterId));

                if (!hasRented) {
                        throw new RuntimeException(
                                        "Bạn chỉ được phép đánh giá kho bãi sau khi đã từng giao dịch thuê kho này!");
                }

                // Tìm xem Renter đã đánh giá kho này chưa. Nếu có rồi thì ghi đè lên đánh giá
                // cũ, nếu chưa thì tạo mới.
                Review review = reviewRepository.findByUserIdAndWarehouseId(renterId, warehouseId)
                                .orElse(Review.builder().user(renter).warehouse(warehouse).build());
                review.setRating(dto.rating());
                review.setComment(dto.comment());

                review = reviewRepository.save(review);

                return new ReviewResponseDTO(
                                review.getId(),
                                renter.getFullName(),
                                review.getRating(),
                                review.getComment());
        }

        @Transactional
        public PaymentResponseDTO payForRentalRequest(Long renterId, Long requestId,
                        jakarta.servlet.http.HttpServletRequest request) {
                RentalRequest rentalRequest = rentalRequestRepository.findById(requestId)
                                .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu thuê!"));

                if (!rentalRequest.getRenter().getId().equals(renterId)) {
                        throw new RuntimeException("Không có quyền thanh toán cho yêu cầu này!");
                }

                if (rentalRequest.getStatus() != com.ailogis.api.enums.RequestStatus.PENDING_PAYMENT) {
                        throw new RuntimeException("Yêu cầu thuê không ở trạng thái chờ thanh toán!");
                }

                Transaction transaction = Transaction.builder()
                                .buyer(rentalRequest.getRenter())
                                .rentalRequest(rentalRequest)
                                .amount(50000.0)
                                .type("RENTAL_FEE")
                                .status("PENDING")
                                .createdAt(LocalDateTime.now())
                                .invoiceDate(LocalDateTime.now())
                                .build();
                transaction = transactionRepository.save(transaction);

                String paymentUrl = paymentService.createPayOSPaymentLink(transaction);
                return new PaymentResponseDTO(paymentUrl);
        }
}