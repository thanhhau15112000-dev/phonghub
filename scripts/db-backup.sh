#!/bin/sh
# Sao lưu CSDL PhongHub (#331): pg_dump schema public dạng custom, kiểm đọc lại được, rồi mã hóa AES256 bằng gpg.
#
#   scripts/db-backup.sh <file đầu ra .dump.gpg>
#
# Biến môi trường (bắt buộc):
#   BACKUP_DATABASE_URL           chuỗi kết nối Postgres, vd. postgresql://user:pass@host:5432/postgres?sslmode=require
#   BACKUP_ENCRYPTION_PASSPHRASE  mật khẩu mã hóa bản sao lưu (giữ ở nơi khác, mất là không giải mã được)
# Tùy chọn:
#   PG_IMAGE  image có pg_dump cùng hoặc mới hơn phiên bản server (mặc định postgres:17)
#
# Cần Docker và gpg. Chuỗi kết nối và mật khẩu chỉ truyền qua biến môi trường, không ghi ra log.
# Chỉ sao lưu dữ liệu nghiệp vụ (schema public, gồm lịch sử Flyway). Tài khoản đăng nhập (schema auth) và tệp trong
# Supabase Storage do Supabase quản lý, không nằm trong bản này.
set -eu

out=${1:?Cần đường dẫn file đầu ra, vd. backup/phonghub.dump.gpg}
: "${BACKUP_DATABASE_URL:?Thiếu BACKUP_DATABASE_URL}"
: "${BACKUP_ENCRYPTION_PASSPHRASE:?Thiếu BACKUP_ENCRYPTION_PASSPHRASE}"
pg_image=${PG_IMAGE:-postgres:17}
export MSYS_NO_PATHCONV=1

mkdir -p "$(dirname "$out")"
plain=$(mktemp)
trap 'rm -f "$plain"' EXIT

echo "== 1. pg_dump ($pg_image)"
# -e BACKUP_DATABASE_URL (không kèm giá trị) để Docker lấy từ môi trường, chuỗi kết nối không hiện trên dòng lệnh
docker run --rm -e BACKUP_DATABASE_URL "$pg_image" \
    sh -c 'exec pg_dump --format=custom --no-owner --no-privileges --schema=public --dbname="$BACKUP_DATABASE_URL"' \
    > "$plain"

echo "== 2. Kiểm bản dump đọc lại được"
tables=$(docker run --rm -i "$pg_image" pg_restore --list < "$plain" | grep -c ' TABLE DATA ' || true)
[ "$tables" -gt 0 ] || { echo "Bản dump không có dữ liệu bảng nào; dừng" >&2; exit 1; }
echo "Số bảng có dữ liệu: $tables"

echo "== 3. Mã hóa"
printf '%s' "$BACKUP_ENCRYPTION_PASSPHRASE" | gpg --batch --yes --quiet --pinentry-mode loopback \
    --passphrase-fd 0 --symmetric --cipher-algo AES256 --output "$out" "$plain"
size=$(wc -c < "$out" | tr -d ' ')
echo "Đã lưu $out ($size byte)"
