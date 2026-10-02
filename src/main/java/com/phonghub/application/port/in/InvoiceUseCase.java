package com.phonghub.application.port.in;

import com.phonghub.domain.model.Invoice;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public interface InvoiceUseCase {

    /** Danh sách kỳ thanh toán, mới nhất trước. Quyền xem theo quyền xem hợp đồng. */
    List<Invoice> listInvoicesForContract(UUID contractId);

    /** Một hóa đơn; quyền xem theo quyền xem hợp đồng của hóa đơn. */
    Invoice getInvoice(UUID invoiceId);

    /** Khoản phí sửa chữa của phiếu bảo trì; rỗng nếu chưa có hoặc người gọi không được xem hợp đồng liên quan. */
    java.util.Optional<Invoice> findFeeInvoiceForTicket(UUID ticketId);

    /** ADMIN / STAFF được phân công / OWNER của nhà trọ phát hành kỳ thanh toán tiền thuê. */
    Invoice issueMonthlyInvoice(UUID contractId, YearMonth period);
}
