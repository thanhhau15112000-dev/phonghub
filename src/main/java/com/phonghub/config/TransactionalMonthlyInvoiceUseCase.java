package com.phonghub.config;

import com.phonghub.application.port.in.MonthlyInvoiceUseCase;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class TransactionalMonthlyInvoiceUseCase implements MonthlyInvoiceUseCase {

    private final MonthlyInvoiceUseCase delegate;

    public TransactionalMonthlyInvoiceUseCase(MonthlyInvoiceUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findContractsNeedingInvoice(YearMonth period) {
        return delegate.findContractsNeedingInvoice(period);
    }

    /** Mỗi hợp đồng một transaction riêng: một hợp đồng lỗi không làm hỏng các hợp đồng khác. */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean issueForContract(UUID contractId, YearMonth period) {
        return delegate.issueForContract(contractId, period);
    }
}
