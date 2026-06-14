package com.ailogis.api.service;

import com.ailogis.api.config.VNPayConfig;
import com.ailogis.api.entity.Transaction;
import com.ailogis.api.repository.TransactionRepository;
import com.ailogis.api.repository.UserRepository;
import com.ailogis.api.repository.WarehouseRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final TransactionRepository transactionRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;

    @Value("${vnpay.tmnCode}") private String vnp_TmnCode;
    @Value("${vnpay.hashSecret}") private String vnp_HashSecret;
    @Value("${vnpay.payUrl}") private String vnp_PayUrl;
    @Value("${vnpay.returnUrl}") private String vnp_ReturnUrl;

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

                // === LOGIC DÙNG CHUNG PHÂN THEO LOẠI GIAO DỊCH ===

                // 1. Nếu là Giao dịch Mua gói SPONSOR của Chủ Kho
                if ("SPONSOR_SUBSCRIPTION".equals(transaction.getType())) {
                    com.ailogis.api.entity.Warehouse warehouse = transaction.getWarehouse();
                    warehouse.setIsSponsor(true);
                    warehouse.setSponsorType(transaction.getSponsor());
                    warehouseRepository.save(warehouse);
                }

                // 2. Nếu là Giao dịch Mua gói AI của Khách thuê (Tương lai)
                else if ("AI_SUBSCRIPTION".equals(transaction.getType())) {
                    com.ailogis.api.entity.User renter = transaction.getBuyer();
                    renter.setAiTier(transaction.getSubscription());
                    userRepository.save(renter);
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
}