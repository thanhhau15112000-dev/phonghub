# PhongHub

PhongHub là hệ thống quản lý vận hành nhà trọ và căn hộ dịch vụ. Backend hiện
tập trung vào các nghiệp vụ property, room, contract và maintenance ticket,
được xây dựng bằng Spring Boot theo Hexagonal Architecture.

## Trạng thái hiện tại

- Backend MVP đã có REST API và giao diện Thymeleaf hiện tại.
- Local runtime mặc định dùng PostgreSQL chạy trong Docker.
- Dữ liệu local được tạo bằng Flyway fixture trong database Docker, không được
  tạo trực tiếp bởi Java `DataSeeder`.
- Profile `prod` dùng PostgreSQL bên ngoài, phù hợp với database của Supabase.
- React frontend theo Figma chưa nằm trong repository snapshot này; frontend
  có thể gọi backend qua API.
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

## Yêu cầu

- JDK 21
- Docker Desktop có Docker Compose
- Maven Wrapper có sẵn trong repository

Node.js và `npm` chỉ cần khi frontend React được thêm vào repository.

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
- `staging`: branch tích hợp và kiểm thử. Mọi thay đổi cần được build và kiểm
  tra trước khi dùng làm ứng viên tích hợp.
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

- React frontend theo Figma chưa có trong snapshot hiện tại.
- URL backend staging đang chạy, CORS và kiểm thử tích hợp với frontend chưa
  được xác minh.
- Kết nối Supabase thật và quyền database cần được kiểm tra tại môi trường
  tương ứng; không dùng fixture Docker để kết luận dữ liệu remote đúng.
- Worker/Cloudflare container config hiện có lỗi TypeScript và là path riêng,
  không thuộc local PostgreSQL backend flow.
- Việc map `PORT`, profile `prod` và health check trên platform deploy cần được
  xác minh riêng trước khi promote lên `main`.
