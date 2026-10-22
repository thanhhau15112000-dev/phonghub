package com.phonghub.application.port.out;

import com.phonghub.domain.model.SepayGatewayOrder;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SepayGatewayOrderRepositoryPort {
    Optional<SepayGatewayOrder> findPendingByInvoiceIdForUpdate(UUID invoiceId);
    Optional<SepayGatewayOrder> findByInvoiceNumberForUpdate(String orderInvoiceNumber);
    Optional<SepayGatewayOrder> findByTransactionId(String transactionId);
    void savePending(SepayGatewayOrder order);
    boolean markPaid(String orderInvoiceNumber, String transactionId, Instant paidAt);
    boolean expire(String orderInvoiceNumber);
}
