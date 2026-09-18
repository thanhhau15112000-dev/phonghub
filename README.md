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
docker compose up -d db
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

- PostgreSQL chạy trong Docker.
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

Fixture local:

- `V4__local_demo_fixture.sql` chỉ nằm trong Flyway location của profile
  `docker`.
- Profile `prod` không quét thư mục fixture này.
- Fixture hiện mô phỏng cùng kịch bản MVP đang dùng trong test; đây không phải
  bản export đã xác minh từ database Supabase remote.

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

Chi tiết request/response được định nghĩa trong controller và DTO tương ứng.
Frontend nên dùng API contract đó thay vì phụ thuộc vào fixture local.

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
