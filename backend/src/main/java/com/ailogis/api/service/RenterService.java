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

        public RenterStatisticResponseDTO getRenterStatistics(Long renterId, int expireDaysAlert) {
                User renter = userRepository.findById(renterId)
                                .orElseThrow(() -> new RuntimeException("Không tìm thấy Renter!"));

                Long totalWarehouse = contractRepository.countDistinctWarehousesByRenterId(renterId);
                Long totalAiConv = aiConversationRepository.countByUserId(renterId);

                Long tokenUsage = aiConversationRepository.sumTokenUsageByUserId(renterId);
                if (tokenUsage == null)
                        tokenUsage = 0L;

                Double totalBilling = transactionRepository.sumTotalBillingByBuyerId(renterId);
                if (totalBilling == null)
                        totalBilling = 0.0;

                String aiTierName = (renter.getAiTier() != null) ? renter.getAiTier().getLabel() : "Chưa đăng ký";

                Long totalReq = rentalRequestRepository.countByRenterId(renterId);
                Long totalOwnerUpdated = rentalRequestRepository.countOwnerUpdatedRequests(renterId);
                Long totalActiveContract = contractRepository.countByRenterIdAndStatus(renterId,
                                com.ailogis.api.enums.ContractStatus.ACTIVE);

                LocalDate targetExpireDate = LocalDate.now().plusDays(expireDaysAlert);
                Long endOfContract = contractRepository.countExpiringContracts(renterId, targetExpireDate);

                return new RenterStatisticResponseDTO(
                                totalWarehouse, totalAiConv, tokenUsage, totalBilling, aiTierName,
                                totalReq, totalOwnerUpdated, totalActiveContract, endOfContract);
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
                        jakarta.servlet.http.HttpServletRequest request) {
                User renter = userRepository.findById(renterId)
                                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));
                AiSubscriptionTier aiTier = aiTierRepository.findById(tierId)
                                .orElseThrow(() -> new RuntimeException("Gói AI không tồn tại!"));

                if (aiTier.getPrice() == 0) {
                        renter.setAiTier(aiTier);
                        userRepository.save(renter);
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
                                .amount(100000.0)
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