package com.ailogis.api.service;

import com.ailogis.api.dto.RenterStatisticResponseDTO;
import com.ailogis.api.dto.PaymentResponseDTO;
import com.ailogis.api.dto.AiTierDTO;
import com.ailogis.api.entity.User;
import com.ailogis.api.entity.Transaction;
import com.ailogis.api.entity.AiSubscriptionTier;
import com.ailogis.api.repository.UserRepository;
import com.ailogis.api.repository.ContractRepository;
import com.ailogis.api.repository.RentalRequestRepository;
import com.ailogis.api.repository.AiConversationRepository;
import com.ailogis.api.repository.TransactionRepository;
import com.ailogis.api.repository.AiSubscriptionTierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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

    public RenterStatisticResponseDTO getRenterStatistics(Long renterId, int expireDaysAlert) {
        User renter = userRepository.findById(renterId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Renter!"));

        Long totalWarehouse = contractRepository.countDistinctWarehousesByRenterId(renterId);
        Long totalAiConv = aiConversationRepository.countByUserId(renterId);

        Long tokenUsage = aiConversationRepository.sumTokenUsageByUserId(renterId);
        if (tokenUsage == null) tokenUsage = 0L;

        Double totalBilling = transactionRepository.sumTotalBillingByBuyerId(renterId);
        if (totalBilling == null) totalBilling = 0.0;

        String aiTierName = (renter.getAiTier() != null) ? renter.getAiTier().getLabel() : "Chưa đăng ký";

        Long totalReq = rentalRequestRepository.countByRenterId(renterId);
        Long totalOwnerUpdated = rentalRequestRepository.countOwnerUpdatedRequests(renterId);
        Long totalActiveContract = contractRepository.countByRenterIdAndStatus(renterId, com.ailogis.api.enums.ContractStatus.ACTIVE);

        LocalDate targetExpireDate = LocalDate.now().plusDays(expireDaysAlert);
        Long endOfContract = contractRepository.countExpiringContracts(renterId, targetExpireDate);

        return new RenterStatisticResponseDTO(
                totalWarehouse, totalAiConv, tokenUsage, totalBilling, aiTierName,
                totalReq, totalOwnerUpdated, totalActiveContract, endOfContract
        );
    }

    public List<AiTierDTO> getActiveAiTiers() {
        return aiTierRepository.findAll().stream()
                .map(tier -> new AiTierDTO(tier.getId(), tier.getLabel(), tier.getDescription(), tier.getTokenInput(), tier.getTokenOutput(), tier.getPrice(), tier.getUnit(), null))
                .toList();
    }

    @Transactional
    public PaymentResponseDTO buyAiSubscription(Long renterId, Long tierId, jakarta.servlet.http.HttpServletRequest request) {
        User renter = userRepository.findById(renterId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));
        AiSubscriptionTier aiTier = aiTierRepository.findById(tierId)
                .orElseThrow(() -> new RuntimeException("Gói AI không tồn tại!"));

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
        String paymentUrl = paymentService.createVNPayUrl(transaction, request);
        return new PaymentResponseDTO(paymentUrl);
    }
}