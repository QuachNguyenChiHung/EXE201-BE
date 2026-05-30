package com.ailogis.api.config;

import com.ailogis.api.entity.*;
import com.ailogis.api.enums.Role;
import com.ailogis.api.enums.UserStatus;
import com.ailogis.api.enums.WarehouseStatus;
import com.ailogis.api.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final WarehouseRepository warehouseRepository;
    private final CertificationTypeRepository certificationTypeRepository;

    @Override
    public void run(String... args) throws Exception {
        // Chỉ chạy nếu hệ thống chưa có dữ liệu tài khoản nào
        if (userRepository.count() == 0) {
            log.info("⏳ Đang khởi tạo dữ liệu mẫu 3 nhóm Role bảo mật...");

            // =================================================================
            // 1. KHỞI TẠO ĐỐI TƯỢNG DOANH NGHIỆP B2B (COMPANIES)
            // =================================================================

            // Công ty phía Nhà cung cấp dịch vụ kho bãi (Owner)
            Company ownerCompany = Company.builder()
                    .companyName("Tập đoàn Kho vận Logistics Toàn Cầu")
                    .companyTaxCode("0109876543")
                    .build();
            ownerCompany = companyRepository.save(ownerCompany);

            // Công ty phía Khách hàng doanh nghiệp SME (Renter) - Phân khúc Nông hải sản
            Company renterCompany = Company.builder()
                    .companyName("Công ty Cổ phần Xuất Nhập khẩu Thủy sản Mê Kông")
                    .companyTaxCode("0203456789")
                    .build();
            renterCompany = companyRepository.save(renterCompany);

            // =================================================================
            // 2. KHỞI TẠO TÀI KHOẢN NGƯỜI DÙNG THEO CÁC VAI TRÒ (USERS & ROLES)
            // =================================================================

            // Tài khoản 1: Quản trị viên hệ thống (EMPLOYEE)
            User employee = User.builder()
                    .company(null) // Nhân viên hệ thống nội bộ không thuộc công ty đối tác bên ngoài
                    .email("employee@ailogis.com")
                    .password("password123")
                    .fullName("Trần Minh Admin")
                    .phone("0911223344")
                    .role(Role.EMPLOYEE)
                    .status(UserStatus.ACTIVE)
                    .avatarUrl("https://ailogis-storage-bucket.s3.ap-southeast-1.amazonaws.com/avatars/admin.png")
                    .build();
            userRepository.save(employee);

            // Tài khoản 2: Đại diện nhà kho (OWNER)
            User owner = User.builder()
                    .company(ownerCompany)
                    .email("owner@ailogis.com")
                    .password("password123")
                    .fullName("Nguyễn Văn Chủ Kho")
                    .phone("0909123456")
                    .role(Role.OWNER)
                    .status(UserStatus.ACTIVE)
                    .avatarUrl("https://ailogis-storage-bucket.s3.ap-southeast-1.amazonaws.com/avatars/owner.png")
                    .build();
            owner = userRepository.save(owner);

            // Tài khoản 3: Đại diện doanh nghiệp đi thuê (RENTER)
            User renter = User.builder()
                    .company(renterCompany)
                    .email("renter@ailogis.com")
                    .password("password123")
                    .fullName("Phạm Thị Thu mua")
                    .phone("0988776655")
                    .role(Role.RENTER)
                    .status(UserStatus.ACTIVE)
                    .avatarUrl("https://ailogis-storage-bucket.s3.ap-southeast-1.amazonaws.com/avatars/renter.png")
                    .build();
            userRepository.save(renter);

            // =================================================================
            // 3. KHỞI TẠO HẠ TẦNG KHO MẪU GẮN CHÍNH XÁC VÀO OWNER
            // =================================================================

            // Khởi tạo loại chứng chỉ mặc định trong hệ thống
            CertificationType haccpType = CertificationType.builder()
                    .label("HACCP - Hệ thống quản lý an toàn thực phẩm")
                    .lawReferences("TCVN 5603:2020")
                    .build();
            haccpType = certificationTypeRepository.save(haccpType);

            Warehouse warehouse = Warehouse.builder()
                    .owner(owner) // Gán trực tiếp tài khoản OWNER vừa tạo phía trên
                    .name("Tổng kho Lạnh Quốc tế Sóng Thần")
                    .description("Hệ thống kho vận đạt tiêu chuẩn ISO ứng dụng công nghệ giám sát nhiệt độ tự động.")
                    .locationProvince("Bình Dương")
                    .locationCommune("Dĩ An")
                    .locationAddressText("Số 10, KCN Sóng Thần 1")
                    .status(WarehouseStatus.APPROVED) // Đặt trạng thái APPROVED để hiển thị ra trang public công khai
                    .isSponsor(false)
                    .sections(new ArrayList<>())
                    .images(new ArrayList<>())
                    .certificationSubmits(new ArrayList<>())
                    .build();

            // Thiết lập phân vùng phòng kho đông lạnh chuyên dụng
            WarehouseSection frozenSection = WarehouseSection.builder()
                    .warehouse(warehouse)
                    .sector(1)
                    .totalCapacity(1500.0)
                    .availableCapacity(1500.0)
                    .tempMin(-25.0)
                    .tempMax(-18.0)
                    .humidity(60.0)
                    .hasCertification(true)
                    .build();

            frozenSection.setPriceTiers(List.of(
                    PriceTier.builder().section(frozenSection).label("Gói lưu trữ theo tháng").value(260000.0).unit("VND").areaUnit("m3").build()
            ));

            warehouse.getSections().add(frozenSection);

            // Đính kèm dữ liệu ảnh URL đại diện trên đám mây S3
            WarehouseImage sampleImage = WarehouseImage.builder()
                    .warehouse(warehouse)
                    .imageUrl("https://ailogis-storage-bucket-492017761328-ap-southeast-1-an.s3.ap-southeast-1.amazonaws.com/images/sample-warehouse.jpg")
                    .isThumbnail(true)
                    .displayOrder(0)
                    .build();
            warehouse.getImages().add(sampleImage);

            // Đệ quy lưu thông tin hạ tầng cơ sở dữ liệu
            warehouseRepository.save(warehouse);

            log.info("✅ Khởi tạo dữ liệu mẫu thành công: 1 Admin, 1 Owner (Kèm 1 Kho), 1 Renter.");
        }
    }
}