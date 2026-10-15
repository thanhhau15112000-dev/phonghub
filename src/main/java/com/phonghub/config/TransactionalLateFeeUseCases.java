package com.phonghub.config;

import com.phonghub.application.port.in.LateFeeAssessmentUseCase;
import com.phonghub.application.port.in.LateFeePolicyUseCase;
import com.phonghub.domain.model.LateFeePolicy;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Gói transaction cho hai use-case phạt trả trễ; mỗi hóa đơn được tính trong một transaction riêng. */
public final class TransactionalLateFeeUseCases {

    private TransactionalLateFeeUseCases() {}

    public static class Policy implements LateFeePolicyUseCase {

        private final LateFeePolicyUseCase delegate;

        public Policy(LateFeePolicyUseCase delegate) {
            this.delegate = delegate;
        }

        @Override
        @Transactional(readOnly = true)
        public LateFeePolicy getPolicy(UUID propertyId) {
            return delegate.getPolicy(propertyId);
        }

        @Override
        @Transactional(rollbackFor = Exception.class)
        public LateFeePolicy updatePolicy(UpdateCommand command) {
            return delegate.updatePolicy(command);
        }
    }

    public static class Assessment implements LateFeeAssessmentUseCase {

        private final LateFeeAssessmentUseCase delegate;

        public Assessment(LateFeeAssessmentUseCase delegate) {
            this.delegate = delegate;
        }

        @Override
        @Transactional(readOnly = true)
        public List<UUID> findInvoicesToAssess(LocalDate today) {
            return delegate.findInvoicesToAssess(today);
        }

        @Override
        @Transactional(rollbackFor = Exception.class)
        public boolean assessInvoice(UUID invoiceId, LocalDate today) {
            return delegate.assessInvoice(invoiceId, today);
        }
    }
}
