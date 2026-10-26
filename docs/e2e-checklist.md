# Checklist E2E theo luồng nghiệp vụ

Chạy trên nhánh tích hợp `feat/integration-219-225` (profile `docker`, Postgres + Redis). Tài khoản demo: admin, owner1, staff1, tech1, tenant1; tài khoản người thuê mới được tạo từ màn hình "Thêm người thuê" (mật khẩu tạm hiển thị một lần).

Cột "Test" là test tự động đã khóa hành vi đó; cột "UI" đánh dấu bước đã bấm thử trên trình duyệt.

## 1. Chuẩn bị

| # | Bước | Kỳ vọng | Test | UI |
|---|------|---------|------|----|
| 1.1 | Admin tạo chủ trọ | Tài khoản chủ trọ đăng nhập được | AdminUserEditIntegrationTest | - |
| 1.2 | Chủ trọ tạo nhà trọ, admin duyệt | Nhà trọ VERIFIED mới tạo được phòng/hợp đồng | (chưa có test riêng; kiểm tra tay) | - |
| 1.3 | Chủ trọ tạo phòng | Phòng AVAILABLE, có hiện trạng ban đầu (#220) | HandoverApiIntegrationTest | - |
| 1.4 | Tạo người thuê kèm tài khoản (CCCD, SĐT, email) | Tài khoản + hồ sơ tạo một lần; trùng email/SĐT báo 409 (#219) | TenantAccountProvisioningUnitTest, TenantPickerUiIntegrationTest | Có |
| 1.5 | Gán staff và quyền (thu tiền, kiểm tra, xử lý báo hỏng), gán kỹ thuật viên | Quyền theo nhà trọ có hiệu lực | PropertyStaffApiIntegrationTest, PropertyStaffUiIntegrationTest | - |

## 2. Hợp đồng và check-in

| # | Bước | Kỳ vọng | Test | UI |
|---|------|---------|------|----|
| 2.1 | Chủ trọ tạo hợp đồng (chọn người thuê có tài khoản) | Hợp đồng DRAFT; thông báo hướng dẫn lập biên bản check-in | ContractDraftEditIntegrationTest | Có |
| 2.2 | Lập biên bản check-in, nhập chỉ số, hiện trạng | Lưu được; thiếu ảnh phòng/điện/nước thì không gửi được (thông báo tiếng Việt) | HandoverApiIntegrationTest, HandoverPhotoIntegrationTest | Có |
| 2.3 | Tải ảnh, gửi người thuê, người thuê xác nhận, quản lý duyệt | Hợp đồng ACTIVE, phòng OCCUPIED, hóa đơn kỳ đầu phát hành (#221) | FullLifecycleE2EIntegrationTest | Chưa (cần upload tệp) |
| 2.4 | Kích hoạt trực tiếp không qua check-in | Bị chặn 409 | ContractLifecycleActionsIntegrationTest | - |
| 2.5 | Chu kỳ thanh toán | Tính từ ngày kích hoạt, hạn = đầu kỳ + 7 ngày (#225) | MonthEndInvoiceRolloverIntegrationTest | Có (bảng chu kỳ) |

## 3. Báo hỏng

| # | Bước | Kỳ vọng | Test |
|---|------|---------|------|
| 3.1 | Người thuê gửi phiếu (lý do, ảnh) | Phiếu REPORTED, thông báo cho người xử lý | MaintenanceBranchFlowIntegrationTest |
| 3.2 | Staff chốt nhánh A (hao mòn) | PENDING_OWNER_APPROVAL, hạn 3 ngày | MaintenanceBranchApprovalUnitTest |
| 3.3 | Chủ trọ duyệt / từ chối | Duyệt: giao được kỹ thuật viên, chủ trọ chịu 100% | MaintenanceBranchFlowIntegrationTest |
| 3.4 | Quá 3 ngày chưa duyệt | Job tự duyệt (escalate), có thông báo nhắc trước hạn | MaintenanceBranchApprovalUnitTest |
| 3.5 | Staff chốt nhánh B (lỗi người thuê) và lập báo giá theo dòng | PENDING_TENANT_APPROVAL, báo giá có version | MaintenanceBranchFlowIntegrationTest |
| 3.6 | Người thuê đồng ý / thương lượng (tối đa 2 vòng) | Đồng ý: được giao thợ; thương lượng: yêu cầu báo giá mới | MaintenanceBranchApprovalUnitTest |
| 3.7 | Quá 7 ngày người thuê không phản hồi | Phiếu CANCELLED | MaintenanceBranchApprovalUnitTest |
| 3.8 | Chi phí sửa vượt tổng đã duyệt | Bị chặn; báo giá bổ sung cộng vào mức trần | MaintenanceBranchApprovalUnitTest |
| 3.9 | Giao kỹ thuật viên quá 72 giờ không xử lý | Giao lại/hủy theo #223 | MaintenanceAssignmentTimeoutIntegrationTest |

## 4. Sửa chữa và thanh toán

| # | Bước | Kỳ vọng | Test |
|---|------|---------|------|
| 4.1 | Kỹ thuật viên hoàn thành, gửi kết quả và ảnh | Người thuê, staff, chủ trọ nhận thông báo | MaintenanceBranchFlowIntegrationTest |
| 4.2 | Phát hóa đơn phần người thuê chịu | Hóa đơn MAINTENANCE theo bên chịu phí | MaintenanceBranchFlowIntegrationTest |
| 4.3 | Người thuê báo đã chuyển khoản/tiền mặt | Yêu cầu PENDING, chưa cộng tiền; chỉ một yêu cầu chờ trên mỗi hóa đơn | ManualPaymentIntegrationTest, PaymentClaimPostgresTest |
| 4.4 | Chủ trọ/staff có quyền thu tiền xác nhận hoặc từ chối (kèm lý do) | Xác nhận: cộng tiền, hóa đơn PAID/PARTIALLY_PAID; xác nhận lần hai 409 | ManualPaymentIntegrationTest |
| 4.5 | Thu trực tiếp (ghi nhận tại quầy) | Quyền: chủ trọ, staff được cấp; admin/kỹ thuật viên/chủ trọ khác bị 403 | ManualPaymentIntegrationTest |

## 5. Check-out

| # | Bước | Kỳ vọng | Test |
|---|------|---------|------|
| 5.1 | Người thuê yêu cầu check-out | Mở biên bản CHECK_OUT | CheckOutApiIntegrationTest |
| 5.2 | Staff kiểm tra phòng, ghi hư hỏng | Hư hỏng nhẹ/nặng cấu hình khấu trừ cọc | HandoverReportApiIntegrationTest |
| 5.3 | Còn nợ hóa đơn | Không kết thúc được; phải thanh toán hoặc bù trừ cọc | FullLifecycleE2EIntegrationTest |
| 5.4 | Kết thúc trước hạn | Hóa đơn CANCELLATION = 50% tiền thuê kỳ hiện tại (+ tiện ích); kỳ đã trả đủ thì thành khoản hoàn | FullLifecycleE2EIntegrationTest, EarlyTerminationBillingServiceTest |
| 5.5 | Duyệt check-out | Hợp đồng ENDED, hoàn/ bù trừ cọc (DEPOSIT_OFFSET), phòng về AVAILABLE (hoặc MAINTENANCE nếu hư hỏng) | FullLifecycleE2EIntegrationTest |
| 5.6 | Lịch sử thuê | Hợp đồng, biên bản check-in/check-out, ảnh giữ lại | HandoverReportApiIntegrationTest |

## Giới hạn đã biết

- Bước tải ảnh (2.3) chưa được bấm bằng tay trên trình duyệt vì công cụ không upload tệp; luồng được test bằng MockMvc.
- #202/#203/#204/#205 chưa có nhánh/PR riêng; phần hiện thực nằm trong nhánh tích hợp này.
- Gán giao dịch SePay cần xem xét (#205) chưa làm.
- #224/#213 (typed-confirmation) chưa tích hợp.
- Mọi commit chỉ ở local, chưa push.
