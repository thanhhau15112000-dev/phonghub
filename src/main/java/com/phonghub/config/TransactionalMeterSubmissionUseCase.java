package com.phonghub.config;

import com.phonghub.application.port.in.MeterSubmissionUseCase;
import com.phonghub.domain.model.MeterReading;
import com.phonghub.domain.model.MeterSubmission;
import com.phonghub.domain.model.MeterType;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class TransactionalMeterSubmissionUseCase implements MeterSubmissionUseCase {

    private final MeterSubmissionUseCase delegate;

    public TransactionalMeterSubmissionUseCase(MeterSubmissionUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MeterSubmission submit(UUID roomId, MeterType meterType, BigDecimal declaredValue, String contentType,
                                  byte[] content) {
        return delegate.submit(roomId, meterType, declaredValue, contentType, content);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeterSubmission> listForRoom(UUID roomId) {
        return delegate.listForRoom(roomId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeterSubmission> listForProperty(UUID propertyId, MeterSubmission.Status status) {
        return delegate.listForProperty(propertyId, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MeterReading accept(UUID submissionId, AcceptCommand command) {
        return delegate.accept(submissionId, command);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MeterSubmission reject(UUID submissionId, String reason) {
        return delegate.reject(submissionId, reason);
    }

    @Override
    @Transactional(readOnly = true)
    public URI photoSignedUrl(UUID submissionId) {
        return delegate.photoSignedUrl(submissionId);
    }
}
