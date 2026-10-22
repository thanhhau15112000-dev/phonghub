# PhongHub

PhongHub là hệ thống quản lý vận hành nhà trọ và căn hộ dịch vụ. Backend hiện
tập trung vào các nghiệp vụ property, room, contract và maintenance ticket,
được xây dựng bằng Spring Boot theo Hexagonal Architecture.

## Trạng thái hiện tại

- Backend MVP đã có REST API và giao diện Thymeleaf hiện tại.
- Giao diện Thymeleaf production đã có đăng nhập bằng session, đổi mật khẩu bắt
  buộc ở lần đầu và khu vực quản trị tài khoản tại `/admin/users`.
- Local runtime mặc định dùng PostgreSQL chạy trong Docker.
- Dữ liệu local được tạo bằng Flyway fixture trong database Docker, không được
  tạo trực tiếp bởi Java `DataSeeder`.
- Profile `prod` dùng PostgreSQL bên ngoài, phù hợp với database của Supabase.
- React SPA không thuộc scope hiện tại; UI chính được server-render bằng
  Thymeleaf, còn REST API vẫn giữ contract cho client tích hợp.
- Đã có tích hợp thanh toán SePay (webhook) và hóa đơn phí sửa chữa
  (migration V10-V12).
- Snapshot này không được gọi là release-ready. Việc deploy thật, CORS, URL
  runtime và dữ liệu Supabase cần được xác minh riêng.

## Kiến trúc

- `domain`: entity và business invariant, không phụ thuộc Spring, database hay
  Supabase.
- `application`: use case, service và port cho inbound/outbound.
- `adapter/in/web`: REST API và giao diện Thymeleaf.
- `adapter/out/persistence/postgres`: repository dùng PostgreSQL và
  `NamedParameterJdbcTemplate`.
- `src/main/resources/db/migration`: schema migration dùng chung cho các
  database PostgreSQL.
- `src/main/resources/db/seed/docker`: dữ liệu fixture chỉ dành cho profile
  `docker`.
- `compose.yaml`: PostgreSQL và backend local chạy bằng Docker Compose.
- `Dockerfile`: image backend cho môi trường deploy.

## Repository và base branch

- Repository: <https://github.com/thanhhau15112000-dev/phonghub>
- `staging` là base branch để lấy source tích hợp, chạy kiểm thử và mở pull
  request cho các feature/task.
- `main` là branch release/production; không dùng `main` làm base cho feature
  thông thường.

Clone đúng branch cho thành viên mới:

```bash
git clone --branch staging --single-branch https://github.com/thanhhau15112000-dev/phonghub.git
cd phonghub
git status --short --branch
```

Nếu đã clone repository trước đó:

```bash
git fetch origin
git switch staging
git pull --ff-only origin staging
```

## Yêu cầu

- Git
- Docker Desktop có Docker Compose v2 trở lên
- JDK 21
- Maven Wrapper có sẵn trong repository; không cần cài Maven riêng.

JDK chỉ bắt buộc khi chạy test hoặc chạy backend bằng Maven/IDE trên máy host.
Nếu chỉ chạy toàn bộ stack bằng Docker Compose, image builder dùng JDK bên
trong Docker. Node.js/`npm` chỉ cần khi làm riêng Cloudflare Worker; Worker
hiện không nằm trong đường deploy Spring production. Tài khoản Supabase không
cần cho local.

## Setup local cho thành viên mới

### Cấu hình mặc định

Local dùng PostgreSQL trong Docker, không cần tạo `.env`. Các giá trị mặc định
được khai báo trong `compose.yaml` và `application-docker.yml`:

| Biến | Chạy backend trên host | Chạy app trong Compose |
| --- | --- | --- |
| `PHONGHUB_DB_HOST` | `localhost` | `db` |
| `PHONGHUB_DB_PORT` | `55432` | `5432` |
| `PHONGHUB_DB_NAME` | `phonghub` | `phonghub` |
| `PHONGHUB_DB_USER` | `phonghub` | `phonghub` |
| `PHONGHUB_DB_PASSWORD` | `phonghub_local_only` | `phonghub_local_only` |
| `PHONGHUB_REDIS_HOST` | `localhost` | `redis` |
| `PHONGHUB_REDIS_PORT` | `16379` | `6379` |

Không copy `.env.example` thành `.env` để chạy local. File đó là template cho
staging/production và chứa cấu hình kết nối Supabase; secret thật không được
đưa vào Git.

## Chạy local với Docker

### Chạy toàn bộ stack bằng Docker Compose

Lệnh dưới đây khởi động PostgreSQL, chạy Flyway migration, nạp fixture local và
khởi động backend:

```bash
docker compose up --build
```

Mở giao diện tại <http://localhost:8080/>. Health check:

```http
GET http://localhost:8080/health
```

Khi khởi động profile `docker`, Flyway tự chạy migration schema và fixture
local. Database được lưu trong Docker volume `phonghub_pgdata`.

### Chạy database bằng Docker, backend bằng Maven

Nếu cần debug backend trực tiếp trên máy:

```bash
docker compose up -d db redis
```

PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE = "docker"
.\mvnw.cmd spring-boot:run
```

Profile `docker` kết nối tới `localhost:55432` theo mặc định. Nếu thay đổi thông
tin database, dùng các biến `PHONGHUB_DB_*`:

```text
PHONGHUB_DB_HOST
PHONGHUB_DB_PORT
PHONGHUB_DB_NAME
PHONGHUB_DB_USER
PHONGHUB_DB_PASSWORD
```

Trên Linux/macOS, cấp quyền thực thi cho Maven Wrapper nếu cần:

```bash
chmod +x mvnw
```

Có thể chạy project bằng IntelliJ IDEA/Eclipse/VS Code với profile `docker`;
entry point là `com.phonghub.PhongHubApplication`.

### Tài khoản và dữ liệu demo local

Profile `docker` có sẵn dữ liệu fixture cho các actor sau:

| Role | Username | Email |
| --- | --- | --- |
| `ADMIN` | `admin` | `admin@phonghub.local` |
| `OWNER` | `owner1` | `owner1@phonghub.local` |
| `OWNER` | `owner2` | `owner2@phonghub.local` |
| `STAFF` | `staff1` | `staff1@phonghub.local` |
| `STAFF` | `staff2` | `staff2@phonghub.local` |
| `TECHNICIAN` | `tech1` | `tech1@phonghub.local` |
| `TENANT` | `tenant1` | `tenant1@phonghub.local` |

Local bật `LocalDemoAuthenticationAdapter` và cho phép chuyển actor demo. Có
thể chọn role trực tiếp ở dropdown trên giao diện; endpoint tương ứng là
`/switch-user?userId=<demo-user-id>`. Đây là cơ chế demo local, không gọi
Supabase Auth và không được dùng để đánh giá security của production.

Compose lưu database trong volume `phonghub_pgdata`, nên `docker compose down`
không xóa dữ liệu. Lệnh sau xóa cả volume và toàn bộ dữ liệu local, chỉ dùng
khi muốn tạo lại database từ đầu:

```bash
docker compose down -v
```

Mật khẩu mặc định trong Compose chỉ dành cho local. Không dùng nó cho
staging hoặc production.

## Profile và nguồn dữ liệu

### `docker`

- PostgreSQL và Redis (session store) chạy trong Docker.
- Flyway chạy `db/migration` và fixture tại `db/seed/docker`.
- Dữ liệu fixture được lưu trong volume Docker.
- Local demo actor switching vẫn được bật để kiểm thử các role nhanh; đây là
  cơ chế xác thực local, không dùng cho production.

### `test`

- Dùng repository in-memory và fixture Java cho test suite hiện tại.
- Profile này chỉ dành cho automated test, không phải nguồn dữ liệu của local
  runtime hay môi trường deploy.

### `prod`

- Kết nối tới PostgreSQL được khai báo qua environment variable.
- Chỉ chạy migration trong `db/migration`; không chạy fixture Docker.
- Xác thực dùng JWT từ Supabase.
- UI Thymeleaf dùng session cookie bảo mật; REST API dùng Bearer JWT.
- Session UI lưu trong Redis qua `REDIS_URL` (Render Key Value `phonghub-sessions`),
  nên phiên đăng nhập không mất khi service restart hoặc redeploy.
- `SUPABASE_SERVICE_ROLE_KEY` chỉ được dùng ở backend và không được đưa vào
  frontend hoặc commit vào Git.
- Chỉ kích hoạt `prod`; không bật đồng thời với profile `docker`.

> `origin/staging` là Git branch, không phải database. Git branch không chứa
> các row dữ liệu. Muốn môi trường deploy dùng dữ liệu thật, backend phải trỏ
> tới database Supabase của môi trường đó bằng secret do môi trường quản lý.

## Database migration và fixture

Migration dùng chung:

- `V1__init_base_schema.sql`: tạo schema nền.
- `V2__align_roles_remove_password_hash_and_add_active_contract_constraint.sql`:
  đồng bộ role, tách password sang Supabase Auth và thêm invariant contract.
- `V3__align_phone_and_add_audit_log.sql`: điều chỉnh phone và tạo audit log.
- `V6__unique_lower_email_and_username.sql`: email và username duy nhất không
  phân biệt hoa thường.
- `V7__add_owner_role_and_property_approval_status.sql`: thêm role OWNER và
  trạng thái duyệt property.
- `V9__standardize_property_verification_status.sql`: chuẩn hóa trạng thái xác
  minh property.
- `V10__add_sepay_transactions_table.sql`: bảng giao dịch SePay.
- `V11__link_invoices_to_sepay_payments.sql`: liên kết hóa đơn với thanh toán
  SePay.
- `V12__maintenance_repair_fee_invoices.sql`: hóa đơn phí sửa chữa bảo trì.

Số V4, V5, V8 thuộc fixture local nên migration dùng chung có khoảng trống.
Các migration dùng chung tiếp tục từ V13; SePay Sandbox được bổ sung tại V48.

Fixture local (chỉ nằm trong Flyway location của profile `docker`,
`db/seed/docker`):

- `V4__local_demo_fixture.sql`
- `V5__local_vietnamese_fixture_text.sql`
- `V8__local_demo_owner_fixture.sql`
- Profile `prod` không quét thư mục fixture này.
- Fixture hiện mô phỏng cùng kịch bản MVP đang dùng trong test; đây không phải
  bản export đã xác minh từ database Supabase remote.

## SePay Sandbox checkout và IPN

Đặt `SEPAY_SANDBOX_CHECKOUT_ENABLED=true`, `SEPAY_MERCHANT_ID`,
`SEPAY_SECRET_KEY` và `SEPAY_PUBLIC_BASE_URL` trong môi trường thử nghiệm. Cấu hình
IPN tại SePay Sandbox trỏ tới
`<SEPAY_PUBLIC_BASE_URL>/api/v1/payments/sepay/ipn` và dùng cùng `SEPAY_SECRET_KEY`.

Người thuê bấm `Thanh toán` để mở checkout Sandbox. Hóa đơn chỉ được ghi nhận đã
trả sau khi PhongHub nhận IPN hợp lệ, đúng mã đơn và đúng số tiền; URL quay về từ
SePay tự nó không xác nhận thanh toán. Sandbox không chuyển tiền thật.

Khi tắt Sandbox checkout, trang hóa đơn dùng QR chuyển khoản đã cấu hình. Giao
dịch chuyển khoản thật tiếp tục được đối soát qua
`/api/v1/payments/sepay/webhook` với `SEPAY_WEBHOOK_API_KEY`.

## REST API chính

- `GET /health`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `POST /api/auth/change-password`
- `GET /login`, `POST /login`
- `GET /admin/users`, `POST /admin/users`
- `POST /admin/users/{userId}/reset-password` (tenant)
- `GET /api/properties`
- `GET /api/properties/{propertyId}/rooms`
- `GET /api/properties/{propertyId}/contracts`
- `GET /api/properties/{propertyId}/maintenance`
- `POST /api/properties`
- `POST /api/contracts`
- `POST /api/contracts/{contractId}/activate`
- `POST /api/contracts/{contractId}/terminate`
- `POST /api/maintenance`
- `POST /api/maintenance/{ticketId}/accept`
- `POST /api/maintenance/{ticketId}/resolve`
- `GET /api/maintenance/{id}`
- `GET /api/contracts/{id}`, `GET /api/contracts/my-active`
- `POST /api/contracts/{id}/occupants`
- `POST /api/contracts/{contractId}/occupants/{tenantId}/check-out`
- `GET /api/properties/{id}`
- `POST /api/properties/{propertyId}/rooms`
- `GET`, `PUT /api/rooms/{id}`; `PUT /api/rooms/{id}/status`
- `GET`, `PUT /api/tenants/{id}`
- `POST /api/admin/properties/{propertyId}/verify`
- `POST /api/admin/properties/{propertyId}/reject`
- `POST /api/v1/payments/sepay/webhook`
- `GET /api/v1/payments/transactions`, `GET /api/v1/payments/transactions/{id}`
- `GET /api/chat/rooms` (danh sách phòng chat và số tin chưa đọc)
- `GET /api/chat/rooms/{roomId}/messages` (lịch sử tin nhắn, kèm header `X-Peer-Last-Read-At`)
- `POST /api/chat/rooms/{roomId}/messages` (gửi tin nhắn văn bản / icon)
- `POST /api/chat/rooms/{roomId}/media` (gửi ảnh hoặc video đính kèm caption, tối đa 25MB)
- `POST /api/chat/rooms/{roomId}/read` (đánh dấu đã đọc phòng chat)
- `GET /api/chat/rooms/{roomId}/seen` (lấy thời gian đã xem của đối phương)
- `GET /api/chat/stream` (SSE server-sent events realtime cho tin nhắn mới và trạng thái đã xem)
- `GET /api/chat/contacts` (danh sách người dùng để bắt đầu trò chuyện)
- `POST /api/chat/rooms/direct` (tạo hoặc mở phòng chat 1-1)

Chi tiết request/response được định nghĩa trong controller và DTO tương ứng.
Frontend nên dùng API contract đó thay vì phụ thuộc vào fixture local.

### Hệ thống Chat Real-time & Lưu trữ Media

Hệ thống trò chuyện được thiết kế theo chuẩn Hexagonal Architecture (Ports & Adapters):
- **Inbound Port:** `ChatRoomUseCase`, `ChatMessageUseCase`
- **Outbound Ports:**
  - `ChatMessageRepositoryPort`, `ChatRoomRepositoryPort` (PostgreSQL / In-Memory)
  - `ChatEventPublisherPort` (SSE Broadcast)
  - `MediaStoragePort` (Lưu trữ và phân phối hình ảnh/video qua CDN)
- **Cơ chế lưu trữ Media (Images & Videos):**
  - **Môi trường Production:** `SupabaseMediaStorageAdapter` tải tệp lên bucket Supabase Storage (`chat-media`) qua REST API và trả về URL CDN công khai (`/storage/v1/object/public/...`) giúp tối ưu tốc độ phân phối và bộ nhớ đệm.
  - **Môi trường Local / Docker:** Tự động fallback sang `LocalMediaStorageAdapter` lưu trữ tại thư mục cục bộ `uploads/chat-media/` và phục vụ qua Spring Web MVC Resource Handler (`/uploads/**`), đảm bảo chạy mượt mà ngay cả khi ngắt mạng hoặc chưa cấu hình Supabase credentials.
  - **Giới hạn & Định dạng:** Hỗ trợ kích thước tệp tối đa 25MB. Hỗ trợ ảnh (`.jpg, .png, .webp, .gif`) và video (`.mp4, .webm, .mov`). Hiển thị trực quan với thumbnail phóng to và trình phát video HTML5 tích hợp ngay trong bong bóng chat.

## Cấu hình production

Runbook Render/Supabase/Cloudflare và quy trình tạo Admin đầu tiên: [docs/production-deployment.md](docs/production-deployment.md).

Kích hoạt profile:

```bash
SPRING_PROFILES_ACTIVE=prod
```

Các biến bắt buộc gồm:

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
SUPABASE_URL
SUPABASE_ANON_KEY
SUPABASE_SERVICE_ROLE_KEY
SUPABASE_JWKS_URI
SUPABASE_JWT_ISSUER
SUPABASE_JWT_AUDIENCE
```

Biến cho thanh toán SePay (mặc định rỗng, cần đặt khi dùng thanh toán thật):

```text
SEPAY_WEBHOOK_API_KEY
SEPAY_BANK_CODE
SEPAY_BANK_ACCOUNT_NUMBER
SEPAY_BANK_ACCOUNT_NAME
```

Để thử checkout Sandbox, đặt `SEPAY_SANDBOX_CHECKOUT_ENABLED=true`,
`SEPAY_MERCHANT_ID`, `SEPAY_SECRET_KEY` và `SEPAY_PUBLIC_BASE_URL`.
`SEPAY_SECRET_KEY` dùng cho checkout/IPN Sandbox; `SEPAY_WEBHOOK_API_KEY` dùng
cho webhook chuyển khoản thật.

Không đặt giá trị thật trong `application-*.yml`, `.env` đã commit hoặc bundle
frontend. Dùng secret/environment management của môi trường deploy.

## Quy ước branch

Đây là quy ước vận hành của repository, không thay thế branch protection hoặc
chính sách CI của remote:

- `feat/*`: phát triển theo feature hoặc task.
- `staging`: branch tích hợp và kiểm thử. Pull request của feature/task đặt
  `staging` làm base; mọi thay đổi cần được build và kiểm tra trước khi dùng
  làm ứng viên tích hợp.
- `main`: branch release/production. Chỉ promote từ `staging` sau khi đã review,
  kiểm tra migration, cấu hình và runtime target.

Không coi việc build local hoặc push lên `staging` là bằng chứng deploy production
thành công.

## Kiểm thử và đóng gói

Chạy test suite:

PowerShell:

```powershell
.\mvnw.cmd test
```

Linux/macOS:

```bash
./mvnw test
```

Đóng gói không chạy lại test:

```bash
./mvnw package -DskipTests -B
```

Build Docker image:

```bash
docker build -t phonghub:local .
```

## Giới hạn và blocker còn lại

- URL backend staging đang chạy, CORS và kiểm thử tích hợp với frontend chưa
  được xác minh.
- Kết nối Supabase thật và quyền database cần được kiểm tra tại môi trường
  tương ứng; không dùng fixture Docker để kết luận dữ liệu remote đúng.
- Worker/Cloudflare container config hiện có lỗi TypeScript và là path riêng,
  không thuộc local PostgreSQL backend flow.
- Việc map `PORT`, profile `prod`, health check, secret environment và domain
  Cloudflare trên platform deploy cần được xác minh riêng trước khi promote lên
  `main`.

## Troubleshooting local

- Nếu cổng PostgreSQL `55432` bị chiếm, đặt `PHONGHUB_DB_PORT` sang cổng khác
  trước khi chạy Compose. Khi chạy backend trên host, dùng cùng giá trị cho
  profile `docker`.
- Nếu cổng ứng dụng `8080` bị chiếm khi chạy backend trên host, đổi `PORT`, ví dụ
  `$env:PORT = "8081"` trên PowerShell, rồi mở cổng mới.
- `docker compose down` chỉ dừng stack và giữ dữ liệu. Muốn tạo lại database
  local từ đầu, dùng `docker compose down -v` rồi chạy lại; lệnh này xóa volume
  `phonghub_pgdata`.
- Không bật profile `prod` để chạy local. Profile đó yêu cầu đầy đủ biến
  Supabase/JDBC và dùng authentication production.

## Private storage và mã hóa dữ liệu tenant

- Tạo bucket Supabase Storage tên `private-files` (hoặc đặt `SUPABASE_STORAGE_BUCKET`) và giữ bucket ở chế độ private.
- File upload chỉ nhận JPEG, PNG, WebP và PDF; giới hạn mặc định 10 MiB. Ứng dụng chỉ trả signed URL tối đa 10 phút.
- Tạo riêng hai khóa 32 byte bằng `openssl rand -base64 32`, rồi đặt `TENANT_DATA_ENCRYPTION_KEY` và `TENANT_DATA_HMAC_KEY`.
- Đặt `TENANT_DATA_KEY_ID` (ví dụ `v1`). Không đổi hoặc xóa khóa đang dùng trước khi re-encrypt dữ liệu sang key id mới.
- Migration `V14` mã hóa CCCD cũ bằng AES-256-GCM, tạo HMAC để kiểm tra trùng, rồi xóa cột plaintext. Thiếu key sẽ làm migration dừng an toàn.
- Tệp vượt giới hạn trả `400`. Ảnh JPEG/PNG được mã hóa lại và WebP bị bỏ EXIF/XMP để xóa dữ liệu vị trí; PDF được lưu nguyên, không xóa metadata.
- Endpoint: `POST /api/properties/{propertyId}/files`, `GET .../files/signed-url`, `DELETE .../files` (chi tiết trong `docs/api-spec.yaml`).

## Tài sản trong phòng

- Migration `V15` mở rộng `room_assets` (mã tài sản duy nhất, trạng thái GOOD/DAMAGED/LOST/RETIRED, xóa mềm, cột kiểm toán `created_by/updated_at/updated_by`) và thêm `room_asset_events` để lưu lịch sử hư hỏng, sửa chữa, thay mới kèm chi phí.
- Owner và staff của nhà trọ thêm, sửa, xóa mềm, thêm hàng loạt từ mẫu `BASIC` (5 món) hoặc `FULL` (9 món) và ghi nhận sự kiện. Người thuê của phòng chỉ xem. Sự kiện có thể gắn phiếu bảo trì nhưng phiếu phải thuộc cùng phòng.
- Ảnh tài sản có thể là liên kết ngoài hoặc tệp tải lên kho riêng tư; tệp chỉ được xem qua link ký có hạn.
- Mã QR được sinh khi gọi (`GET /api/assets/{assetId}/qr` hoặc nút "Mã QR" ở trang phòng) và không được lưu; nội dung QR là đường dẫn trang phòng nên người quét phải đăng nhập.
- Áp dụng mẫu hàng loạt hai lần sẽ tạo trùng tài sản; kiểm tra danh sách phòng trước khi áp dụng.

## Hồ sơ giấy tờ người thuê (CCCD, GPLX)

- Migration `V16` tạo `tenant_documents`: mỗi người thuê có tối đa một dòng cho mỗi loại giấy tờ. Số CCCD vẫn ở bảng `tenants` (đã mã hóa ở `V14`); số GPLX được mã hóa AES-256-GCM kèm HMAC để chặn trùng giữa các người thuê.
- Ảnh giấy tờ (jpeg/png/webp) được kiểm tra bằng magic bytes, xóa metadata, **mã hóa AES-256-GCM rồi mới lưu** vào kho riêng tư (phần mở rộng `.enc`). Dùng chung khóa `TENANT_DATA_*` với số CCCD, nên mất khóa là mất khả năng đọc ảnh; hãy sao lưu khóa an toàn và chỉ xoay khóa sau khi mã hóa lại dữ liệu.
- Quyền: admin, owner/staff của nhà trọ và chính người thuê (phải có hợp đồng tại nhà trọ đó) được xem và tải lên; chỉ owner/staff/admin được xóa. Kỹ thuật viên và owner nhà khác nhận 403.
- Xem thường chỉ trả loại giấy tờ và số che (`****1234`), không trả ảnh. Xem đầy đủ (`POST .../documents/{type}/reveal`, hoặc nút "Xem đầy đủ" ở trang `/properties/{propertyId}/tenants/{tenantId}/documents`) yêu cầu nhập lại mật khẩu của chính người xem. Sai mật khẩu trả 403 và được ghi nhật ký; 5 lần sai trong 10 phút trả 429.
- Mỗi lần xem (danh sách, một giấy tờ, xem đầy đủ, xem thất bại, tải lên, xóa) ghi đúng một sự kiện audit; nhật ký không chứa số giấy tờ hay mật khẩu. Phản hồi xem đầy đủ có `Cache-Control: no-store`.
- Giới hạn đã biết: bộ đếm lần nhập sai nằm trong bộ nhớ của từng instance (sẽ được thay bằng rate limit chung ở #77); xác nhận mật khẩu dùng đăng nhập của nhà cung cấp danh tính nên mỗi lần kiểm tra tạo thêm một phiên đăng nhập phía nhà cung cấp.

## Mã hóa thông tin cá nhân khi lưu

- Migration Java `V17` mã hóa các cột sau bằng AES-256-GCM (định dạng `enc1.<key id>.<nonce>.<ciphertext>`, mỗi cột có ngữ cảnh riêng nên không thể dán ciphertext sang cột khác): `users` (email, username, họ tên, số điện thoại), `tenants` (họ tên, số điện thoại, email, địa chỉ thường trú), `sepay_transactions` (số tài khoản, tài khoản phụ, nội dung, mô tả) và `audit_logs.details`.
- Email, username và số điện thoại của `users` có cột chỉ mục mù `*_hmac` (HMAC-SHA256 của giá trị đã chuẩn hóa: bỏ khoảng trắng, chữ thường, bỏ ký tự phân tách số điện thoại). Đăng nhập, tra cứu và ràng buộc duy nhất dùng chỉ mục này nên vẫn không phân biệt hoa thường; các ràng buộc duy nhất trên dữ liệu thường đã bị gỡ. Số điện thoại tenant có `phone_hmac` để tìm khớp chính xác.
- Họ tên và địa chỉ không còn tìm hoặc sắp xếp được trong SQL; việc lọc phải làm trong ứng dụng. Tìm theo một phần số điện thoại hoặc tên không còn được hỗ trợ ở tầng dữ liệu.
- Nhật ký audit chỉ ghi id, vai trò, tên các trường đã đổi và số đã che; không ghi họ tên, số điện thoại, email, username hay địa chỉ. Thông báo trùng CCCD không còn nêu tên người đang giữ số đó.
- Dùng chung khóa `TENANT_DATA_*` với CCCD. **Trước khi deploy phiên bản có `V17`:** sao lưu khóa ở nơi độc lập với DB, chạy `pg_dump` của database, và chắc chắn cả ba biến `TENANT_DATA_*` đã đặt trên môi trường chạy migration. Migration không đảo ngược được nếu mất khóa. Muốn xoay khóa cần tác vụ mã hóa lại dữ liệu (chưa có).
- Supabase Auth vẫn lưu email của tài khoản ở hệ thống ngoài, không nằm trong phạm vi mã hóa này. `properties.address` là địa chỉ kinh doanh nên giữ nguyên dạng thường.

## Biên bản check-in

- Migration `V18` mở rộng `handovers` (trạng thái, mã băm điều khoản, bản chụp nội quy, người duyệt, cột kiểm toán), đổi ràng buộc duy nhất thành `(contract_id, type)` để mỗi hợp đồng chỉ có một biên bản check-in và còn chỗ cho check-out (#63), và thêm `handover_photos`, `handover_items`, `property_house_rules`. Dòng `handovers` có từ trước V18 được giữ ở trạng thái `APPROVED`.
- **Thứ tự nghiệp vụ: kích hoạt hợp đồng trước, check-in sau.** Biên bản chỉ tạo được cho hợp đồng `ACTIVE`; duyệt biên bản không đổi trạng thái hợp đồng hay phòng, vì hai thứ đó đã đổi lúc kích hoạt.
- Luồng: `DRAFT` (owner/staff nhập chỉ số điện nước, tình trạng phòng, chìa khóa, ảnh) -> `PENDING_TENANT` (đã gửi; hệ thống chụp danh sách tài sản của phòng, nội quy và mã băm điều khoản) -> `PENDING_APPROVAL` (người thuê chính xác nhận, cần đã tải CCCD) -> `APPROVED` hoặc `REJECTED` (từ chối phải có lý do; owner/staff mở lại về `DRAFT` để sửa và gửi lại, vẫn là một biên bản).
- Gửi biên bản cần ít nhất một ảnh phòng, một ảnh đồng hồ điện và một ảnh đồng hồ nước. Chỉ sửa nội dung và ảnh khi ở `DRAFT`. Duyệt khóa dòng trong cơ sở dữ liệu nên hai lần duyệt đồng thời chỉ một lần thành công, lần còn lại trả `409`; lỗi giữa chừng khi duyệt hoàn tác toàn bộ.
- Nội quy chung lưu ở `property_house_rules` (`PUT /api/properties/{id}/house-rules` hoặc ô nhập ở trang biên bản); mỗi lần sửa tăng phiên bản, biên bản đã gửi giữ bản chụp và mã băm cũ.
- Quyền: tạo, sửa, gửi, duyệt, từ chối, mở lại thuộc admin và owner/staff của nhà trọ; chỉ người thuê chính xác nhận; người thuê của hợp đồng được xem. Kỹ thuật viên, owner nhà khác và người thuê khác nhận `403`. Mỗi bước ghi một sự kiện audit không chứa dữ liệu cá nhân.
- Ảnh bàn giao (phòng, đồng hồ) không phải dữ liệu cá nhân nên được làm sạch EXIF và lưu ở kho riêng tư, xem qua link ký có hạn; ảnh giấy tờ của người thuê vẫn được mã hóa theo mục "Hồ sơ giấy tờ".

## Biên bản check-out

- Migration `V19` thêm `handovers.final_deduction` và `handover_items.deduction_amount`; check-out dùng chung bảng và luồng trạng thái với check-in (`type = CHECK_OUT`), nên một hợp đồng có tối đa một check-in và một check-out.
- Chỉ lập check-out cho hợp đồng `ACTIVE` đã có biên bản check-in được duyệt. Danh sách tài sản khởi tạo từ bản chụp check-in; nhân viên cập nhật tình trạng lúc trả (`GOOD`, `DAMAGED`, `LOST`) và khoản khấu trừ đề xuất cho từng tài sản (tài sản còn tốt không được đề xuất khấu trừ). Chỉ số điện nước nhập là chỉ số cuối kỳ; cần đủ ảnh phòng và ảnh hai đồng hồ trước khi gửi.
- **Duyệt check-out chỉ dành cho owner hoặc admin** (owner chốt khoản khấu trừ, mặc định bằng tổng đề xuất). Trong một transaction: biên bản `APPROVED`, hợp đồng `TERMINATED`, phòng về `AVAILABLE` hoặc `MAINTENANCE` nếu có tài sản hỏng hoặc mất. Nếu bất kỳ bước nào lỗi (kể cả ghi audit) thì hoàn tác toàn bộ; đã có test trên PostgreSQL thật.
- API `/api/contracts/{id}/check-out...` và trang `/contracts/{id}/check-out`. Nút kết thúc hợp đồng trực tiếp (`/terminate`) vẫn còn cho các trường hợp không có biên bản.
- Chưa làm: tính tiền cọc và quyết toán (ngoài phạm vi issue); so sánh check-in/check-out và xuất PDF thuộc #72.

## Sửa thông tin nhà trọ

- `PATCH /api/properties/{id}` và nút "Sửa thông tin" ở trang nhà trọ, dành cho owner của nhà trọ và admin; staff, kỹ thuật viên, owner nhà khác và người thuê nhận `403`.
- **Duyệt lại:** tên và địa chỉ là thông tin định danh. Owner đổi một trong hai trên nhà trọ đã duyệt thì nhà trọ quay về `PENDING`; trong lúc chờ duyệt không tạo được phòng hoặc hợp đồng mới (hợp đồng đang hiệu lực không bị ảnh hưởng). Nhà trọ bị từ chối (`REJECTED`) được gửi duyệt lại khi owner sửa và lý do từ chối bị xóa. Sửa mô tả hoặc tổng số phòng không cần duyệt lại. Admin sửa không đổi trạng thái.
- Audit `PROPERTY_UPDATED` chỉ ghi tên các trường đã đổi và trạng thái trước/sau, không ghi giá trị. Gửi dữ liệu không đổi không tạo audit.
- Chưa làm: kiểm tra tổng số phòng không nhỏ hơn số phòng đang có.

## Chỉ số điện nước theo kỳ

- `GET/POST /api/rooms/{id}/meter-readings`, `POST|GET /api/meter-readings/{id}/photo`, `GET/POST /api/properties/{id}/service-rates` và `PATCH /api/service-rates/{id}` (migration V20). Ghi chỉ số dành cho owner/staff của nhà trọ; xem cho cả kỹ thuật viên và tenant đang ở phòng; người ngoài nhà trọ nhận `403`.
- **Kỳ = tháng của ngày ghi**; mỗi phòng/loại (điện, nước)/kỳ chỉ một bản ghi (unique ở DB). Trùng kỳ hoặc ghi kỳ cũ hơn kỳ gần nhất nhận `409`; không ghi ngày ở tương lai.
- **Chỉ số kỳ trước** lấy từ bản ghi gần nhất, nếu chưa có thì từ biên bản check-in đã duyệt; nếu cả hai đều chưa có thì phải gửi `baselineValue`. Không cho nhập tay đè lên giá trị đã suy ra.
- **Tiêu thụ = mới - cũ.** Số mới nhỏ hơn số cũ bị chặn (`400`) trừ khi khai báo thay đồng hồ (`replacement`: chỉ số cuối đồng hồ cũ, chỉ số bắt đầu đồng hồ mới, ghi chú bắt buộc); khi đó tiêu thụ = (cuối đồng hồ cũ - kỳ trước) + (mới - bắt đầu đồng hồ mới).
- Ảnh đồng hồ (jpeg/png/webp) lưu private như các file khác, xem qua signed URL hết hạn sau 10 phút. Đơn giá dịch vụ cấu hình theo nhà trọ, chỉ owner của nhà trọ hoặc admin tạo/sửa, tên dịch vụ không trùng trong cùng nhà trọ. Audit: `METER_READING_RECORDED`, `METER_PHOTO_ATTACHED`, `SERVICE_RATE_CREATED/UPDATED`.
- Chưa làm: màn hình nhập chỉ số (mới có API), đưa tiền điện nước vào hóa đơn (#66), OCR đọc ảnh (#67).

## Điện, nước và dịch vụ trên hóa đơn tháng

- Migration V21 thêm `reading_id`, `rate_id` vào `invoice_items` để biết mỗi dòng được tính từ chỉ số và đơn giá nào. `invoices.utility_amount` giờ ghi tổng các dòng này, `other_amount` là phần còn lại (phí sửa chữa).
- **Điện/nước** tính theo chỉ số cùng kỳ với hóa đơn (kỳ ghi chỉ số = tháng của ngày ghi): tiêu thụ x đơn giá, làm tròn đến đồng; mỗi đơn giá `PER_KWH` / `PER_M3` của nhà trọ là một dòng. Tiêu thụ bằng 0 hoặc đơn giá 0 thì không có dòng. Chỉ số gắn với hợp đồng khác của cùng phòng (khách trước) bị bỏ qua.
- **Dịch vụ cố định** (`PER_ROOM_FLAT`, `PER_PERSON` x số người đang ở, tối thiểu 1) chỉ tính lúc phát hành hóa đơn, nên đơn giá thêm sau đó không làm đổi hóa đơn người thuê đã nhận.
- **Thiếu chỉ số không chặn hóa đơn tiền thuê.** Hóa đơn vẫn phát hành chỉ với tiền thuê; trang hợp đồng và trang thanh toán hiện cảnh báo (chưa có chỉ số điện/nước kỳ này, chưa cấu hình đơn giá, hoặc chỉ số ghi sau khi hóa đơn đã có thanh toán). Nhà trọ không cấu hình đơn giá nước thì không bị cảnh báo thiếu nước.
- **Scheduler** (mỗi giờ và lúc khởi động) thêm dòng điện/nước cho hóa đơn tháng hiện tại và tháng trước khi chỉ số được ghi sau lúc phát hành. Chỉ thêm vào hóa đơn chưa ai trả đồng nào (đã trả một phần hoặc đã trả đủ thì giữ nguyên để không lệch số tiền người thuê đã chuyển); mỗi cặp chỉ số/đơn giá chỉ tính một lần (unique ở DB), dòng được khóa theo hóa đơn nên không ghi đè khoản webhook vừa cộng.
- Chưa làm: sửa chỉ số đã ghi rồi tính lại hóa đơn, xử lý thanh toán vượt số tiền sau khi tổng đổi (#81).

## Thanh toán dư và giao dịch cần xem xét

- Migration V22 thêm vào `sepay_transactions` ba cột: `applied_amount` (đã cộng vào hóa đơn), `excess_amount` (phần dư chưa ghi nhận) và `review_reason` (khác NULL nghĩa là cần xem xét). Giao dịch cũ đã khớp hóa đơn được coi là đã cộng đủ.
- Khi webhook về, số tiền được chia trước khi cộng: chỉ cộng tối đa **số còn nợ** của hóa đơn (hóa đơn đã khóa dòng nên số còn nợ không đổi giữa lúc tính và lúc cộng); phần còn lại là phần dư. `Invoice.applyPayment` giờ từ chối số tiền lớn hơn số còn nợ, không còn cộng quá tổng.
- Ba lý do cần xem xét: `OVERPAID` (chuyển dư so với số còn nợ), `INVOICE_NOT_PAYABLE` (hóa đơn đã trả đủ, đã hủy hoặc đã gộp vào hóa đơn tháng nên không nhận thêm; cả số tiền là phần dư) và `UNMATCHED` (không khớp hóa đơn nào theo nội dung chuyển khoản). Trả thiếu chỉ làm hóa đơn `PARTIALLY_PAID`, không cần xem xét.
- Thông báo cho owner (hoặc admin nếu không khớp được hợp đồng) nêu rõ số tiền dư và việc cần làm. Admin xem được `appliedAmount`, `excessAmount`, `reviewReason` ở `GET /api/v1/payments/transactions`.
- Webhook gửi lại cùng `sepay_id` vẫn được bỏ qua (không cộng và không ghi dư lần hai).
- Chưa làm: màn hình xử lý giao dịch cần xem xét (đánh dấu đã hoàn tiền/cấn trừ), tự cấn trừ phần dư vào kỳ sau, biên lai và đối soát (#82).
