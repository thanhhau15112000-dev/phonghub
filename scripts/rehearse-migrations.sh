#!/bin/sh
# Diễn tập migration trên bản sao dữ liệu thật: restore file dump vào Postgres tạm trong Docker, chạy các migration
# còn thiếu từ src/main/resources/db/migration bằng Flyway CLI, rồi in tác động lên dữ liệu.
#
#   scripts/rehearse-migrations.sh <file dump pg_dump --format=custom>
#
# Biến môi trường:
#   PG_IMAGE   image Postgres tạm (mặc định postgres:17)
#   FLYWAY_IMAGE image Flyway CLI (mặc định flyway/flyway:11)
#   KEEP=1     giữ container và network để tự kiểm tra thêm (tên in ở cuối)
#
# Bám cấu hình production: out-of-order bật, vị trí db/migration. Migration viết bằng Java (V49) không chạy được trong
# Flyway CLI nên bị bỏ qua khi đối chiếu; nó đã nằm trong lịch sử của DB production.
# Cần Docker. Không kết nối tới DB production và không ghi gì ngoài container tạm.
set -eu

dump=${1:?Cần file dump (pg_dump --format=custom)}
[ -f "$dump" ] || { echo "Không thấy $dump" >&2; exit 1; }

pg_image=${PG_IMAGE:-postgres:17}
flyway_image=${FLYWAY_IMAGE:-flyway/flyway:11}
run_id=$$
net=phonghub-rehearsal-net-$run_id
db=phonghub-rehearsal-db-$run_id

# Đường dẫn cho Docker: Git Bash trên Windows cần dạng D:/... (pwd -W) và tắt việc MSYS tự đổi đường dẫn.
host_path() { (cd "$1" && { pwd -W 2>/dev/null || pwd; }); }
export MSYS_NO_PATHCONV=1

repo=$(cd "$(dirname "$0")/.." && pwd)
migrations=$(host_path "$repo/src/main/resources/db/migration")

cleanup() {
    if [ "${KEEP:-0}" = 1 ]; then
        echo "Giữ lại: container $db, network $net (xóa: docker rm -f $db; docker network rm $net)"
    else
        docker rm -f "$db" >/dev/null 2>&1 || true
        docker network rm "$net" >/dev/null 2>&1 || true
    fi
}
trap cleanup EXIT

psql_db() { docker exec -i "$db" psql -U postgres -d postgres -v ON_ERROR_STOP=1 -X "$@"; }

flyway() {
    docker run --rm --network "$net" -v "$migrations:/flyway/sql:ro" "$flyway_image" \
        -url="jdbc:postgresql://$db:5432/postgres" -user=postgres -password=rehearsal \
        -locations=filesystem:/flyway/sql -outOfOrder=true -connectRetries=10 "$@"
}

echo "== 1. Dựng Postgres tạm ($pg_image)"
docker network create "$net" >/dev/null
docker run -d --name "$db" --network "$net" -e POSTGRES_PASSWORD=rehearsal "$pg_image" >/dev/null
i=0
until docker exec "$db" pg_isready -U postgres >/dev/null 2>&1; do
    i=$((i + 1)); [ "$i" -le 60 ] || { echo "Postgres không sẵn sàng" >&2; exit 1; }
    sleep 1
done
sleep 2

echo "== 2. Restore dump"
docker cp "$dump" "$db:/tmp/rehearsal.dump"
docker exec "$db" pg_restore -U postgres -d postgres --clean --if-exists --no-owner --no-privileges /tmp/rehearsal.dump \
    || { echo "pg_restore báo lỗi ở trên; dừng để không diễn tập trên dữ liệu thiếu" >&2; exit 1; }

echo "== 3. Tác động dự kiến lên dữ liệu hiện có (trước khi migrate)"
report() {
    label=$1; sql=$2
    printf '%s: ' "$label"
    psql_db -At -c "$sql" 2>/dev/null || echo "(không truy vấn được, schema chưa có bảng/cột này)"
}
report "V52 hóa đơn RENT sẽ được gán chu kỳ theo tháng dương lịch" \
    "SELECT count(*) FROM invoices WHERE invoice_type = 'RENT'"
report "V53 chính sách phí trễ có grace_days < 7 sẽ bị nâng lên 7" \
    "SELECT count(*) FROM property_late_fee_policies WHERE grace_days < 7"
report "V55 phiếu đang giao kỹ thuật viên sẽ đặt lại assigned_at" \
    "SELECT count(*) FROM maintenance_tickets WHERE assigned_technician_id IS NOT NULL AND status IN ('ASSIGNED', 'IN_PROGRESS')"
report "V56 phiếu của người thuê đang giao/đang sửa sẽ coi như đã chốt nhánh và đã duyệt" \
    "SELECT count(*) FROM maintenance_tickets WHERE requested_by_tenant_id IS NOT NULL AND status IN ('ASSIGNED', 'IN_PROGRESS')"

echo "== 4. Lịch sử Flyway trước khi migrate"
flyway info | grep -E 'Schema version|Pending'

echo "== 5. Đối chiếu checksum migration đã áp dụng với mã nguồn"
flyway -ignoreMigrationPatterns='*:missing,*:pending' validate

echo "== 6. Chạy migration còn thiếu"
flyway -ignoreMigrationPatterns='*:missing' migrate

echo "== 7. Lịch sử Flyway sau khi migrate"
flyway -ignoreMigrationPatterns='*:missing' info | grep -E 'Schema version|Success|Failed|Pending' | tail -n 10

echo "== 8. Kiểm tra dữ liệu sau khi migrate"
report "grace_days nhỏ nhất (phải >= 7)" "SELECT min(grace_days) FROM property_late_fee_policies"
report "hóa đơn RENT thiếu ngày chu kỳ (phải = 0)" \
    "SELECT count(*) FROM invoices WHERE invoice_type = 'RENT' AND (cycle_start_date IS NULL OR cycle_end_date IS NULL)"
report "phiếu giao kỹ thuật viên thiếu assigned_at (phải = 0)" \
    "SELECT count(*) FROM maintenance_tickets WHERE assigned_technician_id IS NOT NULL AND status IN ('ASSIGNED', 'IN_PROGRESS') AND assigned_at IS NULL"
report "phiếu của người thuê đang giao/đang sửa chưa chốt nhánh (phải = 0)" \
    "SELECT count(*) FROM maintenance_tickets WHERE requested_by_tenant_id IS NOT NULL AND status IN ('ASSIGNED', 'IN_PROGRESS') AND branch_confirmed_at IS NULL"

echo "Diễn tập hoàn tất: migration chạy được trên bản sao dữ liệu."
