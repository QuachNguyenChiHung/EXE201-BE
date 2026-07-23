package com.ailogis.api.service;

import com.ailogis.api.config.VNPayConfig;
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
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import lombok.extern.slf4j.Slf4j;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final TransactionRepository transactionRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;
    private final RentalRequestRepository rentalRequestRepository;
    private final RestTemplate restTemplate;

    @Value("${vnpay.tmnCode}") private String vnp_TmnCode;
    @Value("${vnpay.hashSecret}") private String vnp_HashSecret;
    @Value("${vnpay.payUrl}") private String vnp_PayUrl;
    @Value("${vnpay.returnUrl}") private String vnp_ReturnUrl;
    @Value("${vnpay.apiUrl}") private String vnp_ApiUrl;

    public String createVNPayUrl(Transaction transaction, HttpServletRequest request) {
        String vnp_Version = "2.1.0";
        String vnp_Command = "pay";
        String orderType = "other";

        // VNPay yêu cầu số tiền nhân 100
        long amount = (long) (transaction.getAmount() * 100);

        // Lấy ID giao dịch nội bộ làm mã đơn hàng của VNPay
        String vnp_TxnRef = transaction.getId() + "_" + System.currentTimeMillis();

        // Cấu hình tham số gửi sang VNPay
        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", vnp_Version);
        vnp_Params.put("vnp_Command", vnp_Command);
        vnp_Params.put("vnp_TmnCode", vnp_TmnCode);
        vnp_Params.put("vnp_Amount", String.valueOf(amount));
        vnp_Params.put("vnp_CurrCode", "VND");
        vnp_Params.put("vnp_TxnRef", vnp_TxnRef);
        vnp_Params.put("vnp_OrderInfo", "Thanh toan don hang " + vnp_TxnRef);
        vnp_Params.put("vnp_OrderType", orderType);
        vnp_Params.put("vnp_Locale", "vn");
        vnp_Params.put("vnp_ReturnUrl", vnp_ReturnUrl);
        vnp_Params.put("vnp_IpAddr", request.getRemoteAddr());

        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        formatter.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        vnp_Params.put("vnp_CreateDate", formatter.format(cld.getTime()));

        cld.add(Calendar.MINUTE, 15);
        vnp_Params.put("vnp_ExpireDate", formatter.format(cld.getTime()));

        // Build String băm dữ liệu
        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();

        try {
            for (String fieldName : fieldNames) {
                String fieldValue = vnp_Params.get(fieldName);
                if (fieldValue != null && fieldValue.length() > 0) {
                    hashData.append(fieldName).append('=').append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                    query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII.toString())).append('=')
                            .append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                    if (fieldNames.indexOf(fieldName) != fieldNames.size() - 1) {
                        query.append('&');
                        hashData.append('&');
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Lỗi cấu hình URL VNPay");
        }

        String queryUrl = query.toString();
        String vnp_SecureHash = VNPayConfig.hmacSHA512(vnp_HashSecret, hashData.toString());
        queryUrl += "&vnp_SecureHash=" + vnp_SecureHash;

        return vnp_PayUrl + "?" + queryUrl;
    }

    // XỬ LÝ LOGIC CHUNG KHI VNPAY GỌI LẠI (Dùng chung cho cả Owner và Renter)
    @Transactional
    public boolean processVNPayCallback(Map<String, String> params) {
        String secureHash = params.get("vnp_SecureHash");
        params.remove("vnp_SecureHash");
        params.remove("vnp_SecureHashType");

        List<String> fieldNames = new ArrayList<>(params.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();

        try {
            for (String fieldName : fieldNames) {
                String fieldValue = params.get(fieldName);
                if ((fieldValue != null) && (fieldValue.length() > 0)) {
                    hashData.append(fieldName).append('=')
                            .append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                    if (fieldNames.indexOf(fieldName) != fieldNames.size() - 1) {
                        hashData.append('&');
                    }
                }
            }
        } catch (Exception e) { return false; }

        String checkSum = VNPayConfig.hmacSHA512(vnp_HashSecret, hashData.toString());

        // Kiểm tra chữ ký và mã thành công (00 = Thành công)
        if (checkSum.equals(secureHash) && "00".equals(params.get("vnp_ResponseCode"))) {
            String txnRef = params.get("vnp_TxnRef");
            Long transactionId = Long.parseLong(txnRef.split("_")[0]);

            Transaction transaction = transactionRepository.findById(transactionId).orElse(null);

            if (transaction != null && "PENDING".equals(transaction.getStatus())) {
                transaction.setStatus("COMPLETED");
                transaction.setVnpTxnRef(txnRef);
                transaction.setVnpTransactionNo(params.get("vnp_TransactionNo"));
                transaction.setVnpPayDate(params.get("vnp_PayDate"));

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
                return true;
            }
        }

        // Nếu thất bại (User hủy thanh toán)
        try {
            String txnRef = params.get("vnp_TxnRef");
            if (txnRef != null) {
                // Tách lấy ID thật (bỏ phần đuôi _timestamp)
                Long txId = Long.parseLong(txnRef.split("_")[0]);
                Transaction txFailed = transactionRepository.findById(txId).orElse(null);

                if (txFailed != null && "PENDING".equals(txFailed.getStatus())) {
                    txFailed.setStatus("CANCELED"); // Chuyển sang Hủy bỏ
                    transactionRepository.save(txFailed);
                }
            }
        } catch (Exception e) {
            System.out.println("Lỗi khi cập nhật trạng thái hủy giao dịch: " + e.getMessage());
        }

        return false;
    }

    // Mỗi 30 phút dọn dẹp các giao dịch PENDING đã bị bỏ rơi (quá hạn thanh toán VNPay)
    @Scheduled(cron = "0 0/30 * * * ?")
    @Transactional
    public void cleanupAbandonedTransactions() {
        System.out.println("Tác vụ Định kỳ: Đang dọn dẹp các giao dịch bị treo quá 30 phút...");

        List<Transaction> pendingTxs = transactionRepository.findByStatus("PENDING");

        // Lấy mốc thời gian là 30 phút trước
        java.time.LocalDateTime thirtyMinsAgo = java.time.LocalDateTime.now().minusMinutes(30);
        int count = 0;

        for (Transaction tx : pendingTxs) {
            // Nếu giao dịch được tạo trước mốc 30 phút -> Đã hết hạn VNPay -> Hủy
            if (tx.getCreatedAt().isBefore(thirtyMinsAgo)) {
                tx.setStatus("CANCELED");
                transactionRepository.save(tx);
                count++;
            }
        }

        if (count > 0) {
            System.out.println("Tác vụ Định kỳ: Đã xử lý " + count + " giao dịch bị bỏ rơi!");
        }
    }

    @Transactional
    public void refundTransaction(Long rentalRequestId) {
        Transaction tx = transactionRepository.findByRentalRequestIdAndStatus(rentalRequestId, "COMPLETED");
        if (tx == null || tx.getVnpTxnRef() == null || tx.getVnpTransactionNo() == null) {
            log.warn("Không tìm thấy giao dịch hợp lệ để hoàn tiền cho Request ID: {}", rentalRequestId);
            return;
        }

        String vnp_RequestId = UUID.randomUUID().toString();
        String vnp_Version = "2.1.0";
        String vnp_Command = "refund";
        String vnp_TransactionType = "02"; // 02: Hoàn trả toàn phần (Full Refund)
        long amount = (long) (tx.getAmount() * 100);
        String vnp_TxnRef = tx.getVnpTxnRef();
        String vnp_OrderInfo = "Hoan tien phi mo khoa lien he cho Request ID " + rentalRequestId;
        String vnp_TransactionNo = tx.getVnpTransactionNo();
        String vnp_TransactionDate = tx.getVnpPayDate();
        String vnp_CreateBy = "System_Ailogis";

        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        String vnp_CreateDate = formatter.format(cld.getTime());
        String vnp_IpAddr = "127.0.0.1"; // IP của server Ailogis

        String hashData = vnp_RequestId + "|" + vnp_Version + "|" + vnp_Command + "|" + vnp_TmnCode + "|" +
                vnp_TransactionType + "|" + vnp_TxnRef + "|" + amount + "|" + vnp_TransactionNo + "|" +
                vnp_TransactionDate + "|" + vnp_CreateBy + "|" + vnp_CreateDate + "|" + vnp_IpAddr + "|" + vnp_OrderInfo;

        String vnp_SecureHash = VNPayConfig.hmacSHA512(vnp_HashSecret, hashData);

        Map<String, Object> requestParams = new HashMap<>();
        requestParams.put("vnp_RequestId", vnp_RequestId);
        requestParams.put("vnp_Version", vnp_Version);
        requestParams.put("vnp_Command", vnp_Command);
        requestParams.put("vnp_TmnCode", vnp_TmnCode);
        requestParams.put("vnp_TransactionType", vnp_TransactionType);
        requestParams.put("vnp_TxnRef", vnp_TxnRef);
        requestParams.put("vnp_Amount", amount);
        requestParams.put("vnp_OrderInfo", vnp_OrderInfo);
        requestParams.put("vnp_TransactionNo", vnp_TransactionNo);
        requestParams.put("vnp_TransactionDate", vnp_TransactionDate);
        requestParams.put("vnp_CreateBy", vnp_CreateBy);
        requestParams.put("vnp_CreateDate", vnp_CreateDate);
        requestParams.put("vnp_IpAddr", vnp_IpAddr);
        requestParams.put("vnp_SecureHash", vnp_SecureHash);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestParams, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(vnp_ApiUrl, requestEntity, Map.class);
            Map<String, Object> responseBody = response.getBody();

            if (responseBody != null && "00".equals(responseBody.get("vnp_ResponseCode"))) {
                tx.setStatus("REFUNDED");
                transactionRepository.save(tx);
                log.info("Gọi API VNPay hoàn tiền thành công cho Request ID: {}", rentalRequestId);
            } else {
                log.error("VNPay từ chối hoàn tiền: {}", responseBody);
            }
        } catch (Exception e) {
            log.error("Ngoại lệ khi gọi API hoàn tiền VNPay: ", e);
        }
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
                tx.getVnpTxnRef(),
                tx.getVnpTransactionNo(),
                tx.getVnpPayDate(),
                description
        );
    }
}