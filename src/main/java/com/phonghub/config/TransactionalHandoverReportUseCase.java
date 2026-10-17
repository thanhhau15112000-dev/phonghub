package com.phonghub.config;

import com.phonghub.application.port.in.HandoverReportUseCase;
import com.phonghub.domain.model.HandoverType;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class TransactionalHandoverReportUseCase implements HandoverReportUseCase {

    private final HandoverReportUseCase delegate;

    public TransactionalHandoverReportUseCase(HandoverReportUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(readOnly = true)
    public Comparison compare(UUID contractId) {
        return delegate.compare(contractId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdfDocument exportPdf(UUID contractId, HandoverType type) {
        return delegate.exportPdf(contractId, type);
    }
}
