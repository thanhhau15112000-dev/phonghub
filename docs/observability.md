# Hướng Dẫn Giám Sát Hệ Thống PhongHub (Observability Guide)

Tài liệu hướng dẫn vận hành, giám sát số liệu (Metrics), cảnh báo (Alerting) bằng **Grafana & Prometheus** và quản lý log tập trung (Centralized Logging) bằng **OpenSearch, Fluent Bit & OpenSearch Dashboards**.

---

## 1. Kiến Trúc Tổng Quan

```mermaid
flowchart LR
    Client[Client / Trình duyệt] -->|Gửi Request| App[PhongHub Spring Boot App]
    
    subgraph Metrics & Alerting
        App -->|/actuator/prometheus<br/>Bearer Token| Prom[Prometheus]
        Prom -->|Scrape số liệu| Grafana[Grafana Dashboards]
        Grafana -->|Cảnh báo| Alert[Alert Rules: App Down, 5xx, SePay, DB Pool]
    end

    subgraph Centralized Logging
        App -->|ECS Structured JSON Log| FB[Fluent Bit]
        FB -->|Gom & Đẩy Log| OS[OpenSearch<br/>phonghub-logs-YYYY.MM.DD]
        OS -->|Truy vấn theo requestId| OSD[OpenSearch Dashboards]
        OS -->|ISM Policy: 14 ngày| Clean[Tự động xóa log cũ]
    end
```

* **Metrics (Số liệu):** Spring Boot Actuator kết hợp Micrometer Prometheus Registry ghi nhận số liệu JVM, DB Pool Hikari, HTTP routes, tỷ lệ lỗi, custom metrics (429 Rate limit, SSE chat connections, SePay webhook, scheduled jobs).
* **Logging (Log tập trung):** Spring Boot sử dụng định dạng JSON có cấu trúc (ECS format). Bộ lọc `MdcLoggingFilter` tự động gắn `requestId`/`traceId`, `route` và mã định danh người dùng đã băm SHA-256 (`userIdHash`).
* **Bảo mật & PII:** Tuyệt đối không log mật khẩu, CCCD, token xác thực. Endpoint `/actuator/prometheus` được bảo vệ bằng secret token.

---

## 2. Yêu Cầu Cấu Hình Máy & Bật Local

### Cấu hình máy khuyến nghị
* **CPU:** Tối thiểu 2 Cores.
* **RAM:**
  * App + DB + Redis thông thường: ~1GB RAM khả dụng.
  * Toàn bộ Observability Stack (OpenSearch, Dashboards, Prometheus, Grafana, Fluent Bit): Cần thêm **~1.5GB – 2.5GB RAM**.
  * Máy tính phát triển nên có từ **8GB RAM** trở lên.

### Lệnh chạy hệ thống

#### 1. Khởi động môi trường tiêu chuẩn (Mặc định - KHÔNG tốn RAM giám sát)
```bash
docker compose up -d
```
Chỉ chạy `phonghub-db`, `phonghub-redis` và `phonghub-app`.

#### 2. Khởi động kèm toàn bộ hệ thống theo dõi (Observability Profile)
```bash
docker compose --profile observability up -d
```
Lệnh này sẽ khởi động thêm 5 containers: `opensearch`, `opensearch-dashboards`, `prometheus`, `grafana`, `fluent-bit`.

#### 3. Dừng hệ thống theo dõi
```bash
docker compose --profile observability down
```
Ứng dụng PhongHub hoàn toàn độc lập: nếu hệ thống theo dõi bị tắt hoặc gặp lỗi, ứng dụng vẫn hoạt động bình thường 100%.

---

## 3. Danh Sách Địa Chỉ Truy Cập & Tài Khoản

| Dịch vụ | Địa chỉ URL | Cổng (Port) | Tài khoản / Xác thực |
| :--- | :--- | :--- | :--- |
| **PhongHub Ứng dụng** | `http://localhost:8080` | `8080` | Dữ liệu mẫu (admin / owner / tenant) |
| **Actuator Prometheus** | `http://localhost:8080/actuator/prometheus` | `8080` | Header `Authorization: Bearer phonghub-metrics-secret-token`<br/>hoặc param `?token=phonghub-metrics-secret-token` |
| **Prometheus Web UI** | `http://localhost:9090` | `9090` | Không cần mật khẩu (nội bộ) |
| **Grafana Dashboards** | `http://localhost:3000` | `3000` | Username: `admin`<br/>Password: `admin` |
| **OpenSearch REST API** | `http://localhost:9200` | `9200` | `admin` / `admin` (đã tắt TLS ở local) |
| **OpenSearch Dashboards** | `http://localhost:5601` | `5601` | Giao diện trực quan tìm log |

---

## 4. Các Dashboard Dựng Sẵn Trong Grafana

Các dashboard được cấu hình tự động (Provisioning) từ thư mục `ops/grafana/dashboards/`:

1. **PhongHub - Tổng quan App (`phonghub-app-overview`):**
   * Lưu lượng truy cập (Requests Per Second) phân tách theo từng URI.
   * Độ trễ phản hồi p95 (`http_server_requests_seconds`).
   * Dung lượng bộ nhớ JVM Heap (Used vs Max).
   * Số lần bị chặn giới hạn tần suất 429 (`phonghub_ratelimit_rejected_total`).
   * Số kết nối SSE Chat trực tuyến (`phonghub_chat_sse_active_connections`).
2. **PhongHub - DB Pool (HikariCP) (`phonghub-db-pool`):**
   * Số kết nối Active, Idle và Max Connections.
   * Số thread đang chờ kết nối DB (`hikaricp_connections_pending`).
   * Thời gian trung bình để mượn kết nối (`hikaricp_connections_acquire_seconds`).
3. **PhongHub - Lỗi HTTP 5xx (`phonghub-http-5xx`):**
   * Tổng số lỗi 5xx trong 5 phút gần nhất.
   * Tỷ lệ lỗi 5xx trên tổng số request (%).
   * Biểu đồ lỗi 5xx theo từng route/URI cụ thể để cô lập sự cố.
4. **PhongHub - Thanh toán & Job Định kỳ (`phonghub-payments`):**
   * Số lượng webhook SePay thành công vs thất bại/sai chữ ký (`phonghub_sepay_ipn_total`).
   * Lịch sử thực thi các scheduled jobs (`phonghub_scheduled_job_executions_total` cho late fee, SLA timeout).

---

## 5. Quy Tắc Cảnh Báo (Alert Rules)

Grafana được tích hợp sẵn 4 quy tắc cảnh báo tại `ops/grafana/provisioning/alerting/alerting.yaml`:
* **App không phản hồi (`alert-app-down`):** Kích hoạt khi `up{job="phonghub-app"} == 0` kéo dài quá 1 phút (Mức độ: `critical`).
* **Tỷ lệ lỗi 5xx tăng cao (`alert-high-5xx-rate`):** Kích hoạt khi phát sinh lỗi 5xx liên tục trong 2 phút (Mức độ: `warning`).
* **SePay Webhook lỗi liên tiếp (`alert-sepay-ipn-error`):** Kích hoạt khi có từ 3 webhook lỗi trong 5 phút (Mức độ: `high`).
* **Hồ kết nối DB cạn kiệt (`alert-db-pool-exhausted`):** Kích hoạt khi số kết nối active > 90% hoặc có thread pending (Mức độ: `critical`).

---

## 6. Hướng Dẫn Tra Cứu Log Theo `requestId`

Mỗi request vào hệ thống đều được sinh một mã định danh duy nhất (hoặc nhận từ header `X-Request-Id`). Mã này được trả về ở Header Response:

```http
HTTP/1.1 200 OK
X-Request-Id: 4f1a2380-77a8-4e59-8664-897db67eef03
Content-Type: application/json
```

### Các bước tra cứu khi có lỗi:
1. Copy giá trị của `X-Request-Id` từ phản hồi của API hoặc màn hình báo lỗi.
2. Mở **OpenSearch Dashboards** tại `http://localhost:5601`.
3. Chọn mục **Discover** và index pattern `phonghub-logs-*`.
4. Nhập vào ô tìm kiếm:
   ```lucene
   requestId: "4f1a2380-77a8-4e59-8664-897db67eef03"
   ```
5. Hệ thống sẽ hiển thị chính xác toàn bộ luồng log liên quan đến request này (kèm route, mã băm người dùng `userIdHash`, stacktrace lỗi nếu có).

---

## 7. Chính Sách Tự Động Xóa Log (ISM Policy 14 Ngày)

File chính sách Index State Management tại `ops/opensearch/ism-policy.json` tự động gán cho các index có tiền tố `phonghub-logs-*`. Khi index đạt tuổi thọ **14 ngày**, OpenSearch sẽ tự động giải phóng dữ liệu để tránh đầy dung lượng đĩa.

Để nạp chính sách này vào OpenSearch local:
```bash
curl -XPUT "http://localhost:9200/_plugins/_ism/policies/delete_after_14_days" \
     -H "Content-Type: application/json" \
     -d @ops/opensearch/ism-policy.json
```

---

## 8. Phương Án Triển Khai Trên Môi Trường Production (Render)

Môi trường Production hiện tại chạy trên Render gói miễn phí (~512MB RAM), không đủ tài nguyên để tự host OpenSearch và Grafana trên cùng server.

### Phương án tối ưu chi phí (Khuyến nghị): Grafana Cloud Free Tier
* **Ưu điểm:** Miễn phí vĩnh viễn, lưu trữ tới 10,000 active series metrics (Prometheus) và 50GB logs (Loki) mỗi tháng.
* **Cách thực hiện:**
  1. Đăng ký tài khoản miễn phí tại [grafana.com](https://grafana.com).
  2. Lấy thông tin Prometheus remote-write endpoint và Loki push URL.
  3. Cấu hình ứng dụng Spring Boot đẩy số liệu và log trực tiếp sang Grafana Cloud qua HTTPS.
  4. Không tốn bất kỳ chi phí VPS nào và không chiếm dụng RAM của Render.

### Phương án máy chủ riêng (Self-hosted VPS)
* Thuê 1 VPS riêng (tối thiểu 4GB RAM - chi phí khoảng ~100k - 150k VNĐ/tháng).
* Chạy Docker Compose stack `observability` trên VPS này.
* Ứng dụng PhongHub trên Render gửi metrics và log về IP/Domain của VPS qua kết nối bảo mật kèm token xác thực.

