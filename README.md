# PhongHub

Hệ thống quản lý vận hành nhà trọ và căn hộ dịch vụ: nhà trọ, phòng, hợp đồng,
người thuê, bảo trì, hóa đơn và thanh toán. UI server-render bằng Thymeleaf;
REST API phục vụ client tích hợp.

## Tính năng

- Nhà trọ, phòng, tài sản trong phòng (lịch sử hư hỏng/sửa chữa, mã QR)
- Hợp đồng, người ở, biên bản check-in/check-out có duyệt
- Hồ sơ giấy tờ người thuê (CCCD, GPLX) mã hóa khi lưu, xem đầy đủ phải nhập lại mật khẩu
- Chỉ số điện nước theo kỳ, đơn giá dịch vụ, hóa đơn tháng
- Thanh toán SePay: webhook chuyển khoản, checkout Sandbox, xử lý giao dịch dư hoặc không khớp
- Phiếu bảo trì, thông báo, chat realtime (SSE) có ảnh/video
- Quản trị tài khoản tại `/admin/users`, audit log không chứa dữ liệu cá nhân

## Công nghệ

Java 21, Spring Boot 4, Thymeleaf, PostgreSQL (Supabase ở production), Flyway,
Redis (session UI), Supabase Auth (JWT) và Supabase Storage (bucket private).

## Kiến trúc

Hexagonal Architecture:

| Thư mục | Vai trò |
| --- | --- |
| `domain` | Entity và business invariant, không phụ thuộc framework |
| `application` | Use case, service, port inbound/outbound |
| `adapter/in/web` | REST API và controller Thymeleaf |
| `adapter/out` | PostgreSQL, Supabase, SePay, SSE, storage |
| `config` | Wiring bean theo profile |
| `src/main/resources/db/migration` | Schema migration dùng chung |
| `src/main/resources/db/seed/docker` | Fixture chỉ dành cho profile `docker` |

## Chạy local

Yêu cầu: Docker Compose v2, JDK 21 (chỉ khi chạy test hoặc chạy backend trên host).
Local không cần `.env` hay tài khoản Supabase.

```bash
git clone --branch staging https://github.com/thanhhau15112000-dev/phonghub.git
cd phonghub
docker compose up --build
```

Mở <http://localhost:8080/>, health check tại `/health`. Flyway tự chạy migration
và fixture; dữ liệu nằm trong volume `phonghub_pgdata` (`docker compose down -v` để xóa).

Chạy backend trên host để debug (PowerShell):

```powershell
docker compose up -d db redis
$env:SPRING_PROFILES_ACTIVE = "docker"
.\mvnw.cmd spring-boot:run
```

PostgreSQL ở `localhost:55432`, Redis ở `localhost:16379`; đổi qua
`PHONGHUB_DB_*`, `PHONGHUB_REDIS_*` nếu cổng bị chiếm.

### Tài khoản demo

Profile `docker` cho phép chuyển actor bằng dropdown trên giao diện
(`/switch-user?userId=...`), không qua Supabase Auth.

| Role | Username |
| --- | --- |
| `ADMIN` | `admin` |
| `OWNER` | `owner1`, `owner2` |
| `STAFF` | `staff1`, `staff2` |
| `TECHNICIAN` | `tech1` |
| `TENANT` | `tenant1` |

## Profile

| Profile | Database | Xác thực | Storage |
| --- | --- | --- | --- |
| `docker` | PostgreSQL + Redis trong Compose, có fixture | Demo actor switching | In-memory |
| `test` | Repository in-memory | Demo | In-memory |
| `prod` | PostgreSQL ngoài, không chạy fixture | Supabase JWT, session lưu Redis | Supabase Storage |

Không bật `prod` cùng `docker`, và không dùng `prod` để chạy local.

## Kiểm thử và đóng gói

```bash
./mvnw test
```

```bash
./mvnw package -DskipTests -B
```

```bash
docker build -t phonghub:local .
```

## Production

Runbook deploy (Render, Supabase, Cloudflare, tạo Admin đầu tiên):
[docs/production-deployment.md](docs/production-deployment.md). Danh sách biến
môi trường đầy đủ ở [.env.example](.env.example); secret thật chỉ đặt trên môi
trường deploy.

Nhóm biến chính:

- Database và session: `SPRING_DATASOURCE_*`, `REDIS_URL` (thiếu thì ứng dụng không khởi động).
- Supabase: `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_SERVICE_ROLE_KEY` (chỉ dùng ở backend), `SUPABASE_JWKS_URI`, `SUPABASE_JWT_ISSUER`, `SUPABASE_JWT_AUDIENCE`.
- Storage: `SUPABASE_STORAGE_BUCKET` (mặc định `private-files`, phải để private), `STORAGE_MAX_FILE_SIZE_BYTES` (mặc định 10 MiB), `STORAGE_SIGNED_URL_TTL_SECONDS` (mặc định 600). Tệp chỉ được truy cập qua signed URL có hạn.
- Mã hóa dữ liệu cá nhân: `TENANT_DATA_KEY_ID`, `TENANT_DATA_ENCRYPTION_KEY`, `TENANT_DATA_HMAC_KEY` (mỗi khóa 32 byte, `openssl rand -base64 32`). Mất khóa là mất dữ liệu đã mã hóa; sao lưu khóa tách khỏi database và không đổi khóa khi chưa mã hóa lại dữ liệu.
- SePay: `SEPAY_WEBHOOK_API_KEY`, `SEPAY_BANK_*` cho chuyển khoản thật; `SEPAY_SANDBOX_CHECKOUT_ENABLED`, `SEPAY_MERCHANT_ID`, `SEPAY_SECRET_KEY`, `SEPAY_PUBLIC_BASE_URL` cho checkout Sandbox. IPN Sandbox trỏ tới `<SEPAY_PUBLIC_BASE_URL>/api/v1/payments/sepay/ipn`; hóa đơn chỉ được ghi nhận đã trả khi nhận IPN hoặc webhook hợp lệ.

## API

Đặc tả ở [docs/api-spec.yaml](docs/api-spec.yaml); các endpoint chưa có trong
đặc tả (chat, `/api/chat/**`) xem controller tại `adapter/in/web/api`. UI dùng session cookie;
REST API dùng Bearer JWT (`POST /api/auth/login`).

## Quy ước branch

- `feat/*`: phát triển theo task, mở pull request vào `staging`.
- `staging`: tích hợp và kiểm thử.
- `main`: release/production, chỉ promote từ `staging`.
