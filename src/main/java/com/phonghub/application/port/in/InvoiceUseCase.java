package com.phonghub.application.port.in;

import com.phonghub.domain.model.Invoice;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface InvoiceUseCase {

    /** Danh sách kỳ thanh toán, mới nhất trước. Quyền xem theo quyền xem hợp đồng. */
    List<Invoice> listInvoicesForContract(UUID contractId);

    /** Một hóa đơn; quyền xem theo quyền xem hợp đồng của hóa đơn. */
    Invoice getInvoice(UUID invoiceId);

    /** Khoản phí sửa chữa của phiếu bảo trì; rỗng nếu chưa có hoặc người gọi không được xem hợp đồng liên quan. */
    java.util.Optional<Invoice> findFeeInvoiceForTicket(UUID ticketId);

    /**
     * Cảnh báo theo hóa đơn tiền thuê của hợp đồng (thiếu chỉ số hoặc đơn giá điện/nước nên chưa tính tiền).
     * Chỉ có hóa đơn nào có cảnh báo; quyền xem theo quyền xem hợp đồng.
     */
    Map<UUID, List<String>> utilityWarnings(UUID contractId);

    /** ADMIN / STAFF được phân công / OWNER của nhà trọ phát hành kỳ thanh toán tiền thuê. */
    Invoice issueMonthlyInvoice(UUID contractId, YearMonth period);

    /**
     * Hủy hóa đơn tiền thuê (#89): chuyển VOIDED, không xóa cứng; sau đó phát hành lại được kỳ đó. Cùng quyền với phát
     * hành. Bắt buộc có lý do (400). Từ chối (409) khi hóa đơn đã có tiền, đã trả/đã hủy, đã gộp vào hóa đơn khác, đang
     * gộp phí sửa chữa, hoặc là phí sửa chữa (dùng "miễn phí" ở phiếu bảo trì).
     */
    Invoice voidInvoice(UUID invoiceId, String reason);
}
