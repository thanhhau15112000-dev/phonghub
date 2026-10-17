#!/bin/sh
# Đo độ chính xác OCR chỉ số đồng hồ (#67) trên một thư mục ảnh, dùng đúng tham số của TesseractMeterOcrAdapter.
#
#   scripts/ocr-benchmark.sh <thư mục ảnh> [ngưỡng, mặc định 0.85]
#
# Thư mục có file expected.txt, mỗi dòng "<tên file> <chỉ số đúng>", ví dụ "dien-01.jpg 01250.5".
# Máy có lệnh tesseract thì chạy trực tiếp, không thì chạy trong container Alpine (cần Docker).
# Kết quả mỗi ảnh: AUTO_OK (tự điền, đúng), AUTO_WRONG (tự điền nhưng sai: lỗi nguy hiểm), MANUAL (dưới ngưỡng, nhập tay).
set -eu

dir=${1:?Cần thư mục ảnh có expected.txt}
threshold=${2:-0.85}
[ -f "$dir/expected.txt" ] || { echo "Không thấy $dir/expected.txt" >&2; exit 1; }

# Đường dẫn cho Docker: Git Bash trên Windows cần dạng D:/... (pwd -W) và tắt việc MSYS tự đổi đường dẫn.
host_path() { (cd "$1" && { pwd -W 2>/dev/null || pwd; }); }

if ! command -v tesseract >/dev/null 2>&1; then
    images=$(host_path "$dir")
    scripts=$(host_path "$(dirname "$0")")
    MSYS_NO_PATHCONV=1 exec docker run --rm -v "$images:/images" -v "$scripts:/scripts:ro" \
        eclipse-temurin:21-jre-alpine sh -c \
        "apk add --no-cache tesseract-ocr tesseract-ocr-data-eng >/dev/null 2>&1 && sh /scripts/ocr-benchmark.sh /images $threshold"
fi

run_ocr() {
    OMP_THREAD_LIMIT=1 tesseract "$1" stdout --psm 7 -c tessedit_char_whitelist=0123456789. tsv 2>/dev/null
}

total=0; ok=0; wrong=0; manual=0
while read -r file expected _rest; do
    [ -n "$file" ] || continue
    total=$((total + 1))
    # Giống TesseractMeterOcrAdapter.parseTsv: ghép các cụm mức 5, độ tin cậy là cụm thấp nhất.
    result=$(run_ocr "$dir/$file" | awk -F'\t' '$1 == 5 && $12 != "" && $11 >= 0 {
        text = text $12; if (min == "" || $11 < min) min = $11 }
        END { printf "%s %.3f", text, (min == "" ? 0 : min / 100) }')
    got=${result% *}; conf=${result#* }
    if ! printf '%s' "$got" | grep -Eq '^[0-9]+(\.[0-9]+)?$' || awk "BEGIN { exit !($conf < $threshold) }"; then
        status=MANUAL; manual=$((manual + 1))
    elif awk "BEGIN { exit !($got == $expected) }"; then
        status=AUTO_OK; ok=$((ok + 1))
    else
        status=AUTO_WRONG; wrong=$((wrong + 1))
    fi
    printf '%-24s đúng=%-10s đọc=%-10s tin_cậy=%s %s\n' "$file" "$expected" "${got:--}" "$conf" "$status"
done < "$dir/expected.txt"

echo "Tổng $total ảnh, ngưỡng $threshold: tự điền đúng $ok, tự điền SAI $wrong, nhập tay $manual"
