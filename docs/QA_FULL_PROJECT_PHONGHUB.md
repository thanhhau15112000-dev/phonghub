# TÀI LIỆU QA TOÀN DIỆN DỰ ÁN PHONGHUB
**Hệ thống Quản lý Vận hành Nhà trọ & Căn hộ Dịch vụ**
*Tech Stack: Java 21, Spring Boot 4.1, Thymeleaf SSR, Hexagonal Architecture, PostgreSQL, Docker, SePay, Caffeine, Tesseract OCR*

---

## MỤC LỤC
1. [TỔNG QUAN CHẤT LƯỢNG & MỤC TIÊU QA](#1-tổng-quan-chất-lượng--mục-tiêu-qa)
2. [MA TRẬN KIỂM THỬ TOÀN DIỆN (BE + FE TEST MATRIX)](#2-ma-trận-kiểm-thử-toàn-diện-be--fe-test-matrix)
3. [KỊCH BẢN KIỂM THỬ CHI TIẾT (TEST CASES THEO PHÂN HỆ)](#3-kịch-bản-kiểm-thử-chi-tiết-test-cases-theo-phân-hệ)
   - 3.1. Xác thực & Phân quyền (Auth & RBAC)
   - 3.2. Quản lý Tòa nhà & Nhà trọ (Properties)
   - 3.3. Quản lý Phòng trọ & Tài sản (Rooms & Room Assets)
   - 3.4. Quản lý Khách thuê & Bảo mật thông tin cá nhân (Tenants & Privacy)
   - 3.5. Vòng đời Hợp đồng thuê (Contracts Lifecycle)
   - 3.6. Biên bản bàn giao phòng (Handover Check-in/Check-out)
   - 3.7. Chỉ số Điện/Nước & Tesseract OCR (Meters & OCR)
   - 3.8. Hóa đơn, Tiền phòng & Phạt trễ hạn (Invoices & Late Fees)
   - 3.9. Thanh toán & Đối soát tự động SePay (Payments & Webhook)
   - 3.10. Bảo trì, Báo giá & SLA (Maintenance, Quote & SLA)
   - 3.11. Trung tâm thông báo & Trò chuyện (Notifications & Chat)
   - 3.12. Báo cáo, Thống kê & Xuất dữ liệu (Exports & Statistics)
4. [BÁO CÁO AUTOMATION TEST & COVERAGE (JACOCO)](#4-báo-cáo-automation-test--coverage-jacoco)
5. [BỘ CÂU HỎI Q&A BẢO VỆ DỰ ÁN (CHUYÊN SÂU TỪ A - Z)](#5-bộ-câu-hỏi-qa-bảo-vệ-dự-án-chuyên-sâu-từ-a---z)
   - 5.1. Nhóm câu hỏi Kiến trúc (Architecture)
   - 5.2. Nhóm câu hỏi Nghiệp vụ (Business Flow)
   - 5.3. Nhóm câu hỏi Bảo mật (Security & Privacy)
   - 5.4. Nhóm câu hỏi Thanh toán & Concurrency (Payments & Concurrency)
   - 5.5. Nhóm câu hỏi Database & Performance (Data & Caching)

---

## 1. TỔNG QUAN CHẤT LƯỢNG & MỤC TIÊU QA

### 1.1. Phạm vi chất lượng (Quality Scope)
PhongHub áp dụng mô hình kiểm thử đa tầng (Multi-layered Testing Pyramid) đảm bảo tính toàn vẹn từ nghiệp vụ lõi (Pure Domain Invariants) đến giao diện hiển thị người dùng (Thymeleaf SSR Web UI) và tích hợp bên thứ ba (SePay, Tesseract OCR, Supabase).

```text
       [ Tầng UI & Web Integration Tests ]
       (MockMvc, Thymeleaf HTML, Forms, Flash Msgs, Role View)
                         |
       [ Tầng Application Ports & Use Cases ]
       (Business Orchestration, Transaction Boundaries)
                         |
       [ Tầng Pure Domain Entities & Invariants ]
       (Room/Contract State Machine, Late Fees, Pure Java)
                         |
       [ Tầng Outbound Persistence & Security Adapters ]
       (Postgres JDBC, AES-GCM Encryptor, SePay, Caffeine Cache)
```

### 1.2. Các tiêu chí nghiệm thu chất lượng (Quality Gates)
- **Tỷ lệ Pass Test:** 100% active tests phải pass (`Failures = 0`, `Errors = 0`).
- **Ngưỡng bao phủ mã nguồn (JaCoCo Instruction Coverage):** Tối thiểu 75% toàn hệ thống (Hiện tại đạt **80%**).
- **Tuân thủ kiến trúc:** ArchUnit kiểm tra nghiêm ngặt không có bất kỳ rò rỉ mã nguồn từ Spring, JDBC, Web vào package `com.phonghub.domain`.
- **An toàn bảo mật:** Mã hóa thông tin cá nhân khách thuê (AES-GCM 256-bit), giới hạn tần suất đăng nhập (Rate Limiting), kiểm soát phiên đăng nhập và phân quyền chặt chẽ theo Property Scope.

---

## 2. MA TRẬN KIỂM THỬ TOÀN DIỆN (BE + FE TEST MATRIX)

| STT | Phân hệ (Module) | Phạm vi Backend (BE) | Phạm vi Frontend (FE UI) | Lớp Test Tự động Tương Ứng |
|:---:|---|---|---|---|
| **1** | **Xác thực & Phân quyền** | Map Username sang Supabase Email ẩn, Rate Limiting, RBAC (ADMIN, OWNER, STAFF, TECH, TENANT), đổi mật khẩu lần đầu. | Trang `/login`, thông báo lỗi chung, form đổi mật khẩu, phân quyền hiển thị theo Role, quản lý tài khoản `/admin/users`. | `AuthServiceUnitTest`<br/>`RateLimitFilterTest`<br/>`PhongHubUiIntegrationTest` |
| **2** | **Quản lý Nhà trọ** | CRUD Property, duyệt nhà trọ (PENDING/APPROVED), gán Staff phụ trách, lưu trữ/archive tòa nhà. | Bảng danh sách nhà, badge trạng thái phê duyệt, modal gán staff, tab lưu trữ. | `PropertyServiceUnitTest`<br/>`PropertyStaffUiIntegrationTest`<br/>`PropertyArchiveIntegrationTest` |
| **3** | **Quản lý Phòng & Tài sản** | Tạo phòng, chuyển đổi trạng thái (AVAILABLE/OCCUPIED/MAINTENANCE), kiểm kê tài sản phòng, lọc nâng cao. | Lưới danh sách phòng, badge màu theo trạng thái, bảng kiểm kê trang thiết bị, bộ lọc giá/diện tích. | `RoomTransitionsUnitTest`<br/>`RoomAssetServiceUnitTest`<br/>`RoomAssetUiIntegrationTest` |
| **4** | **Khách thuê & Quyền riêng tư** | Quản lý thông tin khách chính & người ở cùng, mã hóa AES-GCM CCCD, cơ chế Blind Index HMAC, Privacy Purge. | Form nhập khách thuê, bảng danh sách có che giấu (masking) số CCCD, trang upload giấy tờ định danh. | `AesGcmTenantDataProtectorTest`<br/>`TenantDocumentUiIntegrationTest`<br/>`TenantPrivacyApiIntegrationTest` |
| **5** | **Vòng đời Hợp đồng** | Soạn thảo DRAFT, kích hoạt ACTIVE, gia hạn RENEW, hủy DRAFT (CANCELLED), đơn phương/kết thúc hợp đồng. | Form lập hợp đồng, xem trước PDF, cảnh báo hợp đồng sắp hết hạn, chi tiết điều khoản thuê. | `ContractLifecycleUnitTest`<br/>`ContractDraftEditUnitTest`<br/>`PhongHubUiIntegrationTest` |
| **6** | **Biên bản Bàn giao** | Bàn giao nhận phòng (Check-in), trả phòng (Check-out), lưu chỉ số điện nước ban đầu & ảnh chụp hiện trạng. | Form lập biên bản bàn giao, giao diện so sánh hiện trạng lúc vào vs lúc ra (Handover Compare). | `HandoverStateMachineUnitTest`<br/>`HandoverUiIntegrationTest`<br/>`HandoverReportUiIntegrationTest` |
| **7** | **Chỉ số Điện Nước & OCR** | Ghi nhận chỉ số, phát hiện bất thường chỉ số âm/tăng đột biến, Tesseract OCR nhận dạng ảnh đồng hồ. | Form nhập chỉ số theo phòng, xem ảnh chụp công tơ, ô gợi ý nhận dạng số qua OCR. | `MeterSubmissionUiIntegrationTest`<br/>`TesseractMeterOcrAdapterTest`<br/>`MeterOcrSuggestionUnitTest` |
| **8** | **Hóa đơn & Phạt nợ** | Lập hóa đơn tự động định kỳ, tính giá bậc thang, áp dụng lãi/phạt quá hạn (Late Fee Policy), Void hóa đơn. | Bảng hóa đơn theo tháng, chi tiết phiếu thu, nút bấm hủy hóa đơn (Void), cấu hình chính sách phạt nợ. | `LateFeeUnitTest`<br/>`MonthlyInvoiceAutomationUnitTest`<br/>`LateFeePolicyUiIntegrationTest` |
| **9** | **Thanh toán & SePay Webhook** | Xử lý Webhook SePay, phân bổ tiền vào hóa đơn, xử lý thanh toán thiếu/thừa (Overpay/Underpay), đối soát. | Trang thanh toán QR VietQR cá nhân hóa, trang quản lý đối soát giao dịch ngân hàng (Reconciliation). | `SepayPaymentServiceUnitTest`<br/>`PaymentReconciliationWebIntegrationTest`<br/>`SepayWebhookControllerIntegrationTest` |
| **10** | **Bảo trì, Báo giá & SLA** | Tạo ticket, gán Technician, quy trình báo giá (Quote) & duyệt giá, SLA phản hồi, từ chối có lý do. | Form báo hỏng dành cho khách, giao diện kỹ thuật viên cập nhật tiến độ, ảnh đính kèm trước/sau sửa chữa. | `MaintenanceQuoteAndVerifyUnitTest`<br/>`MaintenanceTicketTransitionsUnitTest`<br/>`MaintenanceListFiltersIntegrationTest` |
| **11** | **Thông báo & Trò chuyện** | Broadcast thông báo, gửi thông báo cá nhân, phòng chat nội bộ, quyền xem kênh Global (Tenant Read-only). | Chuông thông báo trên Header, dropdown tin chưa đọc, modal widget chat nổi, phòng chat trao đổi. | `NotificationCenterIntegrationTest`<br/>`NotificationInboxServiceUnitTest`<br/>`ChatApiControllerIntegrationTest` |
| **12** | **Báo cáo & Thống kê** | Tổng hợp doanh thu, tỷ lệ lấp đầy phòng, xuất báo cáo PDF (OpenPDF) và Excel XLSX (Apache POI). | Dashboard thống kê với biểu đồ trực quan, các nút bấm tải file PDF hóa đơn/hợp đồng và Excel công nợ. | `StatisticsServiceUnitTest`<br/>`TableExportersTest`<br/>`ExportWebIntegrationTest` |

---

## 3. KỊCH BẢN KIỂM THỬ CHI TIẾT (TEST CASES THEO PHÂN HỆ)

### 3.1. Xác thực & Phân quyền (Auth & RBAC)

#### TC-AUTH-01: Đăng nhập thành công với tài khoản Admin/Owner/Staff
- **Mục tiêu:** Kiểm tra đăng nhập qua giao diện web bằng Username và Password hợp lệ.
- **Tiền điều kiện:** Người dùng có tài khoản đang hoạt động trong hệ thống.
- **Các bước thực hiện:**
  1. Truy cập `GET /login`.
  2. Nhập `username` (vd: `owner1`), `password` (mật khẩu hợp lệ).
  3. Nhấn "Đăng nhập".
- **Kết quả mong đợi:**
  - Hệ thống tạo Session hợp lệ.
  - Redirect 302 về trang `/dashboard`.
  - Header hiển thị đúng tên người dùng và vai trò tương ứng.

#### TC-AUTH-02: Chống lộ thông tin tài khoản khi đăng nhập thất bại (Security Invariant)
- **Mục tiêu:** Đảm bảo hệ thống trả về cùng một thông báo lỗi khi nhập sai username hoặc sai mật khẩu.
- **Các bước thực hiện:**
  1. Thử đăng nhập với Username không tồn tại trong hệ thống.
  2. Thử đăng nhập với Username có thật nhưng sai mật khẩu.
- **Kết quả mong đợi:**
  - Cả hai trường hợp đều trả về thông báo lỗi chung: *"Tên đăng nhập hoặc mật khẩu không chính xác"*.
  - Tuyệt đối không thông báo *"Tài khoản không tồn tại"* để tránh lỗ hổng User Enumeration.

#### TC-AUTH-03: Rate Limiting chống Brute-Force đăng nhập
- **Mục tiêu:** Kiểm tra cơ chế khóa/giới hạn khi thử đăng nhập sai liên tục.
- **Các bước thực hiện:**
  1. Gửi liên tiếp 5 lần đăng nhập sai từ cùng một IP/tài khoản trong vòng 1 phút.
- **Kết quả mong đợi:**
  - Từ lần thử thứ 5 trở đi, hệ thống phản hồi HTTP 429 Too Many Requests hoặc khóa tạm thời và yêu cầu chờ trước khi thử lại.

#### TC-AUTH-04: Bắt buộc đổi mật khẩu ở lần đăng nhập đầu tiên (`must_change_password`)
- **Mục tiêu:** Đảm bảo tài khoản mới tạo bởi Admin phải đổi mật khẩu trước khi truy cập bất kỳ tính năng nào.
- **Các bước thực hiện:**
  1. Đăng nhập bằng tài khoản mới được cấp với mật khẩu tạm thời.
  2. Cố gắng điều hướng sang `/dashboard`, `/rooms`, `/contracts`.
- **Kết quả mong đợi:**
  - Mọi yêu cầu đều bị redirect về trang `/change-password`.
  - Sau khi đổi mật khẩu thành công, cờ `must_change_password` chuyển thành `false` và cho phép truy cập bình thường.

---

### 3.2. Quản lý Tòa nhà & Nhà trọ (Properties)

#### TC-PROP-01: Kiểm duyệt nhà trọ của Owner (Approval Flow)
- **Mục tiêu:** Đảm bảo nhà trọ mới tạo bởi Owner phải qua phê duyệt của Admin trước khi được đưa vào kinh doanh.
- **Các bước thực hiện:**
  1. Tài khoản Owner đăng ký một dãy trọ mới.
  2. Kiểm tra trạng thái dãy trọ: Phải là `PENDING_APPROVAL`.
  3. Tài khoản Admin đăng nhập, vào danh sách duyệt, chọn "Phê duyệt" (Approve).
- **Kết quả mong đợi:**
  - Trạng thái chuyển thành `APPROVED`.
  - Owner nhận được thông báo phê duyệt thành công.
  - Dãy trọ bắt đầu cho phép tạo phòng và ký hợp đồng.

#### TC-PROP-02: Phân quyền quản lý nhà trọ theo nhân viên (Staff Scope Isolation)
- **Mục tiêu:** Staff chỉ có quyền xem và thao tác trên những nhà trọ được Admin phân công.
- **Các bước thực hiện:**
  1. Gán `staff1` quản lý "Nhà trọ A", không gán "Nhà trọ B".
  2. Đăng nhập bằng tài khoản `staff1`.
  3. Truy cập danh sách nhà trọ và thử truy cập trực tiếp URL `/properties/{id_nhà_B}`.
- **Kết quả mong đợi:**
  - Danh sách chỉ hiển thị "Nhà trọ A".
  - Khi cố tình truy cập nhà B, hệ thống trả về lỗi 403 Forbidden.

---

### 3.3. Quản lý Phòng trọ & Tài sản (Rooms & Room Assets)

#### TC-ROOM-01: Chuyển đổi trạng thái phòng (Room State Machine)
- **Mục tiêu:** Kiểm tra vòng đời chuẩn của phòng trọ.
- **Kịch bản:**
  - Phòng mới tạo: `AVAILABLE`.
  - Khi có khách đặt cọc giữ chỗ: `RESERVED`.
  - Khi hợp đồng có hiệu lực: tự động chuyển sang `OCCUPIED`.
  - Khi kết thúc hợp đồng hoặc có sự cố hư hỏng: chuyển sang `MAINTENANCE`.
  - Khi bảo trì nghiệm thu hoàn tất: chuyển về `AVAILABLE`.
- **Invariants bắt buộc:** Không được phép ký hợp đồng ACTIVE trên phòng đang ở trạng thái `MAINTENANCE` hoặc đã có hợp đồng khác đang `OCCUPIED`.

#### TC-ROOM-02: Kiểm kê tài sản bàn giao phòng (Room Assets Management)
- **Mục tiêu:** Kiểm tra thêm, sửa, thống kê trang thiết bị trong phòng (Điều hòa, Nóng lạnh, Giường, Tủ).
- **Kết quả mong đợi:**
  - Mỗi tài sản lưu rõ mã tài sản, số lượng, tình trạng sử dụng (Mới, Đang hoạt động, Hỏng hóc).
  - Tự động đồng bộ danh mục tài sản này vào Biên bản bàn giao khi khách nhận phòng.

---

### 3.4. Khách thuê & Bảo mật Dữ liệu cá nhân (Tenants & Privacy)

#### TC-TENANT-01: Mã hóa an toàn dữ liệu CCCD/Hộ chiếu (AES-GCM Encryption)
- **Mục tiêu:** Đảm bảo số căn cước công dân và thông tin nhạy cảm của khách thuê được mã hóa trước khi lưu vào Database.
- **Kiểm tra kỹ thuật:**
  - Kiểm tra trực tiếp bản ghi trong bảng `tenants` tại cơ sở dữ liệu.
  - Cột `identity_card_number_encrypted` chứa chuỗi ciphertext mã hóa AES-GCM kèm IV.
  - Hệ thống sử dụng HMAC Blind Index để phục vụ việc tra cứu tìm kiếm chính xác mà không cần giải mã toàn bộ bảng dữ liệu.

#### TC-TENANT-02: Che giấu thông tin nhạy cảm trên giao diện (Data Masking)
- **Mục tiêu:** Đảm bảo nhân viên và người xem thông thường không thấy lộ toàn bộ số định danh cá nhân.
- **Kết quả mong đợi:**
  - Trên màn hình danh sách, số CCCD hiển thị định dạng: `07920******123`.
  - Chỉ người có thẩm quyền phù hợp mới có nút bấm "Xem đầy đủ" (kèm theo ghi nhật ký kiểm toán `audit_log`).

---

### 3.5. Vòng đời Hợp đồng thuê (Contracts Lifecycle)

#### TC-CTR-01: Invariant: Một phòng chỉ có duy nhất một hợp đồng hiệu lực tại một thời điểm
- **Mục tiêu:** Ngăn chặn xung đột trùng phòng (Overbooking).
- **Các bước thực hiện:**
  1. Tạo hợp đồng `CTR-01` cho phòng 101 từ ngày 01/10/2026 đến 01/10/2027 (Trạng thái `ACTIVE`).
  2. Cố gắng tạo hoặc kích hoạt tiếp hợp đồng `CTR-02` cho phòng 101 trong cùng khoảng thời gian trên.
- **Kết quả mong đợi:**
  - Hệ thống ném ngoại lệ `RoomAlreadyOccupiedException` hoặc từ chối kích hoạt.
  - Giao diện báo lỗi: *"Phòng 101 hiện đang có hợp đồng hiệu lực"*.

#### TC-CTR-02: Hủy hợp đồng DRAFT (Cancel Draft Contract)
- **Mục tiêu:** Cho phép hủy hợp đồng ở trạng thái nháp khi chưa ký kết.
- **Kết quả mong đợi:**
  - Hợp đồng DRAFT được chuyển sang trạng thái `CANCELLED`.
  - Phòng liên quan được giải phóng trạng thái `RESERVED` quay về `AVAILABLE`.

---

### 3.6. Biên bản Bàn giao phòng (Handover Check-in/Check-out)

#### TC-HAND-01: Lập biên bản bàn giao nhận phòng (Check-in)
- **Mục tiêu:** Ghi nhận chỉ số điện nước ban đầu và hiện trạng tài sản khi giao chìa khóa cho khách.
- **Dữ liệu kiểm thử:**
  - Chỉ số điện đầu: `1250 kWh`.
  - Chỉ số nước đầu: `85 m3`.
  - Ảnh chụp công tơ và thiết bị đính kèm.
- **Kết quả mong đợi:**
  - Biên bản chuyển sang trạng thái `COMPLETED`.
  - Dữ liệu chỉ số đầu được khóa lại làm căn cứ tính cước cho kỳ hóa đơn đầu tiên.

#### TC-HAND-02: Đối soát bàn giao trả phòng (Check-out Comparison)
- **Mục tiêu:** So sánh hiện trạng lúc nhận phòng và lúc trả phòng để xử lý tiền cọc.
- **Kết quả mong đợi:**
  - Giao diện hiển thị bảng so sánh 2 cột: "Lúc nhận" và "Lúc trả".
  - Nếu phát hiện hư hỏng do lỗi khách thuê, hệ thống cho phép tạo khoản trừ bồi thường tài sản trực tiếp vào hóa đơn thanh lý.

---

### 3.7. Chỉ số Điện/Nước & Tesseract OCR

#### TC-METER-01: Validation chỉ số tiêu thụ không âm
- **Mục tiêu:** Ngăn chặn nhập sai chỉ số mới nhỏ hơn chỉ số cũ của kỳ trước.
- **Các bước thực hiện:**
  1. Chỉ số điện kỳ trước là `1500 kWh`.
  2. Người dùng nhập chỉ số mới là `1490 kWh`.
- **Kết quả mong đợi:**
  - Hệ thống báo lỗi: *"Chỉ số mới không được nhỏ hơn chỉ số kỳ trước (1500 kWh)"*.
  - Không cho phép lưu bản ghi.

#### TC-METER-02: Gợi ý chỉ số tự động từ ảnh chụp công tơ bằng Tesseract OCR
- **Mục tiêu:** Hỗ trợ quét tự động số điện/nước từ ảnh chụp thực tế.
- **Các bước thực hiện:**
  1. Tải lên ảnh chụp mặt đồng hồ công tơ điện hiển thị số `04582`.
  2. Nhấn nút "Nhận dạng tự động".
- **Kết quả mong đợi:**
  - Bộ chuyển đổi OCR trích xuất chuỗi số `4582`.
  - Tự động điền vào ô nhập liệu để người dùng xác nhận lại trước khi lưu.

---

### 3.8. Hóa đơn, Tiền phòng & Phạt trễ hạn (Invoices & Late Fees)

#### TC-INV-01: Lập hóa đơn tự động định kỳ hàng tháng
- **Mục tiêu:** Tính toán đầy đủ các khoản phí trong kỳ cước.
- **Công thức kiểm tra:**
  $$\text{Tổng tiền} = \text{Tiền phòng} + (\Delta \text{Điện} \times \text{Đơn giá điện}) + (\Delta \text{Nước} \times \text{Đơn giá nước}) + \text{Phí dịch vụ cố định}$$
- **Kết quả mong đợi:**
  - Hóa đơn được tạo ở trạng thái `ISSUED`.
  - Tạo mã thanh toán duy nhất (Payment Code) gắn liền với hóa đơn.
  - Tự động gửi thông báo đến tài khoản khách thuê.

#### TC-INV-02: Áp dụng chính sách phạt nợ tiền phòng quá hạn (Late Fee Assessment)
- **Mục tiêu:** Tự động tính phí phạt khi hóa đơn quá hạn mà chưa được thanh toán.
- **Quy tắc:**
  - Hạn thanh toán: Ngày 05 hàng tháng.
  - Sau ngày 05, mỗi ngày trễ cộng thêm % cố định hoặc số tiền phạt theo ngày đã cấu hình.
- **Kết quả mong đợi:**
  - Hóa đơn chuyển trạng thái sang `OVERDUE`.
  - Khoản mục "Phí phạt trễ hạn" được tự động bổ sung vào chi tiết hóa đơn.

#### TC-INV-03: Hủy hóa đơn lỗi (Void Invoice Invariant)
- **Mục tiêu:** Hủy hóa đơn xuất sai mà không làm sai lệch sổ sách kế toán.
- **Điều kiện:** Chỉ được phép Void khi hóa đơn chưa có thanh toán thành công nào gắn kèm.
- **Kết quả mong đợi:** Trạng thái chuyển thành `VOIDED`, ghi rõ lý do hủy và người thực hiện vào audit log.

---

### 3.9. Thanh toán & Đối soát tự động SePay (Payments & Webhook)

#### TC-PAY-01: Tích hợp Webhook SePay - Thanh toán đúng số tiền và đúng mã
- **Mục tiêu:** Tự động gạch nợ hóa đơn ngay khi tiền về tài khoản ngân hàng.
- **Kịch bản:**
  - Hóa đơn `INV-2026-10-001` có số tiền cần thanh toán là `3.500.000 VNĐ`.
  - Mã thanh toán: `PH1001`.
  - Ngân hàng bắn Webhook SePay chứa nội dung chuyển khoản: *"PH1001 thanh toan tien phong"*, số tiền `3.500.000 VNĐ`.
- **Kết quả mong đợi:**
  - Webhook Controller xác thực chữ ký/API token hợp lệ.
  - Tìm thấy hóa đơn tương ứng, chuyển trạng thái hóa đơn sang `PAID`.
  - Tạo bản ghi thanh toán `Payment` tương ứng.
  - Trả về HTTP 200 cho SePay.

#### TC-PAY-02: Xử lý thanh toán thừa / thiếu (Overpay / Partial Pay)
- **Mục tiêu:** Đảm bảo hệ thống xử lý chính xác khi khách chuyển sai số tiền.
- **Kịch bản:**
  - Hóa đơn `3.000.000 VNĐ`, khách chuyển `2.000.000 VNĐ`: Hóa đơn ghi nhận thanh toán 1 phần (`PARTIALLY_PAID`), số dư nợ còn lại `1.000.000 VNĐ`.
  - Khách chuyển `3.500.000 VNĐ`: Hóa đơn thành `PAID`, số tiền dư `500.000 VNĐ` được ghi nhận vào quỹ dư tài khoản hoặc đưa vào bảng chờ đối soát (Reconciliation Review).

---

### 3.10. Bảo trì, Báo giá & SLA (Maintenance, Quote & SLA)

#### TC-MAINT-01: Quy trình báo hỏng và gán Kỹ thuật viên
- **Mục tiêu:** Khách gửi yêu cầu báo hỏng -> Staff tiếp nhận -> Gán Technician xử lý.
- **Kết quả mong đợi:**
  - Ticket tạo mới: `OPEN`.
  - Gán kỹ thuật viên: `IN_PROGRESS`, Technician nhận thông báo công việc mới.
  - SLA tính giờ phản hồi bắt đầu đếm ngược.

#### TC-MAINT-02: Báo giá linh kiện và Phê duyệt chi phí (Maintenance Quote)
- **Mục tiêu:** Nếu phát sinh chi phí thay thế linh kiện, kỹ thuật viên gửi báo giá trước khi sửa.
- **Kết quả mong đợi:**
  - Ticket ở trạng thái `WAITING_APPROVAL`.
  - Owner/Tenant nhận được thông báo báo giá (gồm danh mục linh kiện + đơn giá).
  - Khi người chịu chi phí bấm "Đồng ý", ticket mới được tiếp tục thực hiện và sinh hóa đơn chi phí sửa chữa tương ứng.

---

### 3.11. Trung tâm thông báo & Trò chuyện (Notifications & Chat)

#### TC-NOTI-01: Đánh dấu đã đọc và dismiss badge chấm đỏ
- **Mục tiêu:** Đảm bảo trải nghiệm thông báo chính xác trên giao diện.
- **Kết quả mong đợi:**
  - Khi có thông báo mới, biểu tượng quả chuông hiển thị badge chấm đỏ và số lượng tin chưa đọc.
  - Bấm vào xem chi tiết: Chấm đỏ biến mất, trạng thái thông báo chuyển thành `READ`.

#### TC-CHAT-01: Quyền kênh chat Global đối với khách thuê (Tenant Global Read-only)
- **Mục tiêu:** Khách thuê chỉ được đọc thông báo trên Kênh thông báo chung của tòa nhà, không được spam chat tại kênh này.
- **Kết quả mong đợi:**
  - Tại kênh Chat Chung (Global): Ô nhập tin nhắn của Tenant bị khóa (Disabled) kèm ghi chú *"Kênh này chỉ dành cho Ban quản lý gửi thông báo"*.
  - Tại kênh Chat Riêng với quản lý: Tenant gửi và nhận tin nhắn kèm hình ảnh bình thường.

---

### 3.12. Báo cáo, Thống kê & Xuất dữ liệu (Exports & Statistics)

#### TC-EXP-01: Xuất file PDF Hóa đơn & Hợp đồng thuê
- **Mục tiêu:** Tạo file PDF hóa đơn/hợp đồng chuẩn mực, hỗ trợ tiếng Việt đầy đủ dấu (UTF-8).
- **Kết quả mong đợi:**
  - Trả về header `Content-Type: application/pdf`.
  - Nội dung hóa đơn có logo PhongHub, mã QR thanh toán, bảng chi tiết điện/nước, font chữ tiếng Việt không bị lỗi hiển thị.

#### TC-EXP-02: Xuất Excel danh sách phòng và công nợ (Apache POI)
- **Mục tiêu:** Xuất file bảng tính `.xlsx` phục vụ kế toán quản lý.
- **Kết quả mong đợi:**
  - File Excel tải về mở được trên Microsoft Excel, Google Sheets.
  - Các cột dữ liệu số tiền được định dạng format currency chuẩn, có dòng tổng cộng (Total Row).

---

## 4. BÁO CÁO AUTOMATION TEST & COVERAGE (JACOCO)

### 4.1. Kết quả chạy kiểm thử tự động
- **Tổng số ca kiểm thử:** **1.049 Test Cases**
- **Trạng thái thực thi:**
  - Đạt (Passed): **1.044 tests** (Bao gồm toàn bộ Unit test, Edge case test, Web/UI test, và 36 test PostgreSQL Flyway Migration thật)
  - Thất bại (Failed): **0 tests**
  - Lỗi (Errors): **0 tests**

### 4.2. Thống kê độ bao phủ mã nguồn theo JaCoCo

| Thành phần phân tích | Tổng số phần tử | Bị thiếu (Missed) | Đã bao phủ (Covered) | Tỷ lệ phần trăm |
|---|:---:|:---:|:---:|:---:|
| **Instructions (Bytecode)** | **76,726** | **8,214** | **68,512** | **89%** (Vượt xa chuẩn 75%) |
| **Branches (Nhánh điều kiện)** | **6,504** | **1,807** | **4,697** | **72%** |
| **Lines of Code (Dòng code)** | **14,375** | **1,624** | **12,751** | **88.7%** |
| **Methods (Hàm/Phương thức)** | **3,273** | **310** | **2,963** | **90.5%** |
| **Classes (Lớp đối tượng)** | **573** | **8** | **565** | **98.6%** |

---

## 5. BỘ CÂU HỎI Q&A BẢO VỆ DỰ ÁN (CHUYÊN SÂU TỪ A - Z)

### 5.1. Nhóm câu hỏi Kiến trúc (Architecture)

#### Q1: Tại sao dự án PhongHub lại lựa chọn Kiến trúc Lục giác (Hexagonal Architecture / Ports & Adapters) thay vì Kiến trúc 3 tầng (Layered MVC truyền thống)?
**Trả lời:**
- **Sự độc lập của Nghiệp vụ:** Lõi nghiệp vụ (Domain Entities & Invariants) nằm ở trung tâm, hoàn toàn "tinh khiết" (Pure Java), không phụ thuộc vào bất kỳ framework nào (kể cả Spring Framework, Hibernate, hay PostgreSQL).
- **Dễ dàng thay thế công nghệ (Swappable Adapters):** Dự án có thể chuyển đổi linh hoạt giữa lưu trữ In-memory (để chạy 900+ unit test trong vài chục giây) và lưu trữ PostgreSQL thật; hoặc đổi giữa dịch vụ xác thực Supabase Auth và Local Auth mà không cần sửa đổi bất kỳ dòng code nghiệp vụ nào.
- **Khả năng kiểm thử vượt trội:** Toàn bộ business use cases có thể được test độc lập thông qua Mock Port mà không cần khởi động Spring Container hoặc kết nối mạng.

#### Q2: Ports và Adapters trong PhongHub được phân chia như thế nào?
**Trả lời:**
- **Inbound Ports (Driving):** Các interface use case mà phía ngoài gọi vào, ví dụ: `CreateContractUseCase`, `RecordMeterReadingUseCase`.
- **Inbound Adapters:** Nhận input từ người dùng/client và gọi Inbound Ports, ví dụ: `ContractApiController` (REST API), `PhongHubWebController` (Thymeleaf UI).
- **Outbound Ports (Driven):** Các interface đại diện cho nhu cầu của nghiệp vụ cần giao tiếp với hạ tầng bên ngoài, ví dụ: `ContractRepositoryPort`, `PaymentGatewayPort`, `OcrEnginePort`.
- **Outbound Adapters:** Triển khai các Outbound Ports bằng công nghệ cụ thể, ví dụ: `PostgresContractRepository` (JDBC), `SepayPaymentAdapter` (SePay), `TesseractOcrAdapter` (Tesseract).

---

### 5.2. Nhóm câu hỏi Nghiệp vụ (Business Flow)

#### Q3: Hãy trình bày vòng đời của một Hợp đồng thuê và mối quan hệ ràng buộc với trạng thái Phòng trọ?
**Trả lời:**
- Hợp đồng trải qua các trạng thái: `DRAFT` (Soạn thảo) $\rightarrow$ `ACTIVE` (Đang hiệu lực) $\rightarrow$ `TERMINATED` (Kết thúc bình thường) hoặc `CANCELLED` (Hủy khi còn là nháp) / `BREACHED` (Đơn phương chấm dứt do vi phạm).
- **Ràng buộc bất biến (Business Invariant):**
  - Khi hợp đồng chuyển sang `ACTIVE`, phòng trọ tương ứng bắt buộc phải tự động chuyển sang trạng thái `OCCUPIED`.
  - Một phòng tại một thời điểm **chỉ được phép có duy nhất một hợp đồng có trạng thái cư trú** (`RESIDING`). Không thể kích hoạt hợp đồng mới nếu phòng chưa được giải phóng bàn giao xong từ hợp đồng cũ.

#### Q4: Quy trình Bàn giao phòng (Handover) giải quyết bài toán gì trong quản lý nhà trọ?
**Trả lời:**
- Khắc phục tranh chấp thường gặp nhất giữa chủ nhà và khách thuê khi trả phòng: mâu thuẫn về số tiền điện nước kỳ cuối và bồi thường hư hỏng tài sản.
- Hệ thống áp dụng quy trình 2 đầu: **Biên bản nhận phòng (Check-in)** và **Biên bản trả phòng (Check-out)**. Mọi chỉ số công tơ ban đầu và danh mục tài sản kèm ảnh chụp đều được lưu lại và ký xác nhận. Khi trả phòng, màn hình *Handover Compare* tự động đối chiếu chênh lệch để trừ trực tiếp vào tiền cọc một cách minh bạch.

---

### 5.3. Nhóm câu hỏi Bảo mật (Security & Privacy)

#### Q5: Dự án bảo vệ dữ liệu cá nhân của khách thuê (CCCD, ngày sinh, số điện thoại) như thế nào trước nguy cơ rò rỉ cơ sở dữ liệu?
**Trả lời:**
- Áp dụng kỹ thuật **Mã hóa cấp trường (Field-level Encryption)** bằng thuật toán chuẩn công nghiệp **AES-256-GCM**.
- Mỗi bản ghi được mã hóa với một Initialization Vector (IV) ngẫu nhiên, đảm bảo cùng một số CCCD khi mã hóa nhiều lần sẽ ra các chuỗi mã hóa hoàn toàn khác nhau.
- Để vẫn tìm kiếm được khách thuê theo số CCCD mà không cần giải mã toàn bộ bảng (gây chậm hệ thống), dự án sử dụng **HMAC Blind Index**: tạo ra một hash không thể đảo ngược bằng HMAC-SHA256 với khóa bí mật riêng, dùng trường này làm chỉ mục tìm kiếm chính xác.

#### Q6: Tại sao hệ thống lại ẩn Email Supabase phía sau Username khi đăng nhập?
**Trả lời:**
- Khách thuê nhà trọ và người lớn tuổi thường chỉ quen sử dụng số điện thoại hoặc Tên đăng nhập (Username) ngắn gọn, không muốn nhớ email.
- Supabase Auth mặc định bắt buộc đăng nhập bằng Email/Password. Vì vậy, Adapter tại Backend đóng vai trò ánh xạ Username sang Email kỹ thuật ẩn (`<username>@phonghub.internal`), sau đó giao tiếp bảo mật máy chủ-máy chủ với Supabase API. Điều này vừa giúp tận dụng giải pháp quản lý phiên, refresh token của Supabase, vừa đem lại trải nghiệm thân thiện cho người dùng Việt Nam.

---

### 5.4. Nhóm câu hỏi Thanh toán & Concurrency (Payments & Concurrency)

#### Q7: Cơ chế tích hợp SePay Webhook hoạt động ra sao và xử lý thế nào để chống tấn công giả mạo (Replay Attack / Fake Webhook)?
**Trả lời:**
- Mỗi khi phát sinh giao dịch chuyển khoản ngân hàng qua mã QR VietQR, SePay bắn một HTTP POST Webhook về endpoint `/v1/sepay/webhook`.
- **Cơ chế bảo vệ 3 lớp:**
  1. **API Key Authentication:** Kiểm tra token bí mật trong header request từ SePay.
  2. **Idempotency (Tính khả trùng):** Lưu mã giao dịch ngân hàng (`transaction_id`). Nếu mã này đã từng được xử lý, hệ thống bỏ qua ngay lập tức để tránh cộng tiền/gạch nợ 2 lần khi webhook gửi lại.
  3. **Payment Code Matching:** Nội dung chuyển khoản phải chứa mã thanh toán khớp với hóa đơn đang ở trạng thái chờ thanh toán (`ISSUED`/`OVERDUE`).

#### Q8: Dự án xử lý xung đột đồng thời (Concurrency) như thế nào khi hai nhân viên cùng thao tác sửa thông tin trên một phòng hoặc hợp đồng?
**Trả lời:**
- Áp dụng kỹ thuật **Khóa lạc quan (Optimistic Locking)** thông qua cột `version` (`V35__optimistic_locking_version.sql`).
- Khi đọc dữ liệu lên form, client giữ số `version` hiện tại. Khi gửi lệnh Update, câu lệnh SQL kiểm tra:
  ```sql
  UPDATE rooms SET name = :name, version = version + 1 
  WHERE id = :id AND version = :expectedVersion;
  ```
- Nếu bản ghi đã bị người khác cập nhật trước đó, số dòng bị ảnh hưởng trả về bằng 0, hệ thống lập tức ném ngoại lệ `OptimisticLockingFailureException` và cảnh báo người dùng tải lại trang để xem dữ liệu mới nhất, tránh tình trạng ghi đè mất dữ liệu (Lost Update).

---

### 5.5. Nhóm câu hỏi Database & Performance (Data & Caching)

#### Q9: Dự án áp dụng Caching Caffeine ở những vị trí nào và chiến lược xóa cache (Eviction) ra sao?
**Trả lời:**
- Caching tầng ứng dụng sử dụng **Caffeine In-Memory Cache** (cấu hình trong [`PhongHubCaches`](file:///d:/HOCTAP/Java%20spring%202/phonghub/src/main/java/com/phonghub/config/PhongHubCaches.java)) cho các dữ liệu được đọc thường xuyên nhưng ít thay đổi:
  - Danh sách tòa nhà trọ theo Owner (`owner-properties`).
  - Danh sách phòng trọ theo nhà (`property-rooms`).
  - Thống kê chỉ số Dashboard tổng quan.
- **Chiến lược Eviction:**
  - Cache có thời gian sống (TTL - Time to Live) 10-15 phút.
  - Khi có bất kỳ hành động thêm/sửa/xóa phòng, duyệt nhà trọ hoặc ký hợp đồng mới, các phương thức nghiệp vụ gắn annotation `@CacheEvict` để xóa cache liên quan ngay lập tức, đảm bảo tính nhất quán dữ liệu.

#### Q10: Chiến lược quản lý Database Migration của dự án với Flyway có điểm gì đặc biệt?
**Trả lời:**
- Tách biệt rõ ràng giữa **Production Schema Migrations** (`db/migration` từ V1 đến V47) và **Local Demo Fixtures** (`db/seed/docker`).
- Toàn bộ thay đổi về bảng, khóa ngoại, index và trigger đều được version hóa qua Flyway script SQL chuẩn, có thể tự động áp dụng khi triển khai CI/CD hoặc container hóa.
- Dữ liệu demo chỉ chạy ở môi trường `docker`/`local`, tuyệt đối không chạy đè dữ liệu giả vào môi trường `staging` hoặc `prod`.
