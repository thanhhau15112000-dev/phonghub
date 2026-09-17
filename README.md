# PhongHub

Hệ thống quản lý vận hành phòng trọ và căn hộ dịch vụ (Boarding House & Rental Operations Management System).

---

## 1. Kiến trúc Hệ thống (Hexagonal Architecture)

Dự án tuân thủ nghiêm ngặt mô hình **Hexagonal Architecture (Ports & Adapters)**:
- **Domain Layer (`com.phonghub.domain.*`)**: Chứa pure Java entities, value objects, và domain business invariants. Hoàn toàn độc lập, không phụ thuộc vào Spring Framework, JPA/Hibernate, Web hay Supabase.
- **Application Layer (`com.phonghub.application.*`)**: Định nghĩa Inbound Ports (`UseCases`), Outbound Ports (`RepositoryPorts`), và Application Services dàn xếp nghiệp vụ và kiểm tra phân quyền (RBAC + Property Scoping).
- **Adapter Layer (`com.phonghub.adapter.*`)**:
  - `adapter.in.web`: REST API Controllers và Spring MVC UI Controllers (Thymeleaf).
  - `adapter.out.persistence.inmemory`: In-memory data store và seeder phục vụ local development và automated integration tests mà không cần database ngoài.
  - `adapter.out.persistence.postgres`: Triển khai các repository ports trên cơ sở dữ liệu PostgreSQL thông qua Spring `NamedParameterJdbcTemplate` và Flyway migrations.
  - `adapter.out.identity`: Quản lý danh tính người dùng (`LocalDemoAuthenticationAdapter` cho local test/demo, seam tích hợp cho `SupabaseAuthenticationAdapter`).

---

## 2. Các Vai trò Người dùng (Actors & RBAC)

Hệ thống hỗ trợ 4 vai trò chính:
1. **ADMIN**: Toàn quyền quản trị toàn bộ tòa nhà, phòng, hợp đồng, yêu cầu bảo trì, và nhân sự.
2. **STAFF**: Quản lý các tòa nhà được phân công thông qua bảng liên kết `staff_property_assignments`. Không thể tạo hoặc can thiệp vào tòa nhà ngoài phạm vi được gán.
3. **TECHNICIAN**: Xem và cập nhật các phiếu bảo trì được giao hoặc thuộc các tòa nhà được phân công.
4. **TENANT**: Xem thông tin phòng đang thuê, hợp đồng (áp dụng cho cả người đứng tên chính và người ở cùng / additional occupant), và gửi yêu cầu bảo trì.

---

## 3. Chế độ Vận hành: Local Demo vs. Production

### 3.1. Local Demo Mode (`default` hoặc profile `!prod`)
- Kích hoạt khi chạy mặc định hoặc profile khác `prod`.
- Cơ chế lưu trữ: Sử dụng In-Memory repository, tự động seed dữ liệu mẫu qua `DataSeeder`.
- Demo Actor Switching: Cung cấp switcher trên giao diện web (`/switch-user`), query parameter `?asUser=...`, và HTTP header `X-User-Id` để chuyển đổi nhanh giữa các actor (Admin, Staff 1, Staff 2, Technician, Tenant) trong quá trình demo và test.
- Cấu hình: `phonghub.demo.enabled: true`.

Lệnh chạy local:
```bash
./mvnw spring-boot:run
```
Truy cập UI tại: `http://localhost:8080/`

### 3.2. Production Mode (`prod` profile)
- Kích hoạt bằng flag: `--spring.profiles.active=prod` hoặc biến môi trường `SPRING_PROFILES_ACTIVE=prod`.
- Cơ chế lưu trữ: Kết nối PostgreSQL database và tự động chạy migration Flyway (`db/migration/`).
- Demo Actor Switching Isolation (Fix F1):
  - `phonghub.demo.enabled` tự động chuyển sang `false`.
  - Endpoint `/switch-user` trả về mã lỗi `403 Forbidden`.
  - Các header (`X-User-Id`) hoặc query params (`asUser`) bị vô hiệu hóa hoàn toàn, không thể ghi đè danh tính người dùng.
  - Giao diện actor switcher box bị ẩn hoàn toàn khỏi template.
- Fail-Fast Startup Validation: `ProductionConfigValidator` sẽ kiểm tra và lập tức từ chối khởi động (fail-fast) nếu thiếu bất kỳ biến môi trường bắt buộc nào:
  - `SPRING_DATASOURCE_URL`
  - `SPRING_DATASOURCE_USERNAME`
  - `SPRING_DATASOURCE_PASSWORD`
  - `SUPABASE_URL`
  - `SUPABASE_ANON_KEY`

---

## 4. Health Check Endpoint

Ứng dụng cung cấp endpoint giám sát trạng thái hoạt động:
```http
GET /health
```
Phản hồi mẫu:
```json
{
  "status": "UP",
  "timestamp": "2026-09-17T09:25:00Z"
}
```

---

## 5. Docker Deployment

Ứng dụng đi kèm multi-stage `Dockerfile` tối ưu hóa kích thước và bảo mật (chạy dưới non-root user `phonghub` trên nền Eclipse Temurin 21 JRE Alpine).

### 5.1. Build Docker Image
```bash
docker build -t phonghub:latest .
```

### 5.2. Chạy Container Production
```bash
docker run -d \
  --name phonghub-app \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://<db-host>:5432/<db-name> \
  -e SPRING_DATASOURCE_USERNAME=<db-user> \
  -e SPRING_DATASOURCE_PASSWORD=<db-pass> \
  -e SUPABASE_URL=https://<ref>.supabase.co \
  -e SUPABASE_ANON_KEY=<anon-key> \
  phonghub:latest
```

---

## 6. Kiểm thử Tự động (Automated Verification)

Chạy toàn bộ test suite:
```bash
./mvnw clean test
```

Đóng gói JAR file:
```bash
./mvnw clean package
```
