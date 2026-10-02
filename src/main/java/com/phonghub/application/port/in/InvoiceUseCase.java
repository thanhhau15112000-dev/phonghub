package com.phonghub.application.port.in;

import com.phonghub.domain.model.Invoice;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public interface InvoiceUseCase {

    /** Danh sách kỳ thanh toán, mới nhất trước. Quyền xem theo quyền xem hợp đồng. */
    List<Invoice> listInvoicesForContract(UUID contractId);

    /** ADMIN / STAFF được phân công / OWNER của nhà trọ phát hành kỳ thanh toán tiền thuê. */
    Invoice issueMonthlyInvoice(UUID contractId, YearMonth period);
}
