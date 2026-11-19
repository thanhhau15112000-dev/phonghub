# Sao lưu và khôi phục CSDL (#331)

## Sao lưu tự động

Workflow `.github/workflows/db-backup.yml` chạy `scripts/db-backup.sh` mỗi ngày lúc 02:00 giờ Việt Nam
và chạy tay được ở tab **Actions → Sao lưu CSDL → Run workflow**.

1. `pg_dump` schema `public` (dữ liệu nghiệp vụ và lịch sử Flyway), dạng custom.
2. Kiểm bản dump đọc lại được và có dữ liệu.
3. Mã hóa AES256 bằng `gpg` với mật khẩu riêng, rồi lưu thành artifact, giữ 7 ngày.

Cần đặt hai secret ở **Settings → Secrets and variables → Actions**:

| Secret | Giá trị |
|---|---|
| `BACKUP_DATABASE_URL` | Chuỗi kết nối Postgres của Supabase (Project Settings → Database → Connection string, dạng `postgresql://...?sslmode=require`). Nên dùng tài khoản chỉ đọc. |
| `BACKUP_ENCRYPTION_PASSPHRASE` | Mật khẩu mã hóa. Giữ ở nơi khác ngoài GitHub; mất mật khẩu thì không giải mã được bản sao lưu. |

Thiếu secret thì workflow báo lỗi và dừng. Lịch chạy của GitHub chỉ áp dụng trên nhánh mặc định (`main`),
nên sao lưu tự động bắt đầu sau khi thay đổi này lên `main`.

Bản sao lưu **không** gồm tài khoản đăng nhập (schema `auth`) và tệp trong Supabase Storage. Hai phần này do
Supabase quản lý; nếu cần, tải tệp Storage định kỳ bằng Supabase CLI.

## Khôi phục

Tải artifact `phonghub-<thời gian>` ở trang chạy workflow, giải nén ra file `.dump.gpg`.

Diễn tập (không đụng CSDL thật, cần Docker và gpg):

```sh
BACKUP_ENCRYPTION_PASSPHRASE=... scripts/db-restore.sh phonghub-20261112T1900Z.dump.gpg
```

Script dựng Postgres tạm, khôi phục, in số dòng các bảng chính rồi xóa container. Nên diễn tập mỗi tháng.

Khôi phục thật vào một CSDL trống:

```sh
BACKUP_ENCRYPTION_PASSPHRASE=... \
RESTORE_DATABASE_URL='postgresql://...' RESTORE_CONFIRM=yes \
scripts/db-restore.sh phonghub-20261112T1900Z.dump.gpg
```

Sau khi khôi phục: trỏ `SPRING_DATASOURCE_URL` của app sang CSDL đó, khởi động app (Flyway sẽ kiểm lịch sử
migration) và đăng nhập thử từng vai trò trước khi mở lại cho người dùng.

## Sao lưu bằng tay

```sh
BACKUP_DATABASE_URL='postgresql://...' BACKUP_ENCRYPTION_PASSPHRASE=... \
scripts/db-backup.sh backup/phonghub-manual.dump.gpg
```

Chạy trước mỗi lần demo hoặc migration lớn.
