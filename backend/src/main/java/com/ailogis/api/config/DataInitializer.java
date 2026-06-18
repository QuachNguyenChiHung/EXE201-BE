package com.ailogis.api.config;

import com.ailogis.api.dto.*;
import com.ailogis.api.entity.*;
import com.ailogis.api.enums.RequestStatus;
import com.ailogis.api.enums.VerifyStatus;
import com.ailogis.api.enums.WarehouseStatus;
import com.ailogis.api.repository.*;
import com.ailogis.api.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final WarehouseRepository warehouseRepository;
    private final CertificationTypeRepository certificationTypeRepository;
    private final UserSessionRepository sessionRepository;
    private final ContractRepository contractRepository;
    private final AiSubscriptionTierRepository aiTierRepository;
    private final SponsorTierRepository sponsorTierRepository;
    private final ReviewRepository reviewRepository;

    private final AuthService authService;
    private final EmployeeService employeeService;
    private final OwnerService ownerService;
    private final RentalRequestService rentalRequestService;
    private final ContractService contractService;

    private final String HACCP_link = "https://ailogis-storage-bucket-492017761328-ap-southeast-1-an.s3.ap-southeast-1.amazonaws.com/certs/HACCP.pdf";
    private final String ISO9001_link = "https://ailogis-storage-bucket-492017761328-ap-southeast-1-an.s3.ap-southeast-1.amazonaws.com/certs/ISO+9001_2015.pdf";

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (userRepository.count() > 0) {
            log.info("✅ Database đã có dữ liệu, bỏ qua Init.");
            return;
        }

        log.info("⏳ Đang khởi tạo dữ liệu mẫu thông qua TẦNG SERVICE...");

        // =================================================================
        // 1. KHỞI TẠO USERS & COMPANIES
        // =================================================================

        employeeService.createEmployee(new UserCreateUpdateDTO("Trần Minh Admin", "employee@ailogis.com", "password123", "0911223344", "EMPLOYEE", "ACTIVE", "https://ailogis-storage-bucket.s3.ap-southeast-1.amazonaws.com/avatars/admin.png", null, null, null));
        User employee = userRepository.findByEmail("employee@ailogis.com").get();

        authService.registerUser(new RegisterRequestDTO("owner@ailogis.com", "password123", "Nguyễn Văn Sóng Thần", "0909123456", "OWNER", "Tập đoàn Kho vận Sóng Thần", "0109876543"));
        User owner1 = userRepository.findByEmail("owner@ailogis.com").get();

        authService.registerUser(new RegisterRequestDTO("owner2@ailogis.com", "password123", "Lê Trọng Tân Bình", "0909999888", "OWNER", "Công ty Cổ phần Logistics Tân Bình", "0312345678"));
        User owner2 = userRepository.findByEmail("owner2@ailogis.com").get();

        authService.registerUser(new RegisterRequestDTO("owner3@ailogis.com", "password123", "Trần Thị Dược", "0909777666", "OWNER", "Kho lạnh Dược phẩm Miền Nam", "0319998887"));
        User owner3 = userRepository.findByEmail("owner3@ailogis.com").get();

        authService.registerUser(new RegisterRequestDTO("renter@ailogis.com", "password123", "Phạm Thị Thu mua", "0988776655", "RENTER", "CXNK Thủy sản Mê Kông", "0203456789"));
        User renter1 = userRepository.findByEmail("renter@ailogis.com").get();

        authService.registerUser(new RegisterRequestDTO("renter2@ailogis.com", "password123", "Hoàng Văn Vận", "0988111222", "RENTER", "Tập đoàn Thực phẩm Massan", "0300123456"));
        User renter2 = userRepository.findByEmail("renter2@ailogis.com").get();

        authService.registerUser(new RegisterRequestDTO("renter3@ailogis.com", "password123", "Đinh Cung Ứng", "0988333444", "RENTER", "Chuỗi siêu thị VinMart+", "0100987654"));
        User renter3 = userRepository.findByEmail("renter3@ailogis.com").get();

        List<User> allUsers = List.of(employee, owner1, owner2, owner3, renter1, renter2, renter3);

        // =================================================================
        // 2. KHỞI TẠO CHỨNG CHỈ
        // =================================================================
        CertificationType haccp = certificationTypeRepository.save(CertificationType.builder().label("HACCP - Hệ thống quản lý an toàn thực phẩm").lawReferences("TCVN 5603:2020").updateDate(LocalDate.now()).pdfLink("https://ailogis-storage-bucket-492017761328-ap-southeast-1-an.s3.ap-southeast-1.amazonaws.com/certs/HACCP.pdf").build());
        CertificationType iso9001 = certificationTypeRepository.save(CertificationType.builder().label("ISO 9001:2015 - Quản lý chất lượng").lawReferences("ISO/TC 176").updateDate(LocalDate.now()).pdfLink("https://ailogis-storage-bucket-492017761328-ap-southeast-1-an.s3.ap-southeast-1.amazonaws.com/certs/ISO+9001_2015.pdf").build());

        // =================================================================
        // 3. KHỞI TẠO WAREHOUSES
        // =================================================================

        // KHO 1: Kho Sóng Thần (Tọa độ giả lập Dĩ An, Bình Dương)
        WarehouseCreateDTO wh1Dto = new WarehouseCreateDTO(
                "Tổng kho Lạnh Quốc tế Sóng Thần", "Hệ thống kho vận đạt tiêu chuẩn ISO ứng dụng công nghệ giám sát nhiệt độ tự động.", "Số 10, KCN Sóng Thần 1", "Bình Dương", "Dĩ An",
                106.7725, 10.9024, "75000",
                List.of(new WarehouseSectionDTO(null, 1, 1500.0, 1500.0, -25.0, -18.0, 60.0, true, List.of(new PriceTierDTO("Gói lưu trữ theo tháng", 260000.0, "VND", "m3")))));
        List<WarehouseCertCreateDTO> certs1 = List.of(new WarehouseCertCreateDTO(iso9001.getId(), HACCP_link));
        WarehouseResponseDTO wh1Res = ownerService.createWarehouse(owner1.getId(), wh1Dto, List.of("https://ailogis-storage-bucket-492017761328-ap-southeast-1-an.s3.ap-southeast-1.amazonaws.com/images/07c00336-f2b5-4528-84c1-d082a9805f19.jpg"), certs1);
        employeeService.verifyWarehouse(wh1Res.id(), WarehouseStatus.ACTIVE);

        // KHO 2: Kho Tân Bình (Tọa độ giả lập KCN Tân Bình, TP.HCM)
        WarehouseCreateDTO wh2Dto = new WarehouseCreateDTO(
                "Kho mát Nông sản Tân Bình", "Chuyên lưu trữ rau củ quả tươi sống, vị trí ngay sát trung tâm TPHCM, thuận tiện giao hàng nội thành.", "KCN Tân Bình, Lô B2", "Hồ Chí Minh", "Tân Bình",
                106.6358, 10.8038, "70000",
                List.of(new WarehouseSectionDTO(null, 1, 800.0, 800.0, 2.0, 8.0, 85.0, false, List.of(new PriceTierDTO("Thuê bao nguyên khu (Tuần)", 5000000.0, "VND", "sector")))));
        List<WarehouseCertCreateDTO> certs2 = List.of(new WarehouseCertCreateDTO(haccp.getId(), ISO9001_link));
        WarehouseResponseDTO wh2Res = ownerService.createWarehouse(owner2.getId(), wh2Dto, new ArrayList<>(), certs2);
        employeeService.verifyWarehouse(wh2Res.id(), WarehouseStatus.ACTIVE);

        // KHO 3: Kho Quận 9 (Tọa độ giả lập Khu Công Nghệ Cao Quận 9, TP.HCM)
        WarehouseCreateDTO wh3Dto = new WarehouseCreateDTO(
                "Kho lạnh Y tế & Dược phẩm Quận 9", "Kho chuyên dụng chuẩn GSP lưu trữ Vắc xin và Sinh phẩm y tế.", "Khu Công Nghệ Cao, Đường D1", "Hồ Chí Minh", "Quận 9",
                106.8029, 10.8491, "70000",
                List.of(new WarehouseSectionDTO(null, 1, 300.0, 300.0, -80.0, -20.0, 40.0, true, List.of(new PriceTierDTO("Lưu trữ theo Pallet/Tháng", 800000.0, "VND", "pallet")))));
        List<WarehouseCertCreateDTO> certs3 = List.of(
                new WarehouseCertCreateDTO(haccp.getId(), HACCP_link),
                new WarehouseCertCreateDTO(iso9001.getId(), ISO9001_link)
        );
        WarehouseResponseDTO wh3Res = ownerService.createWarehouse(owner3.getId(), wh3Dto, new ArrayList<>(), certs3);
        employeeService.verifyWarehouse(wh3Res.id(), WarehouseStatus.ACTIVE);

        // Employee duyệt Certification
        Warehouse wh1Entity = warehouseRepository.findById(wh1Res.id()).get();
        wh1Entity.getCertificationSubmits().forEach(c -> {
            employeeService.reviewWarehouseCertification(
                    c.getId(),
                    new CertReviewDTO("VERIFIED", null, c.getType().getId())
            );
        });

        Warehouse wh2Entity = warehouseRepository.findById(wh2Res.id()).get();
        wh2Entity.getCertificationSubmits().forEach(c -> {
            employeeService.reviewWarehouseCertification(
                    c.getId(),
                    new CertReviewDTO("VERIFIED", null, c.getType().getId())
            );
        });

        Warehouse wh3Entity = warehouseRepository.findById(wh3Res.id()).get();
        wh3Entity.getCertificationSubmits().forEach(c -> {
            if (c.getType().getId().equals(haccp.getId())) {
                employeeService.reviewWarehouseCertification(
                        c.getId(),
                        new CertReviewDTO("REJECTED", "Đã hết hạn", null)
                );
            }
        });

        // =================================================================
        // 4. KHỞI TẠO REQUEST & CONTRACTS
        // =================================================================

        // HỢP ĐỒNG 1: Renter 1 thuê Kho 1 (Tổng giá gốc: 500m3 * 260k = 130tr/tháng), Renter trả giá còn 125tr
        Long sec1Id = wh1Entity.getSections().get(0).getId();
        Long pt1Id = wh1Entity.getSections().get(0).getPriceTiers().get(0).getId();
        RentRequestCreateDTO req1Dto = new RentRequestCreateDTO(wh1Entity.getId(), "Hải sản cá ngừ đại dương xuất khẩu", null, 6, "Tháng",
                125000000.0,
                List.of(new RentRequestDetailCreateDTO(sec1Id, pt1Id, 500.0, "m3"))); // Thuê 500 m3

        RentRequestResponseDTO req1Res = rentalRequestService.createRequest(renter1.getId(), req1Dto);
        ownerService.updateRequestStatus(owner1.getId(), req1Res.id(), new RequestStatusUpdateDTO(RequestStatus.APPROVED, null, null, "Đồng ý cho thuê giá gốc"));

        ContractResponseDTO c1Res = contractService.createContract(owner1.getId(), new ContractCreateDTO(
                req1Res.id(),
                (long) (500.0 * 260000.0 * 6),
                null, null, null, null, null, null, // Các trường startAt, endAt, điều khoản
                null, null, null, null, null,       // Thông tin Owner
                null, null, null, null, null,       // Thông tin Renter
                "PENDING"                           // Status
        ));

        // HỢP ĐỒNG 2: Renter 2 thuê Kho 2 (Tổng giá gốc: 1 sector * 5tr = 5tr/tuần), Renter trả giá còn 4.8tr
        Long sec2Id = wh2Entity.getSections().get(0).getId();
        Long pt2Id = wh2Entity.getSections().get(0).getPriceTiers().get(0).getId();
        RentRequestCreateDTO req2Dto = new RentRequestCreateDTO(wh2Entity.getId(), "Rau củ Đà Lạt nhập kho chờ phân phối", null, 2, "Tuần",
                4800000.0,
                List.of(new RentRequestDetailCreateDTO(sec2Id, pt2Id, 1.0, "sector"))); // Thuê 1 sector

        RentRequestResponseDTO req2Res = rentalRequestService.createRequest(renter2.getId(), req2Dto);
        ownerService.updateRequestStatus(owner2.getId(), req2Res.id(), new RequestStatusUpdateDTO(RequestStatus.APPROVED, null, null, "Kho lạnh Tân Bình xác nhận yêu cầu."));

        // Renter 3 gửi yêu cầu thuê Kho 1 nhưng Owner chưa duyệt (Tổng giá gốc: 200m3 * 260k = 52tr/tháng), Renter trả giá 50tr
        RentRequestCreateDTO req3Dto = new RentRequestCreateDTO(wh1Entity.getId(), "Thịt bò Kobe nhập khẩu đông lạnh", null, 3, "Tháng",
                50000000.0,
                List.of(new RentRequestDetailCreateDTO(sec1Id, pt1Id, 200.0, "m3")));
        rentalRequestService.createRequest(renter3.getId(), req3Dto);

        ContractResponseDTO c2Res = contractService.createContract(owner2.getId(), new ContractCreateDTO(
                req2Res.id(),
                (long) 5000000.0 * 2,
                null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null,
                "PENDING"
        ));

        // Fake lùi ngày ký hợp đồng vào quá khứ để test biểu đồ/thống kê
        Contract contract1 = contractRepository.findById(c1Res.id()).get();
        contract1.setStartAt(LocalDate.now().minusDays(15));
        contract1.setStatus(com.ailogis.api.enums.ContractStatus.ACTIVE);
        contract1.setOwnerSigned(true);
        contract1.setRenterSigned(true);
        contractRepository.save(contract1);

        Contract contract2 = contractRepository.findById(c2Res.id()).get();
        contract2.setStartAt(LocalDate.now().minusDays(5));
        contract2.setStatus(com.ailogis.api.enums.ContractStatus.ACTIVE);
        contract2.setOwnerSigned(true);
        contract2.setRenterSigned(true);
        contractRepository.save(contract2);

        // =================================================================
        // 5. KHỞI TẠO SESSIONS
        // =================================================================
        List<UserSession> sessions = new ArrayList<>();
        LocalDate today = LocalDate.now();
        Random random = new Random();

        for (User user : allUsers) {
            int loginFrequency = random.nextInt(15) + 5; // 5 - 20 lượt mỗi người
            for (int i = 0; i < loginFrequency; i++) {
                LocalDate loginDate = today.minusDays(random.nextInt(30));
                sessions.add(UserSession.builder()
                        .user(user)
                        .loginDate(loginDate)
                        .loginAt(LocalDateTime.of(loginDate.getYear(), loginDate.getMonth(), loginDate.getDayOfMonth(), random.nextInt(24), random.nextInt(60)))
                        .build());
            }
        }
        sessionRepository.saveAll(sessions);

        // =================================================================
        // 6. KHỞI TẠO CÁC GÓI AI & SPONSOR
        // =================================================================

        // 1. Tạo gói AI
        AiTierDTO aiBasicDto = employeeService.createAiTier(new AiTierDTO(
                null, "Gói AI Basic", "Hỗ trợ tra cứu nhanh", 50000, 20000, 199000.0, "VND", null));
        AiTierDTO aiProDto = employeeService.createAiTier(new AiTierDTO(
                null, "Gói AI Pro", "Tối ưu hóa RAG chuyên sâu", 200000, 100000, 499000.0, "VND", null));

        // 2. Tạo gói Tài trợ (Sponsor)
        SponsorTierDTO sponsorGoldDto = employeeService.createSponsorTier(new SponsorTierDTO(
                null, 1, 1000000.0, 10000000.0, "Tài trợ Vàng (Top 1)", null,  true));
        SponsorTierDTO sponsorSilverDto = employeeService.createSponsorTier(new SponsorTierDTO(
                null, 2, 500000.0, 5000000.0, "Tài trợ Bạc (Top 2)", null, true));

        // GÁN GÓI CHO NGƯỜI DÙNG VÀ KHO BÃI
        AiSubscriptionTier aiProEntity = aiTierRepository.findById(aiProDto.id()).get();
        AiSubscriptionTier aiBasicEntity = aiTierRepository.findById(aiBasicDto.id()).get();
        SponsorTier sponsorGoldEntity = sponsorTierRepository.findById(sponsorGoldDto.id()).get();
        SponsorTier sponsorSilverEntity = sponsorTierRepository.findById(sponsorSilverDto.id()).get();

        // Renter 1 (Khách Thuê 1) mua gói AI Pro
        renter1.setAiTier(aiProEntity);
        userRepository.save(renter1);

        // Renter 2 & 3 mua gói AI Basic
        renter2.setAiTier(aiBasicEntity);
        userRepository.save(renter2);

        renter3.setAiTier(aiBasicEntity);
        userRepository.save(renter3);

        // Kho số 1 & 3 mua gói Tài trợ Vàng
        wh1Entity.setIsSponsor(true);
        wh1Entity.setSponsorType(sponsorGoldEntity);
        warehouseRepository.save(wh1Entity);

        wh3Entity.setIsSponsor(true);
        wh3Entity.setSponsorType(sponsorGoldEntity);
        warehouseRepository.save(wh3Entity);

        // Kho số 2 mua gói Tài trợ Bạc
        wh2Entity.setIsSponsor(true);
        wh2Entity.setSponsorType(sponsorSilverEntity);
        warehouseRepository.save(wh2Entity);

        // =================================================================
        // 7. KHỞI TẠO ĐÁNH GIÁ (RATINGS & REVIEWS)
        // =================================================================
        List<Review> reviews = List.of(
                // Kho 1: Hạ điểm xuống (Avg: 3.5 sao)
                Review.builder().user(renter1).warehouse(wh1Entity).rating(4).comment("Kho tạm ổn, nhưng đường vào hơi nhỏ, bãi đậu xe hay bị kẹt.").build(),
                Review.builder().user(renter2).warehouse(wh1Entity).rating(3).comment("Dịch vụ bình thường, thỉnh thoảng nhiệt độ kho báo cáo hơi chậm.").build(),

                // Kho 2: Avg: 5.0 sao
                Review.builder().user(renter3).warehouse(wh2Entity).rating(5).comment("Vị trí ngay sát trung tâm, xe tải ra vào rất thuận tiện. Tuyệt vời!").build(),

                // Kho 3: Tăng số lượng đánh giá và điểm lên tối đa để test thuật toán (Avg: 5.0 sao)
                Review.builder().user(renter1).warehouse(wh3Entity).rating(5).comment("Kho y tế chuẩn GSP, quy trình kiểm soát vi sinh rất khắt khe và an toàn tuyệt đối.").build(),
                Review.builder().user(renter2).warehouse(wh3Entity).rating(5).comment("Rất hài lòng với cách quản lý chuyên nghiệp, thủ tục giấy tờ cực kỳ nhanh gọn.").build()
        );
        reviewRepository.saveAll(reviews);

        log.info("✅ Init Data Hoàn tất! Tất cả kho bãi, hợp đồng đều ở trạng thái ACTIVE/APPROVED sẵn sàng test.");
    }
}