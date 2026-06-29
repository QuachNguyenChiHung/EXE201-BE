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

        private final String admin_avatar_url = "https://ailogis-storage-bucket-492017761328-ap-southeast-1-an.s3.ap-southeast-1.amazonaws.com/avatars/admin.png";
        private final String HACCP_link = "https://ailogis-storage-bucket-492017761328-ap-southeast-1-an.s3.ap-southeast-1.amazonaws.com/certs/HACCP.pdf";
        private final String ISO9001_link = "https://ailogis-storage-bucket-492017761328-ap-southeast-1-an.s3.ap-southeast-1.amazonaws.com/certs/ISO+9001_2015.pdf";

        private final String warehouse_image_url_1 = "https://ailogis-storage-bucket-492017761328-ap-southeast-1-an.s3.ap-southeast-1.amazonaws.com/warehouses/kho_1.jpg";

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

                employeeService.createEmployee(new UserCreateUpdateDTO("Trần Minh Admin", "employee@ailogis.com",
                                "password123", "0911223344", "EMPLOYEE", "ACTIVE", admin_avatar_url, null, null, null));
                User employee = userRepository.findByEmail("employee@ailogis.com").get();

                authService.registerUser(
                                new RegisterRequestDTO("owner@ailogis.com", "password123", "Nguyễn Văn Sóng Thần",
                                                "0909123456", "OWNER", "Tập đoàn Kho vận Sóng Thần", "0109876543"));
                User owner1 = userRepository.findByEmail("owner@ailogis.com").get();

                authService.registerUser(new RegisterRequestDTO("owner2@ailogis.com", "password123",
                                "Lê Trọng Tân Bình", "0909999888", "OWNER", "Công ty Cổ phần Logistics Tân Bình",
                                "0312345678"));
                User owner2 = userRepository.findByEmail("owner2@ailogis.com").get();

                authService.registerUser(new RegisterRequestDTO("owner3@ailogis.com", "password123", "Trần Thị Dược",
                                "0909777666", "OWNER", "Kho lạnh Dược phẩm Miền Nam", "0319998887"));
                User owner3 = userRepository.findByEmail("owner3@ailogis.com").get();

                authService.registerUser(new RegisterRequestDTO("renter@ailogis.com", "password123", "Phạm Thị Thu mua",
                                "0988776655", "RENTER", "CXNK Thủy sản Mê Kông", "0203456789"));
                User renter1 = userRepository.findByEmail("renter@ailogis.com").get();

                authService.registerUser(new RegisterRequestDTO("renter2@ailogis.com", "password123", "Hoàng Văn Vận",
                                "0988111222", "RENTER", "Tập đoàn Thực phẩm Massan", "0300123456"));
                User renter2 = userRepository.findByEmail("renter2@ailogis.com").get();

                authService.registerUser(new RegisterRequestDTO("renter3@ailogis.com", "password123", "Đinh Cung Ứng",
                                "0988333444", "RENTER", "Chuỗi siêu thị VinMart+", "0100987654"));
                User renter3 = userRepository.findByEmail("renter3@ailogis.com").get();

                List<User> allUsers = List.of(employee, owner1, owner2, owner3, renter1, renter2, renter3);

                // =================================================================
                // 2. KHỞI TẠO CHỨNG CHỈ
                // =================================================================
                CertificationType haccp = certificationTypeRepository.save(CertificationType.builder()
                                .label("HACCP - Hệ thống quản lý an toàn thực phẩm").lawReferences("TCVN 5603:2020")
                                .description("Hệ thống quản lý chất lượng vệ sinh an toàn thực phẩm, đảm bảo kiểm soát các mối nguy từ quá trình sản xuất đến tiêu thụ.")
                                .updateDate(LocalDate.now()).pdfLink(HACCP_link).build());
                CertificationType iso9001 = certificationTypeRepository.save(CertificationType.builder()
                                .label("ISO 9001:2015 - Quản lý chất lượng").lawReferences("ISO/TC 176")
                                .description("Tiêu chuẩn quốc tế về hệ thống quản lý chất lượng, giúp tổ chức đảm bảo cung cấp sản phẩm và dịch vụ đáp ứng yêu cầu khách hàng.")
                                .updateDate(LocalDate.now()).pdfLink(ISO9001_link).build());
                CertificationType iso22000 = certificationTypeRepository.save(CertificationType.builder()
                                .label("ISO 22000:2018 - Quản lý an toàn thực phẩm").lawReferences("ISO/TC 34/SC 17")
                                .description("Tiêu chuẩn quốc tế về hệ thống quản lý an toàn thực phẩm, kết hợp nguyên tắc HACCP và hệ thống quản lý.")
                                .updateDate(LocalDate.now()).pdfLink(ISO9001_link).build());
                CertificationType brc = certificationTypeRepository.save(CertificationType.builder()
                                .label("BRC Global Standard - An toàn thực phẩm quốc tế")
                                .lawReferences("BRCGS Issue 9:2022")
                                .description("Tiêu chuẩn toàn cầu (được công nhận bởi GFSI) cung cấp khuôn khổ khắt khe để quản lý an toàn, chất lượng thực phẩm.")
                                .updateDate(LocalDate.now()).pdfLink(HACCP_link).build());
                CertificationType gmp = certificationTypeRepository.save(CertificationType.builder()
                                .label("GMP - Thực hành sản xuất tốt").lawReferences("QCVN 01-02:2009/BCT")
                                .description("Hệ thống quy định chung hoặc những hướng dẫn đảm bảo điều kiện về kỹ thuật và quản lý để sản xuất ra sản phẩm an toàn.")
                                .updateDate(LocalDate.now()).pdfLink(ISO9001_link).build());
                CertificationType gsp = certificationTypeRepository.save(CertificationType.builder()
                                .label("GSP - Thực hành bảo quản tốt (Dược phẩm)")
                                .lawReferences("Thông tư 36/2018/TT-BYT")
                                .description("Thực hành tốt bảo quản thuốc, đảm bảo chất lượng dược phẩm, sinh phẩm được duy trì trong suốt quá trình lưu trữ.")
                                .updateDate(LocalDate.now()).pdfLink(ISO9001_link).build());
                CertificationType halal = certificationTypeRepository.save(CertificationType.builder()
                                .label("Halal - Chứng nhận thực phẩm đạt chuẩn Hồi giáo")
                                .lawReferences("TCVN 12944:2020")
                                .description("Chứng nhận sản phẩm không chứa các thành phần bị cấm theo luật Hồi giáo và đáp ứng các tiêu chuẩn vệ sinh nghiêm ngặt.")
                                .updateDate(LocalDate.now()).pdfLink(HACCP_link).build());

                // =================================================================
                // 3. KHỞI TẠO WAREHOUSES
                // =================================================================

                // KHO 1: Kho Sóng Thần (Tọa độ giả lập Dĩ An, Bình Dương)
                WarehouseCreateDTO wh1Dto = new WarehouseCreateDTO(
                                "Tổng kho Lạnh Quốc tế Sóng Thần",
                                "Hệ thống kho vận đạt tiêu chuẩn ISO ứng dụng công nghệ giám sát nhiệt độ tự động.",
                                "Số 10, KCN Sóng Thần 1", "Tỉnh Bình Dương", "Dĩ An",
                                106.7725, 10.9024, "75000",
                                List.of(new WarehouseSectionDTO(null, 1, 1500.0, 1500.0, -25.0, -18.0, 60.0, true,
                                                List.of(new PriceTierDTO(null, "Giá theo tháng", 260000.0, "month",
                                                                "m3")))));
                List<WarehouseCertCreateDTO> certs1 = List.of(new WarehouseCertCreateDTO(iso9001.getId(), HACCP_link));
                WarehouseResponseDTO wh1Res = ownerService.createWarehouse(owner1.getId(), wh1Dto,
                                List.of(warehouse_image_url_1), certs1);
                employeeService.verifyWarehouse(wh1Res.id(), WarehouseStatus.ACTIVE);

                // KHO 2: Kho Tân Bình (Tọa độ giả lập KCN Tân Bình, TP.HCM)
                WarehouseCreateDTO wh2Dto = new WarehouseCreateDTO(
                                "Kho mát Nông sản Tân Bình",
                                "Chuyên lưu trữ rau củ quả tươi sống, vị trí ngay sát trung tâm TPHCM, thuận tiện giao hàng nội thành.",
                                "KCN Tân Bình, Lô B2", "Thành phố Hồ Chí Minh", "Tân Bình",
                                106.6358, 10.8038, "70000",
                                List.of(new WarehouseSectionDTO(null, 1, 800.0, 800.0, 2.0, 8.0, 85.0, false,
                                                List.of(new PriceTierDTO(null, "Giá theo tuần", 5000000.0,
                                                                "week", "m3")))));
                List<WarehouseCertCreateDTO> certs2 = List.of(new WarehouseCertCreateDTO(haccp.getId(), ISO9001_link));
                WarehouseResponseDTO wh2Res = ownerService.createWarehouse(owner2.getId(), wh2Dto, new ArrayList<>(),
                                certs2);
                employeeService.verifyWarehouse(wh2Res.id(), WarehouseStatus.ACTIVE);

                // KHO 3: Kho Quận 9 (Tọa độ giả lập Khu Công Nghệ Cao Quận 9, TP.HCM)
                WarehouseCreateDTO wh3Dto = new WarehouseCreateDTO(
                                "Kho lạnh Y tế & Dược phẩm Quận 9",
                                "Kho chuyên dụng chuẩn GSP lưu trữ Vắc xin và Sinh phẩm y tế.",
                                "Khu Công Nghệ Cao, Đường D1", "Thành phố Hồ Chí Minh", "Quận 9",
                                106.8029, 10.8491, "70000",
                                List.of(new WarehouseSectionDTO(null, 1, 300.0, 300.0, -80.0, -20.0, 40.0, true,
                                                List.of(new PriceTierDTO(null, "Giá theo tháng", 800000.0,
                                                                "month", "m3")))));
                List<WarehouseCertCreateDTO> certs3 = List.of(
                                new WarehouseCertCreateDTO(haccp.getId(), HACCP_link),
                                new WarehouseCertCreateDTO(iso9001.getId(), ISO9001_link),
                                new WarehouseCertCreateDTO(gsp.getId(), ISO9001_link));
                WarehouseResponseDTO wh3Res = ownerService.createWarehouse(owner3.getId(), wh3Dto, new ArrayList<>(),
                                certs3);
                employeeService.verifyWarehouse(wh3Res.id(), WarehouseStatus.ACTIVE);

                // Employee duyệt Certification
                Warehouse wh1Entity = warehouseRepository.findById(wh1Res.id()).get();
                wh1Entity.getCertificationSubmits().forEach(c -> {
                        employeeService.reviewWarehouseCertification(
                                        c.getId(),
                                        new CertReviewDTO("VERIFIED", null, c.getType().getId()));
                });

                Warehouse wh2Entity = warehouseRepository.findById(wh2Res.id()).get();
                wh2Entity.getCertificationSubmits().forEach(c -> {
                        employeeService.reviewWarehouseCertification(
                                        c.getId(),
                                        new CertReviewDTO("VERIFIED", null, c.getType().getId()));
                });

                Warehouse wh3Entity = warehouseRepository.findById(wh3Res.id()).get();
                wh3Entity.getCertificationSubmits().forEach(c -> {
                        if (c.getType().getId().equals(haccp.getId())) {
                                employeeService.reviewWarehouseCertification(
                                                c.getId(),
                                                new CertReviewDTO("REJECTED", "Đã hết hạn", null));
                        }
                });

                // =================================================================
                // 3b. 10 KHO BÃI BỔ SUNG (đa tỉnh/thành, đa nhiệt độ)
                // =================================================================

                // KHO 4: Hà Nội – Đông Anh | Thực phẩm đông lạnh
                WarehouseCreateDTO wh4Dto = new WarehouseCreateDTO(
                                "Kho lạnh Thực phẩm Đông Anh",
                                "Kho hiện đại tại KCN Thăng Long, chuyên bảo quản thịt gia súc, gia cầm và chế phẩm sữa. Hệ thống điều nhiệt tự động 24/7, giám sát từ xa.",
                                "Lô CN7, KCN Thăng Long", "Thành phố Hà Nội", "Đông Anh",
                                105.8442, 21.1198, "10000",
                                List.of(new WarehouseSectionDTO(null, 1, 1200.0, 1200.0, -20.0, -15.0, 55.0, true,
                                                List.of(new PriceTierDTO(null, "Giá theo tháng", 280000.0, "month",
                                                                "m3")))));
                WarehouseResponseDTO wh4Res = ownerService.createWarehouse(owner1.getId(), wh4Dto,
                                List.of(warehouse_image_url_1),
                                List.of(new WarehouseCertCreateDTO(haccp.getId(), HACCP_link),
                                                new WarehouseCertCreateDTO(iso22000.getId(), ISO9001_link)));
                employeeService.verifyWarehouse(wh4Res.id(), WarehouseStatus.ACTIVE);

                // KHO 5: Hải Phòng – Hải An | Hải sản cảng biển (2 khu nhiệt độ)
                WarehouseCreateDTO wh5Dto = new WarehouseCreateDTO(
                                "Trung tâm Lạnh Cảng Hải Phòng",
                                "Sát Cảng Đình Vũ, tiếp nhận container lạnh, bảo quản hải sản nhập khẩu. Hai khu nhiệt độ độc lập phục vụ đa dạng nhu cầu.",
                                "KCN Đình Vũ, Đường Bạch Đằng", "Thành phố Hải Phòng", "Hải An",
                                106.7519, 20.8270, "18000",
                                List.of(
                                                new WarehouseSectionDTO(null, 1, 800.0, 800.0, -25.0, -18.0, 60.0, true,
                                                                List.of(new PriceTierDTO(null,
                                                                                "Giá theo tháng", 320000.0,
                                                                                "month", "m3"))),
                                                new WarehouseSectionDTO(null, 2, 400.0, 400.0, -2.0, 4.0, 85.0, false,
                                                                List.of(new PriceTierDTO(null,
                                                                                "Giá theo tháng", 180000.0,
                                                                                "month", "m3")))));
                WarehouseResponseDTO wh5Res = ownerService.createWarehouse(owner2.getId(), wh5Dto,
                                List.of(warehouse_image_url_1),
                                List.of(new WarehouseCertCreateDTO(haccp.getId(), HACCP_link),
                                                new WarehouseCertCreateDTO(iso9001.getId(), ISO9001_link)));
                employeeService.verifyWarehouse(wh5Res.id(), WarehouseStatus.ACTIVE);

                // KHO 6: Đà Nẵng – Sơn Trà | Hải sản xuất khẩu
                WarehouseCreateDTO wh6Dto = new WarehouseCreateDTO(
                                "Kho lạnh Hải sản Xuất khẩu Đà Nẵng",
                                "Chuẩn HACCP, phục vụ xuất khẩu sang Nhật Bản và EU. Gần cảng Tiên Sa, thủ tục nhanh gọn, hỗ trợ chứng từ xuất nhập khẩu.",
                                "KCN Thọ Quang, Sơn Trà", "Thành phố Đà Nẵng", "Sơn Trà",
                                108.2301, 16.0900, "55000",
                                List.of(new WarehouseSectionDTO(null, 1, 600.0, 600.0, -22.0, -18.0, 60.0, true,
                                                List.of(new PriceTierDTO(null, "Giá theo tháng", 310000.0,
                                                                "month", "m3")))));
                WarehouseResponseDTO wh6Res = ownerService.createWarehouse(owner3.getId(), wh6Dto,
                                List.of(warehouse_image_url_1),
                                List.of(new WarehouseCertCreateDTO(haccp.getId(), HACCP_link),
                                                new WarehouseCertCreateDTO(brc.getId(), HACCP_link)));
                employeeService.verifyWarehouse(wh6Res.id(), WarehouseStatus.ACTIVE);

                // KHO 7: Cần Thơ – Ninh Kiều | Thủy sản ĐBSCL
                WarehouseCreateDTO wh7Dto = new WarehouseCreateDTO(
                                "Kho Thủy sản ĐBSCL Cần Thơ",
                                "Trung tâm thu mua và bảo quản tôm, cá tra, cá basa cho vùng Đồng bằng sông Cửu Long. Công suất lớn, giá cạnh tranh nhất khu vực.",
                                "KCN Trà Nóc, Đường Trần Hoàng Na", "Thành phố Cần Thơ", "Ninh Kiều",
                                105.7469, 10.0180, "92000",
                                List.of(new WarehouseSectionDTO(null, 1, 1000.0, 1000.0, -18.0, -12.0, 65.0, true,
                                                List.of(new PriceTierDTO(null, "Giá theo tháng", 240000.0, "month",
                                                                "m3")))));
                WarehouseResponseDTO wh7Res = ownerService.createWarehouse(owner1.getId(), wh7Dto,
                                List.of(warehouse_image_url_1),
                                List.of(new WarehouseCertCreateDTO(haccp.getId(), HACCP_link),
                                                new WarehouseCertCreateDTO(halal.getId(), HACCP_link)));
                employeeService.verifyWarehouse(wh7Res.id(), WarehouseStatus.ACTIVE);

                // KHO 8: Long An – Bến Lức | Trái cây xuất khẩu (không cần cert đặc biệt)
                WarehouseCreateDTO wh8Dto = new WarehouseCreateDTO(
                                "Kho Mát Trái Cây Xuất Khẩu Long An",
                                "Chuyên thanh long, xoài, dứa phục vụ xuất khẩu Trung Quốc, Hàn Quốc. Dải nhiệt độ rộng phù hợp nhiều chủng loại trái cây nhiệt đới.",
                                "KCN Thuận Đạo, Đường tỉnh 830", "Tỉnh Long An", "Bến Lức",
                                106.4809, 10.6254, "85000",
                                List.of(new WarehouseSectionDTO(null, 1, 700.0, 700.0, 4.0, 12.0, 80.0, false,
                                                List.of(new PriceTierDTO(null, "Giá theo tháng", 150000.0,
                                                                "month", "m3")))));
                WarehouseResponseDTO wh8Res = ownerService.createWarehouse(owner2.getId(), wh8Dto,
                                List.of(warehouse_image_url_1), new ArrayList<>());
                employeeService.verifyWarehouse(wh8Res.id(), WarehouseStatus.ACTIVE);

                // KHO 9: Đồng Nai – Biên Hòa | Thực phẩm chế biến
                WarehouseCreateDTO wh9Dto = new WarehouseCreateDTO(
                                "Kho Lạnh Thực phẩm Chế biến Biên Hòa",
                                "Trong KCN Biên Hòa 2, phục vụ các doanh nghiệp chế biến thực phẩm. Kết nối thuận tiện tuyến cao tốc TP.HCM – Hà Nội.",
                                "KCN Biên Hòa 2, Đường số 4", "Tỉnh Đồng Nai", "Biên Hòa",
                                107.0338, 10.9450, "71000",
                                List.of(new WarehouseSectionDTO(null, 1, 900.0, 900.0, -15.0, -10.0, 60.0, true,
                                                List.of(new PriceTierDTO(null, "Giá theo tháng", 230000.0,
                                                                "month", "m3")))));
                WarehouseResponseDTO wh9Res = ownerService.createWarehouse(owner3.getId(), wh9Dto,
                                List.of(warehouse_image_url_1),
                                List.of(new WarehouseCertCreateDTO(haccp.getId(), HACCP_link),
                                                new WarehouseCertCreateDTO(iso9001.getId(), ISO9001_link),
                                                new WarehouseCertCreateDTO(iso22000.getId(), ISO9001_link)));
                employeeService.verifyWarehouse(wh9Res.id(), WarehouseStatus.ACTIVE);

                // KHO 10: Bà Rịa – Vũng Tàu | Thủy sản tươi sống
                WarehouseCreateDTO wh10Dto = new WarehouseCreateDTO(
                                "Kho Lạnh Thủy sản Tươi Vũng Tàu",
                                "Ngay cạnh cảng cá Vũng Tàu, chuyên bảo quản tôm hùm, mực ống, cá biển tươi sống. Phục vụ nhà hàng cao cấp và xuất khẩu.",
                                "Số 12, Đường 30/4, Phường Thắng Nhì", "Thành phố Hồ Chí Minh", "Thắng Nhì",
                                107.0843, 10.3460, "64000",
                                List.of(new WarehouseSectionDTO(null, 1, 450.0, 450.0, -5.0, 0.0, 90.0, true,
                                                List.of(new PriceTierDTO(null, "Giá theo tháng",
                                                                200000.0, "month", "m3")))));
                WarehouseResponseDTO wh10Res = ownerService.createWarehouse(owner1.getId(), wh10Dto,
                                List.of(warehouse_image_url_1),
                                List.of(new WarehouseCertCreateDTO(haccp.getId(), HACCP_link)));
                employeeService.verifyWarehouse(wh10Res.id(), WarehouseStatus.ACTIVE);

                // KHO 11: Bình Dương – Thuận An | Đa năng 2 khu vực
                WarehouseCreateDTO wh11Dto = new WarehouseCreateDTO(
                                "Kho Lạnh Đa Năng Thuận An",
                                "Hai khu vực nhiệt độ độc lập: đông lạnh sâu và bảo quản mát. Giá tốt, gần TP.HCM, phù hợp doanh nghiệp cần linh hoạt diện tích.",
                                "KCN Thuận An, Đường Lê Hồng Phong", "Tỉnh Bình Dương", "Thuận An",
                                106.6778, 10.9809, "75000",
                                List.of(
                                                new WarehouseSectionDTO(null, 1, 1000.0, 1000.0, -20.0, -10.0, 60.0,
                                                                true,
                                                                List.of(new PriceTierDTO(null, "Giá theo tháng",
                                                                                250000.0, "month", "m3"))),
                                                new WarehouseSectionDTO(null, 2, 600.0, 600.0, 0.0, 8.0, 80.0, false,
                                                                List.of(new PriceTierDTO(null,
                                                                                "Giá theo tháng", 130000.0,
                                                                                "month", "m3")))));
                WarehouseResponseDTO wh11Res = ownerService.createWarehouse(owner2.getId(), wh11Dto,
                                List.of(warehouse_image_url_1),
                                List.of(new WarehouseCertCreateDTO(iso9001.getId(), ISO9001_link)));
                employeeService.verifyWarehouse(wh11Res.id(), WarehouseStatus.ACTIVE);

                // KHO 12: Hải Phòng – Lê Chân | Dược phẩm & vắc xin
                WarehouseCreateDTO wh12Dto = new WarehouseCreateDTO(
                                "Kho Lạnh Dược phẩm Hải Phòng",
                                "Chuẩn GSP, chuyên lưu trữ vắc xin, huyết thanh và dược phẩm yêu cầu kiểm soát nhiệt độ nghiêm ngặt. Hệ thống ghi nhật ký tự động 24/7.",
                                "Số 88, Đường Đinh Tiên Hoàng, Phường Minh Khai", "Thành phố Hải Phòng", "Lê Chân",
                                106.6880, 20.8499, "18000",
                                List.of(new WarehouseSectionDTO(null, 1, 200.0, 200.0, 2.0, 8.0, 45.0, true,
                                                List.of(new PriceTierDTO(null, "Giá theo tháng",
                                                                650000.0, "month", "m3")))));
                WarehouseResponseDTO wh12Res = ownerService.createWarehouse(owner3.getId(), wh12Dto,
                                List.of(warehouse_image_url_1),
                                List.of(new WarehouseCertCreateDTO(haccp.getId(), HACCP_link),
                                                new WarehouseCertCreateDTO(iso9001.getId(), ISO9001_link),
                                                new WarehouseCertCreateDTO(gsp.getId(), ISO9001_link),
                                                new WarehouseCertCreateDTO(gmp.getId(), ISO9001_link)));
                employeeService.verifyWarehouse(wh12Res.id(), WarehouseStatus.ACTIVE);

                // KHO 13: Hà Nội – Hoàng Mai | Nông sản Bắc Bộ (2 khu mát)
                WarehouseCreateDTO wh13Dto = new WarehouseCreateDTO(
                                "Kho Nông sản Bắc Bộ Hoàng Mai",
                                "Chuyên bảo quản rau củ và trái cây miền Bắc cho hệ thống siêu thị và chuỗi bán lẻ. Hai khu vực riêng biệt tối ưu từng chủng loại.",
                                "Số 200, Đường Lĩnh Nam, KCN Vĩnh Tuy", "Thành phố Hà Nội", "Hoàng Mai",
                                105.8659, 20.9815, "10000",
                                List.of(
                                                new WarehouseSectionDTO(null, 1, 500.0, 500.0, 5.0, 12.0, 85.0, false,
                                                                List.of(new PriceTierDTO(null,
                                                                                "Giá theo tháng", 120000.0,
                                                                                "month", "m3"))),
                                                new WarehouseSectionDTO(null, 2, 400.0, 400.0, 8.0, 15.0, 75.0, false,
                                                                List.of(new PriceTierDTO(null,
                                                                                "Giá theo tháng",
                                                                                100000.0, "month", "m3")))));
                WarehouseResponseDTO wh13Res = ownerService.createWarehouse(owner1.getId(), wh13Dto,
                                List.of(warehouse_image_url_1), new ArrayList<>());
                employeeService.verifyWarehouse(wh13Res.id(), WarehouseStatus.ACTIVE);

                // Duyệt toàn bộ chứng nhận cho 10 kho mới
                for (Long whId : List.of(wh4Res.id(), wh5Res.id(), wh6Res.id(), wh7Res.id(), wh8Res.id(),
                                wh9Res.id(), wh10Res.id(), wh11Res.id(), wh12Res.id(), wh13Res.id())) {
                        Warehouse whEnt = warehouseRepository.findById(whId).get();
                        whEnt.getCertificationSubmits()
                                        .forEach(c -> employeeService.reviewWarehouseCertification(c.getId(),
                                                        new CertReviewDTO("VERIFIED", null, c.getType().getId())));
                }

                // Lưu entity refs dùng cho reviews
                Warehouse wh4Entity = warehouseRepository.findById(wh4Res.id()).get();
                Warehouse wh5Entity = warehouseRepository.findById(wh5Res.id()).get();
                Warehouse wh6Entity = warehouseRepository.findById(wh6Res.id()).get();
                Warehouse wh7Entity = warehouseRepository.findById(wh7Res.id()).get();
                Warehouse wh8Entity = warehouseRepository.findById(wh8Res.id()).get();
                Warehouse wh9Entity = warehouseRepository.findById(wh9Res.id()).get();
                Warehouse wh10Entity = warehouseRepository.findById(wh10Res.id()).get();
                Warehouse wh12Entity = warehouseRepository.findById(wh12Res.id()).get();
                Warehouse wh13Entity = warehouseRepository.findById(wh13Res.id()).get();

                // =================================================================
                // 4. KHỞI TẠO REQUEST & CONTRACTS
                // =================================================================

                // HỢP ĐỒNG 1: Renter 1 thuê Kho 1 (Tổng giá gốc: 500m3 * 260k = 130tr/tháng),
                // Renter trả giá còn 125tr
                Long sec1Id = wh1Entity.getSections().get(0).getId();
                Long pt1Id = wh1Entity.getSections().get(0).getPriceTiers().get(0).getId();
                RentRequestCreateDTO req1Dto = new RentRequestCreateDTO(wh1Entity.getId(),
                                "Hải sản cá ngừ đại dương xuất khẩu", null, 6, "Tháng",
                                LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(6),
                                125000000.0,
                                List.of(new RentRequestDetailCreateDTO(sec1Id, pt1Id, 500.0, "m3")));

                RentRequestResponseDTO req1Res = rentalRequestService.createRequest(renter1.getId(), req1Dto);
                ownerService.updateRequestStatus(owner1.getId(), req1Res.id(), new RequestStatusUpdateDTO(
                                RequestStatus.APPROVED, null, null, "Đồng ý cho thuê giá gốc"));

                ContractResponseDTO c1Res = contractService.createContract(owner1.getId(), new ContractCreateDTO(
                                req1Res.id(),
                                (long) (500.0 * 260000.0 * 6),
                                null, null, null, null, null, null, // Các trường startAt, endAt, điều khoản
                                null, null, null, null, null, // Thông tin Owner
                                null, null, null, null, null, // Thông tin Renter
                                "PENDING" // Status
                ));

                // HỢP ĐỒNG 2: Renter 2 thuê Kho 2 (Tổng giá gốc: 1 sector * 5tr = 5tr/tuần),
                // Renter trả giá còn 4.8tr
                Long sec2Id = wh2Entity.getSections().get(0).getId();
                Long pt2Id = wh2Entity.getSections().get(0).getPriceTiers().get(0).getId();
                RentRequestCreateDTO req2Dto = new RentRequestCreateDTO(wh2Entity.getId(),
                                "Rau củ Đà Lạt nhập kho chờ phân phối", null, 2, "Tuần",
                                LocalDate.now().minusWeeks(5), LocalDate.now().plusWeeks(2),
                                4800000.0,
                                List.of(new RentRequestDetailCreateDTO(sec2Id, pt2Id, 1.0, "sector")));

                RentRequestResponseDTO req2Res = rentalRequestService.createRequest(renter2.getId(), req2Dto);
                ownerService.updateRequestStatus(owner2.getId(), req2Res.id(), new RequestStatusUpdateDTO(
                                RequestStatus.APPROVED, null, null, "Kho lạnh Tân Bình xác nhận yêu cầu."));

                // Renter 3 gửi yêu cầu thuê Kho 1 nhưng Owner chưa duyệt (Tổng giá gốc: 200m3 *
                // 260k = 52tr/tháng), Renter trả giá 50tr
                RentRequestCreateDTO req3Dto = new RentRequestCreateDTO(wh1Entity.getId(),
                                "Thịt bò Kobe nhập khẩu đông lạnh", null, 3, "Tháng",
                                LocalDate.now(), LocalDate.now().plusMonths(3),
                                50000000.0,
                                List.of(new RentRequestDetailCreateDTO(sec1Id, pt1Id, 200.0, "m3")));
                rentalRequestService.createRequest(renter3.getId(), req3Dto);

                ContractResponseDTO c2Res = contractService.createContract(owner2.getId(), new ContractCreateDTO(
                                req2Res.id(),
                                (long) 5000000.0 * 2,
                                null, null, null, null, null, null,
                                null, null, null, null, null,
                                null, null, null, null, null,
                                "PENDING"));

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
                                                .loginAt(LocalDateTime.of(loginDate.getYear(), loginDate.getMonth(),
                                                                loginDate.getDayOfMonth(), random.nextInt(24),
                                                                random.nextInt(60)))
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
                                null, "Gói AI Pro", "Tối ưu hóa RAG chuyên sâu", 200000, 100000, 499000.0, "VND",
                                null));

                // 2. Tạo gói Tài trợ (Sponsor)
                SponsorTierDTO sponsorGoldDto = employeeService.createSponsorTier(new SponsorTierDTO(
                                null, 1, 1000000.0, 10000000.0, "Tài trợ Vàng (Top 1)", null, true));
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
                List<Review> reviews = new ArrayList<>(List.of(
                                // Kho 1: Avg 3.5 sao
                                Review.builder().user(renter1).warehouse(wh1Entity).rating(4)
                                                .comment("Kho tạm ổn, nhưng đường vào hơi nhỏ, bãi đậu xe hay bị kẹt.")
                                                .build(),
                                Review.builder().user(renter2).warehouse(wh1Entity).rating(3).comment(
                                                "Dịch vụ bình thường, thỉnh thoảng nhiệt độ kho báo cáo hơi chậm.")
                                                .build(),

                                // Kho 2: Avg 5.0 sao
                                Review.builder().user(renter3).warehouse(wh2Entity).rating(5).comment(
                                                "Vị trí ngay sát trung tâm, xe tải ra vào rất thuận tiện. Tuyệt vời!")
                                                .build(),

                                // Kho 3: Avg 5.0 sao
                                Review.builder().user(renter1).warehouse(wh3Entity).rating(5).comment(
                                                "Kho y tế chuẩn GSP, quy trình kiểm soát vi sinh rất khắt khe và an toàn tuyệt đối.")
                                                .build(),
                                Review.builder().user(renter2).warehouse(wh3Entity).rating(5).comment(
                                                "Rất hài lòng với cách quản lý chuyên nghiệp, thủ tục giấy tờ cực kỳ nhanh gọn.")
                                                .build(),

                                // Kho 4 – Hà Nội Đông Anh: Avg 4.0
                                Review.builder().user(renter2).warehouse(wh4Entity).rating(4).comment(
                                                "Kho sạch sẽ, nhiệt độ ổn định. Vị trí KCN Thăng Long thuận tiện cho xe container.")
                                                .build(),
                                Review.builder().user(renter3).warehouse(wh4Entity).rating(4).comment(
                                                "Nhân viên nhiệt tình, thủ tục nhập xuất hàng nhanh gọn. Sẽ tiếp tục thuê.")
                                                .build(),

                                // Kho 5 – Cảng Hải Phòng: Avg 4.5
                                Review.builder().user(renter1).warehouse(wh5Entity).rating(5).comment(
                                                "Sát cảng Đình Vũ, tiết kiệm chi phí vận chuyển rất nhiều. Khu đông lạnh sâu hoạt động hoàn hảo.")
                                                .build(),
                                Review.builder().user(renter3).warehouse(wh5Entity).rating(4).comment(
                                                "Hai khu nhiệt độ riêng biệt rất linh hoạt. Giá hợp lý so với vị trí đắc địa.")
                                                .build(),

                                // Kho 6 – Đà Nẵng: Avg 5.0
                                Review.builder().user(renter2).warehouse(wh6Entity).rating(5).comment(
                                                "Xuất khẩu sang Nhật, yêu cầu chứng từ HACCP rất ngặt nghèo — kho này đáp ứng hoàn toàn.")
                                                .build(),

                                // Kho 7 – Cần Thơ: Avg 4.0
                                Review.builder().user(renter1).warehouse(wh7Entity).rating(4).comment(
                                                "Giá cạnh tranh nhất vùng ĐBSCL. Công suất lớn, phù hợp thu mua vụ tôm cá tra.")
                                                .build(),
                                Review.builder().user(renter3).warehouse(wh7Entity).rating(4).comment(
                                                "Chút lưu ý là đường vào kho mùa mưa hơi trơn, nhưng dịch vụ kho bãi rất tốt.")
                                                .build(),

                                // Kho 8 – Long An trái cây: Avg 4.0
                                Review.builder().user(renter2).warehouse(wh8Entity).rating(4).comment(
                                                "Giá tốt nhất cho kho mát trái cây khu vực Long An. Xuất thanh long đi Trung Quốc rất thuận tiện.")
                                                .build(),

                                // Kho 9 – Đồng Nai chế biến: Avg 4.5
                                Review.builder().user(renter1).warehouse(wh9Entity).rating(5).comment(
                                                "HACCP + ISO 9001 đầy đủ, đối tác nước ngoài kiểm tra rất hài lòng. Đường cao tốc sát bên.")
                                                .build(),
                                Review.builder().user(renter2).warehouse(wh9Entity).rating(4).comment(
                                                "Diện tích vừa đủ cho dây chuyền chế biến quy mô vừa. Dịch vụ hỗ trợ kỹ thuật tốt.")
                                                .build(),

                                // Kho 10 – Vũng Tàu hải sản tươi: Avg 5.0
                                Review.builder().user(renter3).warehouse(wh10Entity).rating(5).comment(
                                                "Ngay cảng cá, tôm hùm giữ tươi cực kỳ tốt. Nhà hàng khách sạn 5 sao dùng rất hài lòng.")
                                                .build(),

                                // Kho 12 – Hải Phòng dược phẩm: Avg 5.0
                                Review.builder().user(renter1).warehouse(wh12Entity).rating(5).comment(
                                                "Hệ thống ghi log nhiệt độ tự động đáp ứng chuẩn GDP/GSP quốc tế. Tuyệt đối tin tưởng cho vắc xin.")
                                                .build(),
                                Review.builder().user(renter2).warehouse(wh12Entity).rating(5).comment(
                                                "Chứng chỉ đầy đủ, nhân viên được đào tạo bài bản. Lựa chọn hàng đầu cho dược phẩm tại Hải Phòng.")
                                                .build(),

                                // Kho 13 – Hà Nội nông sản: Avg 4.0
                                Review.builder().user(renter3).warehouse(wh13Entity).rating(4).comment(
                                                "Hai khu nhiệt độ riêng giúp bảo quản rau củ và trái cây không bị lẫn mùi. Giá rẻ, phù hợp chuỗi bán lẻ.")
                                                .build()));
                reviewRepository.saveAll(reviews);

                log.info("✅ Init Data Hoàn tất! Tất cả kho bãi, hợp đồng đều ở trạng thái ACTIVE/APPROVED sẵn sàng test.");
        }
}