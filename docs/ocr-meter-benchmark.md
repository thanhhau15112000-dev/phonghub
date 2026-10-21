# OCR chỉ số đồng hồ (#67)

## Cách hoạt động

- Engine: lệnh `tesseract` 5.x, gọi từ `TesseractMeterOcrAdapter`. Ảnh được coi là một dòng số (`--psm 7`), chỉ nhận chữ số và dấu chấm.
- Độ tin cậy trả về là độ tin cậy **thấp nhất** trong các cụm Tesseract nhận ra. Chỉ cần một chữ số đọc không chắc là cả kết quả bị hạ xuống.
- Dưới ngưỡng `phonghub.ocr.min-confidence` (mặc định `0.85`) thì không điền sẵn, người dùng phải nhập tay.
- Người thuê gửi chỉ số kèm ảnh: số khai được so với OCR (`MATCH` / `MISMATCH` / `UNVERIFIED`), nhân viên duyệt và nhập giá trị cuối. Hệ thống lưu cả giá trị OCR lẫn giá trị đã xác nhận.
- Lỗi, quá thời gian (`TESSERACT_TIMEOUT_SECONDS`, mặc định 15) hoặc đang bận (`TESSERACT_MAX_CONCURRENT`, mặc định 1) thì coi như không đọc được, không chặn việc gửi chỉ số.

## Bật / tắt

| Môi trường | Mặc định | Ghi chú |
|---|---|---|
| Docker image (Render) | `METER_OCR_ENGINE=tesseract` | Image cài `tesseract-ocr` + dữ liệu `eng`, thêm khoảng 60 MB |
| Chạy local / test | `disabled` | Đặt `METER_OCR_ENGINE=tesseract` nếu máy có lệnh `tesseract` |

Chọn `tesseract` mà máy không có lệnh thì app ghi cảnh báo lúc khởi động và quay về nhập tay, không lỗi.

## So sánh công cụ

| Công cụ | Chi phí | Ưu | Nhược |
|---|---|---|---|
| **Tesseract** (đang dùng) | Miễn phí, chạy trong container | Không gửi ảnh ra ngoài, không cần khóa API | Kém với ảnh chụp cả mặt đồng hồ, chói, nghiêng nhiều; cần ảnh cắt gần ô số |
| Google Cloud Vision (Text Detection) | Có hạn mức miễn phí hằng tháng, sau đó tính theo mỗi 1.000 ảnh | Đọc tốt ảnh thực tế | Gửi ảnh nhà người thuê ra dịch vụ ngoài; cần khóa, billing |
| AWS Textract / Azure AI Vision Read | Tương tự, tính theo trang/giao dịch | Đọc tốt | Như trên; phải chọn region |
| Mô hình vision (LLM) | Tính theo token | Hiểu được cả mặt đồng hồ, chữ số đỏ phần thập phân | Chi phí, độ trễ, khó kiểm soát độ tin cậy |

Giá dịch vụ đám mây thay đổi theo thời điểm, cần xem lại trang giá trước khi chọn. Với quy mô đồ án và yêu cầu không đẩy ảnh cá nhân ra ngoài, chọn Tesseract. Adapter mới chỉ cần cài `MeterOcrPort` nên đổi engine sau này không ảnh hưởng nghiệp vụ.

## Kết quả đo

Chạy bằng `scripts/ocr-benchmark.sh` (đúng tham số của adapter), Tesseract 5.5.2 trên Alpine 3.24, ngưỡng 0.85.

**20 ảnh tổng hợp.** Số in bằng font Consolas/Arial/Courier cỡ 40–72 px, gồm 4 kiểu: đen trên trắng, trắng trên đen (ô số đồng hồ cơ), nền xám có nhiễu và làm mờ, xoay nghiêng 4°. Một nửa số ảnh có phần thập phân.

| Kết quả | Số ảnh |
|---|---|
| Tự điền, đúng | 17 |
| Tự điền, **sai** | 0 |
| Nhập tay (dưới ngưỡng) | 3 |

Hai ảnh Tesseract đọc sai có độ tin cậy 0.35 và 0.00 nên đều rơi xuống nhập tay. Ảnh thứ ba đọc đúng nhưng chỉ đạt 0.84, dưới ngưỡng.

Gọi qua API, ảnh JPEG được `FileSanitizer` mã hóa lại trước khi OCR nên độ tin cậy có thể lệch đôi chút so với chạy lệnh trực tiếp.

**Chưa làm:** đo trên **ít nhất 20 ảnh đồng hồ thật**, theo yêu cầu của issue. Ảnh tổng hợp chỉ chứng minh pipeline chạy đúng và ngưỡng chặn được kết quả sai; ảnh thật (chụp cả mặt đồng hồ, chói, góc nghiêng) sẽ cho tỷ lệ tự điền thấp hơn nhiều. Cách đo:

1. Chụp ảnh đồng hồ thật, nên cắt sát ô số. Không dùng ảnh có thông tin người thuê.
2. Tạo `expected.txt` trong cùng thư mục, mỗi dòng `<tên file> <chỉ số đúng>`.
3. Chạy `scripts/ocr-benchmark.sh <thư mục>`. Máy không có tesseract thì script tự chạy trong Docker.
4. Ghi kết quả vào bảng trên. Nếu có `AUTO_WRONG` thì tăng `METER_OCR_MIN_CONFIDENCE` cho tới khi hết.
