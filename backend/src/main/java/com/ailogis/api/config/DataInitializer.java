package com.ailogis.api.config;

import com.ailogis.api.entity.*;
import com.ailogis.api.enums.ContractStatus;
import com.ailogis.api.enums.RequestStatus;
import com.ailogis.api.enums.Role;
import com.ailogis.api.enums.UserStatus;
import com.ailogis.api.enums.WarehouseStatus;
import com.ailogis.api.repository.*;
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

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final WarehouseRepository warehouseRepository;
    private final CertificationTypeRepository certificationTypeRepository;
    private final RentalRequestRepository requestRepository;
    private final ContractRepository contractRepository;
    private final UserSessionRepository sessionRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (userRepository.count() > 0) {
            log.info("✅ Database đã có dữ liệu, bỏ qua Init.");
            return;
        }

        log.info("⏳ Đang khởi tạo dữ liệu mẫu (Hardcode Data Thật) với trạng thái ACTIVE...");

        // =================================================================
        // 1. KHỞI TẠO CÔNG TY (COMPANIES) - DỮ LIỆU THẬT
        // =================================================================
        Company ownerComp1 = companyRepository.save(Company.builder().companyName("Tập đoàn Kho vận Sóng Thần").companyTaxCode("0109876543").build());
        Company ownerComp2 = companyRepository.save(Company.builder().companyName("Công ty Cổ phần Logistics Tân Bình").companyTaxCode("0312345678").build());
        Company ownerComp3 = companyRepository.save(Company.builder().companyName("Kho lạnh Dược phẩm Miền Nam").companyTaxCode("0319998887").build());

        Company renterComp1 = companyRepository.save(Company.builder().companyName("CXNK Thủy sản Mê Kông").companyTaxCode("0203456789").build());
        Company renterComp2 = companyRepository.save(Company.builder().companyName("Tập đoàn Thực phẩm Massan").companyTaxCode("0300123456").build());
        Company renterComp3 = companyRepository.save(Company.builder().companyName("Chuỗi siêu thị VinMart+").companyTaxCode("0100987654").build());

        // =================================================================
        // 2. KHỞI TẠO USERS - THỦ CÔNG TỪNG NGƯỜI
        // =================================================================
        List<User> allUsers = new ArrayList<>();

        // --- EMPLOYEE ---
        User employee = userRepository.save(User.builder()
                .email("employee@ailogis.com").password("password123").fullName("Trần Minh Admin")
                .phone("0911223344").role(Role.EMPLOYEE).status(UserStatus.ACTIVE)
                .avatarUrl("https://ailogis-storage-bucket.s3.ap-southeast-1.amazonaws.com/avatars/admin.png").build());
        allUsers.add(employee);

        // --- OWNERS ---
        User owner1 = userRepository.save(User.builder().company(ownerComp1).email("owner@ailogis.com").password("password123").fullName("Nguyễn Văn Sóng Thần").phone("0909123456").role(Role.OWNER).status(UserStatus.ACTIVE).build());
        User owner2 = userRepository.save(User.builder().company(ownerComp2).email("owner2@ailogis.com").password("password123").fullName("Lê Trọng Tân Bình").phone("0909999888").role(Role.OWNER).status(UserStatus.ACTIVE).build());
        User owner3 = userRepository.save(User.builder().company(ownerComp3).email("owner3@ailogis.com").password("password123").fullName("Trần Thị Dược").phone("0909777666").role(Role.OWNER).status(UserStatus.ACTIVE).build());
        allUsers.addAll(List.of(owner1, owner2, owner3));

        // --- RENTERS ---
        User renter1 = userRepository.save(User.builder().company(renterComp1).email("renter@ailogis.com").password("password123").fullName("Phạm Thị Thu mua").phone("0988776655").role(Role.RENTER).status(UserStatus.ACTIVE).build());
        User renter2 = userRepository.save(User.builder().company(renterComp2).email("renter2@ailogis.com").password("password123").fullName("Hoàng Văn Vận").phone("0988111222").role(Role.RENTER).status(UserStatus.ACTIVE).build());
        User renter3 = userRepository.save(User.builder().company(renterComp3).email("renter3@ailogis.com").password("password123").fullName("Đinh Cung Ứng").phone("0988333444").role(Role.RENTER).status(UserStatus.ACTIVE).build());
        allUsers.addAll(List.of(renter1, renter2, renter3));

        // =================================================================
        // 3. KHỞI TẠO CHỨNG CHỈ
        // =================================================================
        CertificationType haccp = certificationTypeRepository.save(CertificationType.builder().label("HACCP - Hệ thống quản lý an toàn thực phẩm").lawReferences("TCVN 5603:2020").updateDate(LocalDate.now()).pdfLink("https://ailogis-storage-bucket-492017761328-ap-southeast-1-an.s3.ap-southeast-1.amazonaws.com/certs/HACCP.pdf").build());
        CertificationType iso9001 = certificationTypeRepository.save(CertificationType.builder().label("ISO 9001:2015 - Quản lý chất lượng").lawReferences("ISO/TC 176").updateDate(LocalDate.now()).pdfLink("https://ailogis-storage-bucket-492017761328-ap-southeast-1-an.s3.ap-southeast-1.amazonaws.com/certs/ISO+9001_2015.pdf").build());

        // =================================================================
        // 4. KHỞI TẠO WAREHOUSES - THỦ CÔNG, STATUS LUÔN ACTIVE
        // =================================================================

        // KHO SỐ 1
        Warehouse wh1 = Warehouse.builder().owner(owner1).name("Tổng kho Lạnh Quốc tế Sóng Thần")
                .description("Hệ thống kho vận đạt tiêu chuẩn ISO ứng dụng công nghệ giám sát nhiệt độ tự động.")
                .locationProvince("Bình Dương").locationCommune("Dĩ An").locationAddressText("Số 10, KCN Sóng Thần 1")
                .status(WarehouseStatus.ACTIVE).isSponsor(true).sections(new ArrayList<>()).images(new ArrayList<>()).certificationSubmits(new ArrayList<>()).build();

        WarehouseSection sec1 = WarehouseSection.builder().warehouse(wh1).sector(1).totalCapacity(1500.0).availableCapacity(1500.0).tempMin(-25.0).tempMax(-18.0).humidity(60.0).hasCertification(true).build();
        sec1.setPriceTiers(List.of(PriceTier.builder().section(sec1).label("Gói lưu trữ theo tháng").value(260000.0).unit("VND").areaUnit("m3").build()));
        wh1.getSections().add(sec1);
        wh1.getImages().add(WarehouseImage.builder().warehouse(wh1).imageUrl("https://ailogis-storage-bucket-492017761328-ap-southeast-1-an.s3.ap-southeast-1.amazonaws.com/images/07c00336-f2b5-4528-84c1-d082a9805f19.jpg").isThumbnail(true).displayOrder(0).build());

        // Submit chứng chỉ ISO 9001 cho Kho 1
        wh1.getCertificationSubmits().add(CertificationSubmit.builder()
                .warehouse(wh1)
                .type(iso9001)
                .link("https://ailogis-storage-bucket-492017761328-ap-southeast-1-an.s3.ap-southeast-1.amazonaws.com/certs/ISO+9001_2015.pdf")
                .isVerified(true)
                .build());

        wh1 = warehouseRepository.save(wh1);

        // KHO SỐ 2
        Warehouse wh2 = Warehouse.builder().owner(owner2).name("Kho mát Nông sản Tân Bình")
                .description("Chuyên lưu trữ rau củ quả tươi sống, vị trí ngay sát trung tâm TPHCM, thuận tiện giao hàng nội thành.")
                .locationProvince("Hồ Chí Minh").locationCommune("Tân Bình").locationAddressText("KCN Tân Bình, Lô B2")
                .status(WarehouseStatus.ACTIVE).isSponsor(false).sections(new ArrayList<>()).images(new ArrayList<>()).certificationSubmits(new ArrayList<>()).build();

        WarehouseSection sec2 = WarehouseSection.builder().warehouse(wh2).sector(1).totalCapacity(800.0).availableCapacity(500.0).tempMin(2.0).tempMax(8.0).humidity(85.0).hasCertification(false).build();
        sec2.setPriceTiers(List.of(PriceTier.builder().section(sec2).label("Thuê bao nguyên khu (Tuần)").value(5000000.0).unit("VND").areaUnit("sector").build()));
        wh2.getSections().add(sec2);

        // Submit chứng chỉ HACCP cho Kho 2
        wh2.getCertificationSubmits().add(CertificationSubmit.builder()
                .warehouse(wh2)
                .type(haccp)
                .link("https://ailogis-storage-bucket-492017761328-ap-southeast-1-an.s3.ap-southeast-1.amazonaws.com/certs/HACCP.pdf")
                .isVerified(true)
                .build());

        wh2 = warehouseRepository.save(wh2);

        // KHO SỐ 3
        Warehouse wh3 = Warehouse.builder().owner(owner3).name("Kho lạnh Y tế & Dược phẩm Quận 9")
                .description("Kho chuyên dụng chuẩn GSP lưu trữ Vắc xin và Sinh phẩm y tế.")
                .locationProvince("Hồ Chí Minh").locationCommune("Quận 9").locationAddressText("Khu Công Nghệ Cao, Đường D1")
                .status(WarehouseStatus.ACTIVE).isSponsor(false).sections(new ArrayList<>()).images(new ArrayList<>()).certificationSubmits(new ArrayList<>()).build();

        WarehouseSection sec3 = WarehouseSection.builder().warehouse(wh3).sector(1).totalCapacity(300.0).availableCapacity(100.0).tempMin(-80.0).tempMax(-20.0).humidity(40.0).hasCertification(true).build();
        sec3.setPriceTiers(List.of(PriceTier.builder().section(sec3).label("Lưu trữ theo Pallet/Tháng").value(800000.0).unit("VND").areaUnit("pallet").build()));
        wh3.getSections().add(sec3);

        // Submit chứng chỉ HACCP và ISO 9001 cho Kho 3 - Chưa xác minh
        wh3.getCertificationSubmits().add(CertificationSubmit.builder()
                .warehouse(wh3)
                .type(haccp)
                .link("https://ailogis-storage-bucket-492017761328-ap-southeast-1-an.s3.ap-southeast-1.amazonaws.com/certs/HACCP.pdf")
                .isVerified(false)
                .build());

        wh3.getCertificationSubmits().add(CertificationSubmit.builder()
                .warehouse(wh3)
                .type(iso9001)
                .link("https://ailogis-storage-bucket-492017761328-ap-southeast-1-an.s3.ap-southeast-1.amazonaws.com/certs/ISO+9001_2015.pdf")
                .isVerified(false)
                .build());

        wh3 = warehouseRepository.save(wh3);

        // =================================================================
        // 5. KHỞI TẠO REQUEST & CONTRACT - ÉP STATUS LUÔN APPROVED/ACTIVE
        // =================================================================

        // Hợp đồng 1: Renter 1 thuê Kho 1
        RentalRequest req1 = requestRepository.save(RentalRequest.builder()
                .warehouse(wh1).renter(renter1).cargoDescription("Hải sản cá ngừ đại dương xuất khẩu")
                .duration(6).durationUnit("Tháng").status(RequestStatus.APPROVED).build());

        contractRepository.save(Contract.builder()
                .request(req1).owner(owner1).renter(renter1).startAt(LocalDate.now().minusDays(15))
                .status(ContractStatus.ACTIVE).build());

        // Hợp đồng 2: Renter 2 thuê Kho 2
        RentalRequest req2 = requestRepository.save(RentalRequest.builder()
                .warehouse(wh2).renter(renter2).cargoDescription("Rau củ Đà Lạt nhập kho chờ phân phối")
                .duration(2).durationUnit("Tuần").status(RequestStatus.APPROVED).build());

        contractRepository.save(Contract.builder()
                .request(req2).owner(owner2).renter(renter2).startAt(LocalDate.now().minusDays(5))
                .status(ContractStatus.ACTIVE).build());

        // =================================================================
        // 6. KHỞI TẠO SESSIONS - CHỈ DÙNG LOOP Ở ĐÂY ĐỂ VẼ BIỂU ĐỒ
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

        log.info("✅ Init Data Hoàn tất! Tất cả kho bãi, hợp đồng đều ở trạng thái ACTIVE/APPROVED sẵn sàng test.");
    }
}