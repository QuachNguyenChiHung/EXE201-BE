package com.ailogis.api.scheduler;

import com.ailogis.api.entity.RentalRequest;
import com.ailogis.api.enums.RequestStatus;
import com.ailogis.api.repository.RentalRequestRepository;
import com.ailogis.api.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RentalRequestScheduler {
    private final RentalRequestRepository rentalRequestRepository;
    private final PaymentService paymentService;

    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void autoRefundExpiredRequests() {
        log.info("Bắt đầu quét các Rental Request quá hạn 7 ngày...");

        LocalDate cutoffDate = LocalDate.now().minusDays(7);

        List<RentalRequest> expiredRequests = rentalRequestRepository.findByStatusAndUpdatedAtBefore(RequestStatus.PENDING, cutoffDate);

        for (RentalRequest request : expiredRequests) {
            request.setStatus(RequestStatus.REJECTED);
            request.setRejectionReason("Hệ thống tự động hủy và hoàn tiền do Owner không phản hồi sau 7 ngày.");
            rentalRequestRepository.save(request);
            paymentService.refundTransaction(request.getId());

            log.info("Đã hủy và hoàn tiền tự động cho Rental Request ID: {}", request.getId());
        }

        log.info("Hoàn tất quét. Đã xử lý {} yêu cầu.", expiredRequests.size());
    }
}