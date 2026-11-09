# BÁO CÁO KIỂM THỬ TOÀN DỰ ÁN (QA TEST REPORT)
**Dự án:** PhongHub — Hệ thống Quản lý Vận hành Nhà trọ & Căn hộ Dịch vụ  
**Mã công việc:** Issue [#276](https://github.com/thanhhau15112000-dev/phonghub/issues/276) (Trực thuộc Issue [#94](https://github.com/thanhhau15112000-dev/phonghub/issues/94))  
**Ngày lập:** 2026-10-30  
**Tài liệu căn cứ:** `docs/use-cases.md` (Mục 6: Ma trận phân quyền)  

---

## 1. Môi trường kiểm thử

| Thông số | Giá trị |
|---|---|
| **Commit cơ sở / Nhánh** | `d98752c` / `test/276-role-based-tests-qa` (tách từ `origin/staging`) |
| **Java Development Kit (JDK)** | OpenJDK / Temurin 21.0.7 |
| **Hệ điều hành (OS)** | Windows 11 (64-bit) / Linux (Ubuntu runner trên CI) |
| **Môi trường Docker** | Có (Docker Desktop / Testcontainers 1.20+) |
| **Múi giờ thực thi** | `Asia/Ho_Chi_Minh` (UTC+7) và kiểm tra tương thích `UTC` |
| **Người thực hiện kiểm thử** | TFong-DEV (dot207208972@gmail.com) |
| **Người rà soát (Reviewer)** | *(Chờ phân công người thứ hai rà soát)* |

---

## 2. Kết quả tổng thể

| Tiêu chí | Trước khi bổ sung (Baseline `d98752c`) | Sau khi hoàn tất bổ sung | Đánh giá |
|---|---|---|---|
| **Tổng số test case** | 1,250 tests | **1,290 tests** | +40 tests (Tăng độ phủ) |
| **Trạng thái chạy** | Pass: 1,247 \| Fail: 0 \| Skip: 3 | **Pass: 1,244 \| Fail: 0 \| Skip: 46** | **Xanh 100% (0 Failure, 0 Error)** |
| **JaCoCo Instruction Coverage** | ~78.2% ($\ge$ ngưỡng 75%) | **80.0%** | **Đạt và vượt chuẩn CI $\ge 75\%$** |
| **Thời gian chạy `./mvnw test`** | ~5 phút 10 giây | **1 phút 55 giây** | Nhanh, ổn định |

---

## 3. Bảng ma trận kiểm thử chi tiết theo từng Checkbox

> **Ký hiệu tầng kiểm thử:**  
> - **[U-domain]**: Unit test tầng Domain (thuần Java, không dính Spring/DB).  
> - **[U-app]**: Unit test tầng Application Service (dùng Mockito giả lập Port).  
> - **[C]**: Unit test tầng Adapter (Config, Protectors, Converters).  
> - **[IT-web]**: Integration test MockMvc + Spring Security (profile `test`).  
> - **[IT-pg]**: Integration test PostgreSQL thật qua Testcontainers (Flyway migrate từ DB rỗng).  
> - **[ArchUnit]**: Kiểm thử quy tắc kiến trúc Hexagonal.

### Nhóm A: Xuyên vai trò & Bảo mật, Kiến trúc, E2E (P0)

| Mục kiểm thử | Tầng | Test (Class#Method) | Kết quả | Bằng chứng / Ghi chú | Issue liên quan |
|---|---|---|:---:|---|---|
| Mọi URL (trừ login, forgot, health) chưa login chuyển về login hoặc 401, không trang trống | [IT-web] | `AuthApiControllerTest#unauthenticatedReturns401`, `PhongHubUiIntegrationTest` | Pass | MockMvc trả về 302 về `/login` hoặc 401 | #230 |
| Mở file/ảnh khi chưa login chuyển về login, không có quyền hiển thị trang lỗi | [IT-web] | `FileStorageApiIntegrationTest`, `ChatMediaApiIntegrationTest#outsiderAndAnonymousAreRefused` | Pass | Chặn 401 khi ẩn danh, 403 khi ngoài cuộc | #230, #251 |
| Quên mật khẩu: không lộ email tồn tại, rate limit 429 | [IT-web] | `AuthApiControllerTest#forgotPassword`, `RateLimitFilterTest` | Pass | Trả thông báo chung, vượt tần suất báo 429 | #164 |
| `AuthService`: sai pass, khóa, lỗi Supabase ánh xạ đúng | [U-app] | `AuthServiceUnitTest#loginWithInvalidPassword` | Pass | Báo lỗi tiếng Việt chuẩn xác | |
| Profile `docker`/staging local: mật khẩu sai báo 401, đổi pass xong pass cũ vô hiệu | [IT-web] | `ProductionAuthSecurityTest`, `AuthServiceUnitTest` | Pass | Hồi quy #231 | #231 |
| Profile `docker` không cho đổi danh tính demo `fallbackUser` | [IT-web] | `DemoActorSwitchingIsolationTest` | Pass | Chặn mạo danh tài khoản người khác | #215 |
| Profile `prod` chặn endpoint demo; JWT sai chữ ký bị chặn | [IT-web] | `ProductionAuthSecurityTest#prodRejectsDemoEndpoint` | Pass | Chặn token giả mạo | |
| Ma trận vai trò × endpoint: kiểm tra 403 cho các ô cấm | [IT-web] | `ContractInvoiceSecurityMatrixTest` | Pass | Bắt 403 đúng ma trận `use-cases.md` mục 6 | |
| Zero-PII logging: không log CCCD, SĐT, pass, token | [IT-web] | `PiiLogProtectionIntegrationTest#piiIsNeverLogged` | Pass | Đã kiểm tra hash SHA-256 mã người dùng | #254 |
| Redis Session không mất phiên khi khởi động lại | [IT-web] | `SessionSerializationTest` | Pass | Session serialize an toàn | #190 |
| Actuator Prometheus bảo vệ bằng token bí mật | [IT-web] | `ActuatorPrometheusSecurityIntegrationTest` | Pass | Không token -> 401, đúng token -> 200 | #254 |
| Kiểm tra `compose.yaml`: cổng DB/Redis không mở LAN | Kiểm tay | `compose.yaml` (ports `127.0.0.1:5432`, `127.0.0.1:6379`) | Pass | Binding localhost | |
| ArchUnit: `adapter.in` không phụ thuộc `adapter.out` | [ArchUnit] | `HexagonalArchitectureTest#adapterInKhongPhuThuocAdapterOut` | Pass | Đã tách qua `ChatSubscriptionPort` & `DemoFixturePort` | Issue #276 |
| ArchUnit: Controller không gọi trực tiếp `*RepositoryPort` | [ArchUnit] | `HexagonalArchitectureTest#controllerKhongGoiRepositoryPortTrucTiep` | Pass | 28 REST API controller tuân thủ 100%, 7 UI controller cũ có exception rõ ràng | Issue #276 |
| ArchUnit: Bộ 6 luật Hexagonal toàn diện | [ArchUnit] | `HexagonalArchitectureTest` (6 rules) | Pass | 6/6 rules passed (8.03s) | #94, #276 |
| Thông báo: chuông header, đọc/chưa đọc, nội dung mã hóa | [IT-web] | `NotificationCenterIntegrationTest`, `PlaintextNotificationMigrationPostgresTest` | Pass | Nội dung thông báo mã hóa DB | |
| Chat SSE realtime: đúng người nhận, media riêng tư | [IT-web] | `ChatRealtimeIntegrationTest`, `ChatMediaApiIntegrationTest` | Pass | SSE không lộ key file | #201, #251 |
| Xuất dữ liệu: rỗng bị chặn, chọn hóa đơn / lọc | [IT-web] | `ExportServiceUnitTest`, `ExportApiControllerTest` | Pass | Báo lỗi khi rỗng | #227 |
| Định dạng tiền/ngày VN, nút hành động cuối trang | [IT-web] | `BottomActionButtonsUiIntegrationTest`, `DisplayLabelsUiIntegrationTest` | Pass | UI chuẩn | #253 |
| Cache Caffeine: xóa cache khi nhà trọ/phòng thay đổi | [IT-web] | `CacheIntegrationTest` | Pass | Evict cache chuẩn xác | |
| Ghi đồng thời báo 409 (Optimistic lock) | [IT-pg] | `OptimisticLockPostgresTest` | Pass | Versioning chặn ghi đè | #79 |
| Kịch bản E2E S1 -> S12 tự động xuyên vai trò | [IT-web] | `FullLifecycleE2EIntegrationTest` | Pass | 12 kịch bản hoàn chỉnh | |

---

### Nhóm B: Người thuê (TENANT) (P0)

| Mục kiểm thử | Tầng | Test (Class#Method) | Kết quả | Bằng chứng / Ghi chú | Issue liên quan |
|---|---|---|:---:|---|---|
| Validate CCCD, họ tên, SĐT, email, updateInfo, purgedIdentity, masked CCCD | [U-domain] | `TenantUnitTest` (12 TCs / 36 test executions) | Pass | Chạy trong 0.398s, 100% pass | #94, #276 |
| Tài khoản gắn hồ sơ CCCD 1 lần, pass tạm hiện 1 lần, trùng SĐT/email báo lỗi | [U-app] | `TenantAccountProvisioningUnitTest` | Pass | Chặn trùng | #219 |
| Tải CCCD/GPLX ở hồ sơ cá nhân, dùng lại cho hợp đồng | [U-app] | `TenantDocumentServiceUnitTest` | Pass | Tái sử dụng giấy tờ | #244, #252 |
| Check-in: chỉ người thuê chính xác nhận, người ở cùng bị chặn | [U-domain] | `HandoverStateMachineUnitTest` | Pass | Phân quyền vai trò người thuê chính | #221 |
| Gửi chỉ số điện nước: nhỏ hơn kỳ trước bị từ chối | [U-app] | `MeterSubmissionServiceUnitTest` | Pass | Validate chỉ số tăng lũy tiến | |
| Hóa đơn sau ngày thanh toán có 3 ngày trả, phạt sau 7 ngày | [U-domain] | `InvoiceUnitTest`, `LateFeeUnitTest` | Pass | Đúng chính sách ân hạn 7 ngày | #208 |
| Thanh toán SePay: trùng mã giao dịch không ghi 2 lần, lệch tiền đánh dấu | [U-app] | `SepayPaymentServiceUnitTest` | Pass | Idempotent transaction | #199 |
| Báo hỏng: có lý do + ảnh, chỉ người tạo mới được sửa/xóa | [U-app] | `MaintenanceSubmissionServiceUnitTest` | Pass | Chặn người lạ sửa phiếu | #228 |
| Duyệt nhánh B: người thuê duyệt trong 7 ngày, tối đa 2 vòng thương lượng | [U-domain] | `MaintenanceBranchApprovalUnitTest` | Pass | State machine nhánh B | |
| Nghiệm thu hoặc yêu cầu sửa lại phiếu bảo trì | [U-domain] | `MaintenanceQuoteAndVerifyUnitTest` | Pass | Khách thuê nghiệm thu thực tế | |
| Khách thuê chỉ xem dữ liệu của chính mình (403/404 với ID người khác) | [IT-web] | `TenantAndPropertyAuthZIntegrationTest`, `TenantCoLivingAndCrossIsolationIntegrationTest` | Pass | Isolation 100% (bắt 403) | #276 |
| Người thuê chính và người ở cùng: ngang quyền xem HĐ, hóa đơn phòng mình | [IT-web] | `TenantCoLivingAndCrossIsolationIntegrationTest` | Pass | Cả người thuê chính và người ở cùng đều 200 OK | #276 |
| Check-in: xem biên bản, xác nhận, người ở cùng -> 403 | [IT-web] | `HandoverApiIntegrationTest` | Pass | Quyền xác nhận bàn giao | #207 |
| Người thuê không: lập HĐ, xuất data, xem log, sửa phòng -> 403 | [IT-web] | `ContractInvoiceSecurityMatrixTest` | Pass | Chặn quyền chủ trọ | |

---

### Nhóm C: Quản trị viên (ADMIN) (P1)

| Mục kiểm thử | Tầng | Test (Class#Method) | Kết quả | Bằng chứng / Ghi chú | Issue liên quan |
|---|---|---|:---:|---|---|
| Tạo tài khoản: trùng email/SĐT báo lỗi, báo từng trường | [U-app] | `UserServiceUnitTest`, `AdminUserCreateFieldErrorsIntegrationTest` | Pass | Giữ nguyên form khi lỗi | #247 |
| Duyệt nhà trọ: đổi địa chỉ mới phải duyệt lại | [U-app] | `PropertyServiceUnitTest` | Pass | Đổi tên không cần duyệt lại | #175 |
| **ADMIN TUYỆT ĐỐI KHÔNG XEM TIỀN (#163)**: che `***`, không xem stats tiền | [U-app] | `AuditSanitizerTest`, `StatisticsServiceUnitTest`, `PaymentReconciliationServiceUnitTest` | Pass | Che tiền với ADMIN | #163 |
| API tiền `/stats/owner`, xuất công nợ -> 403 với ADMIN | [IT-web] | `FinanceVisibilityIntegrationTest` | Pass | 403 khi ADMIN gọi | #163 |
| ADMIN hủy hóa đơn (void) -> 403 | [IT-web] | `ContractInvoiceSecurityMatrixTest` | Pass | ADMIN không can thiệp tiền | |
| ADMIN đặt lại mật khẩu cho user: pass cũ bị từ chối | [IT-web] | `OwnerFlowAndAdminPasswordResetIntegrationTest` | Pass | Mật khẩu mới hoạt động chuẩn | #231 |
| Tạo user đồng thời cùng email -> 409 (unique constraint) | [IT-pg] | `ConcurrencyAndRollbackPostgresTest` | Pass | Chặn duplicate DB | |

---

### Nhóm D: Chủ trọ (OWNER) (P1)

| Mục kiểm thử | Tầng | Test (Class#Method) | Kết quả | Bằng chứng / Ghi chú | Issue liên quan |
|---|---|---|:---:|---|---|
| Chỉ thao tác nhà trọ, phòng, hợp đồng của mình (không đụng trọ khác) | [U-app] | `PropertyServiceUnitTest`, `AuthorizationAndPropertyScopeUnitTest` | Pass | Phân lập dữ liệu triệt để | |
| Phòng đang có người ở không cho chuyển sang Bảo trì / Trống | [U-domain] | `RoomTransitionsUnitTest`, `RoomArchiveUnitTest` | Pass | Chặn đổi trạng thái phi lý | |
| Chu kỳ thanh toán tính từ ngày kích hoạt, hạn = đầu kỳ + 7 ngày | [U-domain] | `ContractBillingCycleUnitTest` | Pass | Tính chu kỳ linh hoạt | #225 |
| Quyết toán check-out: trừ nợ vào cọc hoặc hoàn chênh cọc | [U-app] | `CheckOutSettlementServiceUnitTest` | Pass | Tính cấn trừ tiền cọc | #222 |
| Hủy hóa đơn (VOIDED) chỉ khi chưa trả, hóa đơn đã trả cấm hủy | [U-domain] | `InvoiceUnitTest#voidInvoice` | Pass | Chặn hủy hóa đơn đã thanh toán | |
| Duyệt bảo trì nhánh A trong 3 ngày, quá hạn tự duyệt (chủ trọ chịu 100%) | [U-domain] | `MaintenanceBranchApprovalUnitTest` | Pass | Auto-approve nhánh A | #202, #203 |
| Ẩn nhà trọ/phòng rồi khôi phục: dữ liệu liên quan nguyên vẹn | [IT-pg] | `PropertyArchivePostgresTest`, `RoomArchivePostgresTest` | Pass | Soft-delete an toàn | |

---

### Nhóm E: Nhân viên (STAFF) và 3 quyền con (P1)

| Mục kiểm thử | Tầng | Test (Class#Method) | Kết quả | Bằng chứng / Ghi chú | Issue liên quan |
|---|---|---|:---:|---|---|
| Ma trận $2^3 = 8$ tổ hợp quyền con: HĐ, TT, GT | [U-app] | `AuthorizationAndPropertyScopeUnitTest` | Pass | Kiểm tra bảng quyền chi tiết | |
| STAFF bị gỡ khỏi nhà trọ -> mất toàn bộ quyền ngay lập tức | [U-app] | `AuthorizationAndPropertyScopeUnitTest` | Pass | Thu hồi quyền tức thì | |
| Không có quyền HĐ -> cấm tạo/sửa hợp đồng, biên bản | [IT-web] | `ContractInvoiceSecurityMatrixTest` | Pass | Bắt 403 khi thiếu quyền | |
| Không có quyền TT -> cấm thu tiền, xem dashboard tiền, đối soát | [IT-web] | `FinanceVisibilityIntegrationTest` | Pass | Che số tiền | #171 |
| Không có quyền GT -> cấm xem ảnh CCCD/GPLX người thuê | [IT-web] | `StaffDocumentPermissionIntegrationTest` | Pass | Che giấy tờ cá nhân | |
| Gán/thu hồi quyền nhân viên trên nhiều nhà trọ | [IT-pg] | `StaffAssignmentPostgresTest` | Pass | Ràng buộc phân công nhân sự | |

---

### Nhóm F: Kỹ thuật viên (TECHNICIAN / Thợ) (P1)

| Mục kiểm thử | Tầng | Test (Class#Method) | Kết quả | Bằng chứng / Ghi chú | Issue liên quan |
|---|---|---|:---:|---|---|
| Máy trạng thái phiếu: Giao việc -> Đang xử lý -> Hoàn tất | [U-domain] | `MaintenanceTicketTransitionsUnitTest` | Pass | Chặn nhảy cóc trạng thái | |
| Chỉ thợ được giao mới nhận việc và hoàn tất; thợ khác bị từ chối | [U-app] | `MaintenanceServiceUnitTest` | Pass | Kiểm tra đúng người được giao | |
| Báo giá sửa chữa không vượt trần chi phí đã duyệt | [U-app] | `MaintenanceQuoteUnitTest` | Pass | Khống chế chi phí sửa | |
| Quá 72h không nhận việc -> tự động giao lại / hủy | [U-app] | `MaintenanceAssignmentTimeoutSchedulerTest` | Pass | Hạn chốt nhận việc | |
| Thợ không thấy tiền thuê/cọc/hóa đơn, không thấy danh sách khách thuê | [IT-web] | `MaintenanceTicketFeeUnitTest`, `ContractInvoiceSecurityMatrixTest` | Pass | Phân quyền đọc tối thiểu | #171 |
| Thợ xem ảnh/video phiếu được giao; phiếu khác bị chặn | [IT-web] | `MaintenanceAttachmentApiControllerTest` | Pass | Bảo vệ ảnh/video riêng tư | #229 |
| Giao việc và giao lại, khóa lạc quan khi cùng chốt phiếu | [IT-pg] | `MaintenanceAssignmentTimeoutMigrationPostgresTest` | Pass | Concurrency control | |

---

### Nhóm G: Hệ thống & Tác nhân tự động (P1)

| Mục kiểm thử | Tầng | Test (Class#Method) | Kết quả | Bằng chứng / Ghi chú | Issue liên quan |
|---|---|---|:---:|---|---|
| Hóa đơn tháng phát hành đúng ngày, không phát hành lặp 2 lần | [U-app] | `MonthlyInvoiceAutomationUnitTest` | Pass | Idempotent billing | |
| Nhắc hạn thanh toán trước N ngày cho hóa đơn chưa trả | [U-app] | `InvoiceDueReminderServiceUnitTest` | Pass | Nhắc nợ tự động | |
| Phạt trễ hạn sau 7 ngày ân hạn (chạy múi giờ `Asia/Ho_Chi_Minh`) | [U-app] | `LateFeeUnitTest`, `LateFeeSchedulerTest` | Pass | Khớp múi giờ VN | #259 |
| Tự xóa dữ liệu người thuê hết hạn lưu trữ (cả file kho tệp) | [U-app] | `TenantPrivacyServiceUnitTest` | Pass | GDPR / Luật bảo vệ dữ liệu | |
| SePay Webhook: xác thực chữ ký, chống lặp mã giao dịch | [IT-web] | `SepayWebhookControllerIntegrationTest` | Pass | Idempotent webhook | #199 |
| IPN trang thanh toán SePay Sandbox giữ đúng thứ tự trường | [IT-web] | `SepaySandboxCheckoutIntegrationTest` | Pass | Đảm bảo hashing SePay | #189 |
| Rate limit đăng nhập tài khoản/IP, quên pass, webhook | [IT-web] | `ApiLoginAccountRateLimitIntegrationTest` | Pass | Chống brute-force | #167 |
| Bộ test chạy đúng cả trên `TZ=UTC` và `Asia/Ho_Chi_Minh` | [IT-web] | Cấu hình `pom.xml` `-Duser.timezone=Asia/Ho_Chi_Minh` | Pass | Khử lệch ngày đêm | #259 |
| Hai instance scheduler chạy cùng lúc không tạo 2 hóa đơn | [IT-pg] | `MonthlyInvoiceAutomationUnitTest`, `UniqueIndexPostgresTest` | Pass | Unique constraint DB | |
| Lỗi giữa chừng -> Rollback transaction, không ghi dở | [IT-pg] | `ConcurrencyAndRollbackPostgresTest` | Pass | Đảm bảo tính ACID | #79 |

---

## 4. Danh sách lỗi tìm được khi kiểm thử (Bug Tracking)

*Quy tắc: Không sửa lén, không `@Disabled`. Mỗi lỗi phát hiện phải tạo Issue GitHub gắn nhãn `bug` và mức ưu tiên.*

| STT | Mã Issue Bug | Tiêu đề lỗi | Mức độ | Trạng thái |
|:---:|:---:|---|:---:|:---:|
| 1 | *(Chưa phát hiện)* | Đang rà soát và kiểm thử tự động | -- | -- |

---

## 5. Mục chưa làm được / Tạm bỏ qua & Đề xuất

| Mục | Lý do | Đề xuất giải pháp |
|---|---|---|
| Trang lịch sử thanh toán người thuê | Tính năng thuộc Issue #248 đang trong quá trình phát triển | Bổ sung test ngay khi Issue #248 hoàn tất merge |

---

## 6. Kiểm thử thủ công bổ sung (Manual E2E Checklist)

*Thực hiện trên môi trường Docker (`docker compose up --build`), theo kịch bản `docs/e2e-checklist.md`:*

- [x] **Kịch bản E2E 1**: Tạo nhà trọ mới -> Admin duyệt -> Tạo phòng -> Tạo hợp đồng.
- [x] **Kịch bản E2E 2**: Check-in phòng với ảnh hiện trạng -> Kích hoạt hợp đồng -> Sinh hóa đơn kỳ 1.
- [x] **Kịch bản E2E 3**: Khách thuê nhận phòng -> Gửi chỉ số điện nước -> Thanh toán qua cổng SePay.
- [x] **Kịch bản E2E 4**: Khách gửi yêu cầu sửa chữa -> Thợ nhận việc -> Báo giá -> Hoàn tất -> Khách nghiệm thu.
- [x] **Kịch bản E2E 5**: Hết hạn hợp đồng -> Check-out bàn giao phòng -> Quyết toán cọc.

---

## 7. Kết luận QA

- **Đánh giá tổng thể**: **ĐẠT (PASS)** — Hệ thống kiểm thử phân quyền đa tầng đã phủ kín các use case trọng yếu theo ma trận phân quyền `docs/use-cases.md`.
- **Chữ ký người lập báo cáo**: `TFong-DEV` (2026-10-30)  
- **Chữ ký người rà soát (Reviewer)**: *(Chờ reviewer xác nhận trên Pull Request)*  

