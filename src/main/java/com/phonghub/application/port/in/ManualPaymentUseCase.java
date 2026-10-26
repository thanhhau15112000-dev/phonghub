package com.phonghub.application.port.in;

import com.phonghub.domain.model.PaymentClaim;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Thanh toán ngoài cổng SePay (#205): người thuê báo đã thanh toán, người có quyền thu tiền xác nhận hoặc từ chối,
 * hoặc ghi nhận trực tiếp. Xác nhận cộng tiền vào hóa đơn qua cùng đường với webhook (không ghi trạng thái trực tiếp).
 */
public interface ManualPaymentUseCase {

    /** Người thuê của hóa đơn báo đã thanh toán, chờ xác nhận. */
    PaymentClaim submitClaim(UUID invoiceId, BigDecimal amount, PaymentClaim.Method method, String note);

    /** Người có quyền thu tiền ghi nhận trực tiếp một khoản đã thu (xác nhận ngay). */
    PaymentClaim recordPayment(UUID invoiceId, BigDecimal amount, PaymentClaim.Method method, String note);

    /** Xác nhận đã nhận tiền: cộng vào hóa đơn. Xác nhận lần hai trả 409 và không cộng thêm. */
    PaymentClaim confirm(UUID claimId);

    /** Từ chối yêu cầu, bắt buộc có lý do; người thuê được thông báo. */
    PaymentClaim reject(UUID claimId, String reason);

    /** Yêu cầu chờ xác nhận của nhà trọ (chỉ người có quyền thu tiền). */
    List<PaymentClaim> listPending(UUID propertyId);

    /** Lịch sử yêu cầu của hợp đồng: người thuê của hợp đồng hoặc người có quyền xem tiền của nhà trọ. */
    List<PaymentClaim> listForContract(UUID contractId);
}
