#!/bin/sh
# Khôi phục bản sao lưu PhongHub (#331).
#
#   scripts/db-restore.sh <file .dump.gpg>
#
# Biến môi trường:
#   BACKUP_ENCRYPTION_PASSPHRASE  mật khẩu đã dùng khi sao lưu (bắt buộc)
#   RESTORE_DATABASE_URL          CSDL đích, nên là CSDL trống. Không đặt thì chạy chế độ diễn tập: dựng Postgres
#                                 tạm trong Docker, khôi phục vào đó, in số dòng các bảng chính rồi xóa container.
#   RESTORE_CONFIRM=yes           bắt buộc khi có RESTORE_DATABASE_URL, để không ghi nhầm vào CSDL đang chạy
#   PG_IMAGE                      mặc định postgres:17
set -eu

file=${1:?Cần file .dump.gpg}
[ -f "$file" ] || { echo "Không thấy $file" >&2; exit 1; }
: "${BACKUP_ENCRYPTION_PASSPHRASE:?Thiếu BACKUP_ENCRYPTION_PASSPHRASE}"
pg_image=${PG_IMAGE:-postgres:17}
export MSYS_NO_PATHCONV=1

# Chạy trong container: CSDL đích đã có schema public nên bỏ lệnh CREATE SCHEMA public của bản dump.
# Tham số kết nối lấy từ biến TARGET bên trong container (không hiện trên dòng lệnh của máy chạy).
restore_script='cat > /tmp/phonghub.dump
pg_restore --list /tmp/phonghub.dump | grep -v " SCHEMA - public " > /tmp/phonghub.list
exec pg_restore --use-list=/tmp/phonghub.list --no-owner --no-privileges --exit-on-error --dbname="$TARGET" /tmp/phonghub.dump'

plain=$(mktemp)
db=phonghub-restore-drill-$$
cleanup() { rm -f "$plain"; docker rm -f "$db" >/dev/null 2>&1 || true; }
trap cleanup EXIT

echo "== 1. Giải mã"
printf '%s' "$BACKUP_ENCRYPTION_PASSPHRASE" | gpg --batch --yes --quiet --pinentry-mode loopback \
    --passphrase-fd 0 --decrypt --output "$plain" "$file"

if [ -n "${RESTORE_DATABASE_URL:-}" ]; then
    [ "${RESTORE_CONFIRM:-}" = yes ] || { echo "Đặt RESTORE_CONFIRM=yes để xác nhận ghi vào RESTORE_DATABASE_URL" >&2; exit 1; }
    echo "== 2. Khôi phục vào CSDL đích"
    TARGET=$RESTORE_DATABASE_URL docker run --rm -i -e TARGET "$pg_image" sh -c "$restore_script" < "$plain"
    echo "Khôi phục xong. Kiểm tra app kết nối CSDL này trước khi chuyển người dùng sang."
    exit 0
fi

echo "== 2. Diễn tập: dựng Postgres tạm ($pg_image)"
docker run -d --name "$db" -e POSTGRES_PASSWORD=drill "$pg_image" >/dev/null
i=0
until docker exec "$db" pg_isready -U postgres >/dev/null 2>&1; do
    i=$((i + 1)); [ "$i" -le 60 ] || { echo "Postgres không sẵn sàng" >&2; exit 1; }
    sleep 1
done
sleep 2

echo "== 3. Khôi phục"
docker exec -i -e TARGET=postgresql://postgres@localhost/postgres "$db" sh -c "$restore_script" < "$plain"

echo "== 4. Số dòng các bảng chính"
for t in users properties rooms tenants contracts invoices payments maintenance_tickets flyway_schema_history; do
    n=$(docker exec "$db" psql -U postgres -d postgres -At -c "SELECT count(*) FROM public.$t" 2>/dev/null || echo "không có bảng")
    echo "$t: $n"
done
echo "Diễn tập khôi phục hoàn tất."
