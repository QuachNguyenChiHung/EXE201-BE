package com.ailogis.api.service;

import com.ailogis.api.dto.TransactionResponseDTO;
import com.ailogis.api.entity.RentalRequest;
import com.ailogis.api.entity.Transaction;
import com.ailogis.api.entity.User;
import com.ailogis.api.entity.Warehouse;
import com.ailogis.api.enums.RequestStatus;
import com.ailogis.api.repository.RentalRequestRepository;
import com.ailogis.api.repository.TransactionRepository;
import com.ailogis.api.repository.UserRepository;
import com.ailogis.api.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.PayOS;
import vn.payos.exception.APIException;
import vn.payos.exception.WebhookException;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;
import vn.payos.model.webhooks.WebhookData;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final TransactionRepository transactionRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;
    private final RentalRequestRepository rentalRequestRepository;
    private final NotificationService notificationService;
    private final PayOS payOS;

    @Value("${payos.return-url}")
    private String payosReturnUrl;

    @Value("${payos.webhook-url}")
    private String payosWebhookUrl;

    // PayOS yeu cau orderCode phai duy nhat VINH VIEN trong pham vi tai khoan merchant - no
    // khong bao gio "quen" mot orderCode da tung thay. Id cua bang transactions thi se reset
    // ve 1 moi khi DB dev bi xoa/tao lai -> giao dich moi bi trung voi orderCode cu PayOS da
    // thay -> loi "transaction has already existed". Ma hoa orderCode tu THOI DIEM TAO (khong
    // bao gio lui lai duoc, ke ca khi DB reset) + id giup tranh dung lai orderCode cu vinh vien,
    // khong chi mot lan nhu cach cong offset co dinh.
    private static final long ORDER_CODE_ID_MODULUS = 100_000_000L; // du cho 99,999,999 giao dich

    // Chu so cao = thoi diem tao (epoch giay, khong bao gio lui), chu so thap = id. Hai "the he"
    // DB khac nhau (truoc/sau reset) luon roi vao thoi diem thuc te khac nhau nen orderCode
    // khong bao gio trung nhau, du id co lap lai (vd ca hai deu la id=1).
    private long encodeOrderCode(Transaction transaction) {
        long epochSeconds = transaction.getCreatedAt().toEpochSecond(java.time.ZoneOffset.UTC);
        return epochSeconds * ORDER_CODE_ID_MODULUS + (transaction.getId() % ORDER_CODE_ID_MODULUS);
    }

    // Giai ma khong can biet lai thoi diem tao - chi la phep chia lay du chinh xac, khong mo ho.
    private long decodeTransactionId(long orderCode) {
        return orderCode % ORDER_CODE_ID_MODULUS;
    }

    // Sinh link thanh toán PayOS (thay thế cho createVNPayUrl)
    public String createPayOSPaymentLink(Transaction transaction) {
        String description = buildPayOSDescription(transaction);
        // PayOS gioi han do dai description rat ngan (~25 ky tu), can cat bot neu vuot qua
        if (description.length() > 25) {
            description = description.substring(0, 25);
        }

        CreatePaymentLinkRequest req = CreatePaymentLinkRequest.builder()
                .orderCode(encodeOrderCode(transaction))
                // PayOS nhan so tien VND nguyen goc, KHONG nhan 100 (khac quy uoc cua VNPay)
                .amount(Math.round(transaction.getAmount()))
                .description(description)
                .cancelUrl(payosReturnUrl)
                .returnUrl(payosReturnUrl)
                .build();

        try {
            CreatePaymentLinkResponse resp = payOS.paymentRequests().create(req);
            transaction.setProviderTxnRef(resp.getPaymentLinkId());
            transactionRepository.save(transaction);
            return resp.getCheckoutUrl();
        } catch (APIException e) {
            throw new RuntimeException("Lỗi khi tạo link thanh toán PayOS: " + e.getErrorDesc().orElse(e.getMessage()));
        }
    }

    // Xây nội dung chuyển khoản hiển thị trên trang thanh toán PayOS - toi da 25 ky tu nen chi
    // ghi loai giao dich viet tat + khoang ngay. Ngay bat dau = ngay tao giao dich; ngay ket thuc
    // lay tu RentalRequest (da co san) voi RENTAL_FEE, con Sponsor/AI khong luu thoi han nao ca
    // nen tam tinh +1 thang chi de hien thi (khong ghi xuong DB).
    private String buildPayOSDescription(Transaction tx) {
        String prefix = "DH" + tx.getId();
        LocalDate start = tx.getCreatedAt().toLocalDate();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");

        String typeAbbr;
        LocalDate end;
        if ("RENTAL_FEE".equals(tx.getType()) && tx.getRentalRequest() != null
                && tx.getRentalRequest().getEndDate() != null) {
            typeAbbr = "MG";
            end = tx.getRentalRequest().getEndDate();
        } else if ("SPONSOR_SUBSCRIPTION".equals(tx.getType())) {
            typeAbbr = "SP";
            end = start.plusMonths(1);
        } else if ("AI_SUBSCRIPTION".equals(tx.getType())) {
            typeAbbr = "AI";
            end = start.plusMonths(1);
        } else {
            return prefix;
        }

        return prefix + " " + typeAbbr + " " + start.format(fmt) + "-" + end.format(fmt);
    }

    // Áp dụng kết quả thanh toán vào Transaction nội bộ (dùng chung cho cả webhook và return-url)
    // Lưu ý: không tự @Transactional ở đây (self-invocation không đi qua proxy Spring) -
    // các phương thức public gọi vào đây (handlePayOSWebhook, verifyAndApplyByOrderCode) đã có @Transactional riêng.
    private void applyPaymentResult(Long transactionId, String providerTxnRef, String providerTransactionNo,
            String providerPayDate, boolean paid) {
        if (transactionId == null) {
            return;
        }

        Transaction transaction = transactionRepository.findById(transactionId).orElse(null);

        if (transaction == null || !"PENDING".equals(transaction.getStatus())) {
            return;
        }

        if (paid) {
            transaction.setStatus("COMPLETED");
            if (providerTxnRef != null) {
                transaction.setProviderTxnRef(providerTxnRef);
            }
            if (providerTransactionNo != null) {
                transaction.setProviderTransactionNo(providerTransactionNo);
            }
            if (providerPayDate != null) {
                transaction.setProviderPayDate(providerPayDate);
            }

            // 1. Nếu là Giao dịch Mua gói SPONSOR của Chủ Kho
            if ("SPONSOR_SUBSCRIPTION".equals(transaction.getType())) {
                Warehouse warehouse = transaction.getWarehouse();
                warehouse.setIsSponsor(true);
                warehouse.setSponsorType(transaction.getSponsor());
                warehouseRepository.save(warehouse);
            }

            // 2. Nếu là Giao dịch Mua gói AI của Khách thuê (Tương lai)
            else if ("AI_SUBSCRIPTION".equals(transaction.getType())) {
                User renter = transaction.getBuyer();
                renter.setAiTier(transaction.getSubscription());
                userRepository.save(renter);
            }

            // 3. Nếu là Giao dịch Thanh toán phí thuê kho của Khách thuê
            else if ("RENTAL_FEE".equals(transaction.getType())) {
                RentalRequest req = transaction.getRentalRequest();
                req.setStatus(RequestStatus.PENDING);
                rentalRequestRepository.save(req);
            }

            transactionRepository.save(transaction);
        } else {
            // Nếu thất bại (User hủy thanh toán)
            transaction.setStatus("CANCELED");
            transactionRepository.save(transaction);
        }
    }

    // Xử lý webhook PayOS (server-to-server, nguồn xác thực chính)
    @Transactional
    public boolean handlePayOSWebhook(Map<String, Object> webhookBody) {
        WebhookData data;
        try {
            data = payOS.webhooks().verify(webhookBody);
        } catch (WebhookException e) {
            log.warn("Chữ ký webhook PayOS không hợp lệ: {}", e.getMessage());
            return false;
        }

        boolean paid = "00".equals(data.getCode());
        applyPaymentResult(decodeTransactionId(data.getOrderCode()), data.getPaymentLinkId(), data.getReference(),
                data.getTransactionDateTime(), paid);
        return true;
    }

    // Xử lý khi người dùng được redirect về từ trang thanh toán PayOS (return-url),
    // dùng để re-check trạng thái thật với PayOS trước khi hiển thị kết quả cho người dùng
    @Transactional
    public boolean verifyAndApplyByOrderCode(Long orderCode) {
        PaymentLink link;
        try {
            link = payOS.paymentRequests().get(orderCode);
        } catch (APIException e) {
            log.error("Lỗi khi kiểm tra trạng thái thanh toán PayOS cho orderCode {}: {}", orderCode,
                    e.getErrorDesc().orElse(e.getMessage()));
            return false;
        }

        PaymentLinkStatus status = link.getStatus();
        if (status == PaymentLinkStatus.PAID) {
            applyPaymentResult(decodeTransactionId(orderCode), link.getId(), null, null, true);
            return true;
        }

        // CANCELLED/EXPIRED/FAILED are genuinely terminal - safe to mark CANCELED.
        // PENDING/PROCESSING/UNDERPAID are NOT failures - the payment simply hasn't
        // concluded yet (e.g. bank transfer still settling). Marking the transaction
        // CANCELED here would be premature: if the real webhook later arrives with
        // paid=true, applyPaymentResult's idempotency guard (status must be PENDING)
        // would silently no-op against an already-CANCELED transaction, permanently
        // losing a payment that actually succeeded. So only apply a terminal failure;
        // otherwise leave the transaction untouched (still PENDING) for the webhook
        // to resolve authoritatively later.
        if (status == PaymentLinkStatus.CANCELLED || status == PaymentLinkStatus.EXPIRED
                || status == PaymentLinkStatus.FAILED) {
            applyPaymentResult(decodeTransactionId(orderCode), link.getId(), null, null, false);
        }
        return false;
    }

    // Người dùng bấm hủy/thoát ngay trên trang thanh toán PayOS (cancel=true ở return-url) -
    // không chờ tác vụ dọn dẹp 30 phút, hủy ngay cả link PayOS lẫn Transaction nội bộ.
    @Transactional
    public void cancelPayOSTransaction(long orderCode) {
        try {
            payOS.paymentRequests().cancel(orderCode);
        } catch (Exception e) {
            log.warn("Không thể hủy link thanh toán PayOS cho orderCode {}: {}", orderCode, e.getMessage());
        }
        applyPaymentResult(decodeTransactionId(orderCode), null, null, null, false);
    }

    // Đăng ký URL webhook với PayOS (thao tác 1 lần, chạy thủ công qua endpoint riêng, KHÔNG gọi lúc khởi động app)
    public String registerPayOSWebhook() {
        try {
            payOS.webhooks().confirm(payosWebhookUrl);
            return "Đăng ký webhook PayOS thành công";
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi đăng ký webhook PayOS: " + e.getMessage());
        }
    }

    // Mỗi 30 phút dọn dẹp các giao dịch PENDING đã bị bỏ rơi (quá hạn thanh toán)
    @Scheduled(cron = "0 0/30 * * * ?")
    @Transactional
    public void cleanupAbandonedTransactions() {
        System.out.println("Tác vụ Định kỳ: Đang dọn dẹp các giao dịch bị treo quá 30 phút...");

        List<Transaction> pendingTxs = transactionRepository.findByStatus("PENDING");

        // Lấy mốc thời gian là 30 phút trước
        java.time.LocalDateTime thirtyMinsAgo = java.time.LocalDateTime.now().minusMinutes(30);
        int count = 0;

        for (Transaction tx : pendingTxs) {
            // Nếu giao dịch được tạo trước mốc 30 phút -> Đã hết hạn -> Hủy
            if (tx.getCreatedAt().isBefore(thirtyMinsAgo)) {
                tx.setStatus("CANCELED");
                transactionRepository.save(tx);

                // Best-effort hủy link thanh toán bên PayOS, không để lỗi API chặn việc hủy ở DB nội bộ
                try {
                    payOS.paymentRequests().cancel(encodeOrderCode(tx));
                } catch (Exception e) {
                    log.warn("Không thể hủy link thanh toán PayOS cho giao dịch {}: {}", tx.getId(), e.getMessage());
                }

                count++;
            }
        }

        if (count > 0) {
            System.out.println("Tác vụ Định kỳ: Đã xử lý " + count + " giao dịch bị bỏ rơi!");
        }
    }

    // Hoàn tiền thủ công: đánh dấu giao dịch đã hoàn và báo cho Renter,
    // không gọi API PayOS ở bước này (theo quyết định sản phẩm, tiền được xử lý hoàn thủ công)
    @Transactional
    public void refundTransaction(Long rentalRequestId) {
        Transaction tx = transactionRepository.findByRentalRequestIdAndStatus(rentalRequestId, "COMPLETED");
        if (tx == null) {
            log.warn("Không tìm thấy giao dịch hợp lệ để hoàn tiền cho Request ID: {}", rentalRequestId);
            return;
        }

        tx.setStatus("REFUNDED");
        transactionRepository.save(tx);

        notificationService.saveAndNotify(tx.getBuyer().getId(),
                "Yêu cầu thuê của bạn đã bị từ chối. Khoản thanh toán sẽ được hoàn lại cho bạn trong thời gian sớm nhất.");

        log.info("Đã đánh dấu hoàn tiền cho Request ID: {}", rentalRequestId);
    }

    public List<TransactionResponseDTO> getTransactionHistory(Long userId) {
        return transactionRepository.findByBuyerIdOrderByIdDesc(userId)
                .stream()
                .map(this::mapToTransactionResponseDTO)
                .collect(Collectors.toList());
    }

    private TransactionResponseDTO mapToTransactionResponseDTO(Transaction tx) {
        String description = "Giao dịch hệ thống";

        if ("SPONSOR_SUBSCRIPTION".equals(tx.getType()) && tx.getSponsor() != null) {
            description = "Gói Sponsor: " + tx.getSponsor().getLabel();
        } else if ("AI_SUBSCRIPTION".equals(tx.getType()) && tx.getSubscription() != null) {
            description = "Gói AI: " + tx.getSubscription().getLabel();
        } else if ("RENTAL_FEE".equals(tx.getType()) && tx.getRentalRequest() != null) {
            description = "Phí liên hệ kho: " + tx.getRentalRequest().getWarehouse().getName();
        }

        return new TransactionResponseDTO(
                tx.getId(),
                tx.getAmount(),
                tx.getType(),
                tx.getStatus(),
                tx.getCreatedAt(),
                tx.getProviderTxnRef(),
                tx.getProviderTransactionNo(),
                tx.getProviderPayDate(),
                description
        );
    }
}
