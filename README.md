# Ailogis API - B2B Cold Storage Matching Platform

Ailogis là nền tảng B2B đa bên (Multi-sided Platform) giúp kết nối các chủ sở hữu kho lạnh (Owners) với các doanh nghiệp SME trong lĩnh vực Nông/Hải sản, Dược phẩm và Hàng công nghiệp (Renters) có nhu cầu thuê không gian lưu trữ ngắn hạn.

Dự án Backend này cung cấp hệ thống RESTful API mạnh mẽ, quản lý luồng nghiệp vụ phức tạp từ việc đăng tải kho bãi, kiểm duyệt chứng chỉ, cho đến xử lý hợp đồng thuê và quản lý sức chứa theo thời gian thực.

## Công Nghệ Sử Dụng (Tech Stack)

* **Ngôn ngữ & Framework:** Java 17, Spring Boot 3.x
* **Bảo mật:** Spring Security, JSON Web Token (JWT)
* **Cơ sở dữ liệu:** PostgreSQL, Spring Data JPA / Hibernate
* **Lưu trữ đám mây:** AWS S3 (Amazon Web Services SDK v2)
* **Triển khai & Môi trường:** Docker, Docker Compose
* **Công cụ thiết kế & Test:** Postman, DBML

## Tính Năng Nổi Bật (Key Features)

* **Role-Based Access Control (RBAC):** Hệ thống phân quyền chặt chẽ với 3 vai trò biệt lập:
    * `EMPLOYEE`: Quản trị viên hệ thống, duyệt kho bãi.
    * `OWNER`: Chủ kho, quản lý hạ tầng (phòng lạnh, sức chứa, biểu giá) và duyệt đơn thuê.
    * `RENTER`: Khách thuê, tìm kiếm kho bãi và gửi yêu cầu đặt chỗ.
* **Tích Hợp Cloud Storage:** Upload trực tiếp hình ảnh kho và hồ sơ pháp lý (PDF) lên hệ thống Amazon S3. Đảm bảo file được định danh duy nhất (UUID) và quản lý độc lập.
* **Quản Lý Sức Chứa Động (Dynamic Capacity):** Tự động tính toán và cập nhật sức chứa khả dụng (`availableCapacity`) của từng phòng kho khi Hợp đồng được kích hoạt hoặc hủy bỏ.
* **Mô Hình Dữ Liệu Bền Vững:** Các chứng chỉ, biểu giá, và thông tin pháp lý trên hợp đồng được thiết kế theo dạng *Snapshot* (Bản chụp bất biến), đảm bảo tính toàn vẹn của dữ liệu lịch sử.

## Yêu Cầu Hệ Thống (Prerequisites)

Để chạy dự án trên máy cá nhân, bạn cần cài đặt:
* [JDK 17+](https://adoptium.net/)
* [Maven 3.8+](https://maven.apache.org/)
* [Docker Desktop](https://www.docker.com/products/docker-desktop) (Để chạy Database cục bộ)
* Một tài khoản AWS IAM có quyền `AmazonS3FullAccess`.

## 🛠Hướng Dẫn Cài Đặt (Getting Started)

### 1. Khởi chạy Database bằng Docker
Hệ thống sử dụng Docker Compose để tạo nhanh môi trường PostgreSQL. Mở terminal tại thư mục gốc của dự án và chạy lệnh:
```bash
docker-compose up -d