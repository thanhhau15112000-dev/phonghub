package com.phonghub.config;

import com.phonghub.application.port.in.UtilityUseCase;
import com.phonghub.domain.model.MeterReading;
import com.phonghub.domain.model.ServiceRate;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class TransactionalUtilityUseCase implements UtilityUseCase {

    private final UtilityUseCase delegate;

    public TransactionalUtilityUseCase(UtilityUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MeterReading recordReading(RecordReadingCommand command) {
        return delegate.recordReading(command);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeterReading> listReadings(UUID roomId) {
        return delegate.listReadings(roomId);
    }

    @Override
    @Transactional(readOnly = true)
    public OcrSuggestion readMeterPhoto(UUID roomId, com.phonghub.domain.model.MeterType meterType,
                                        String contentType, byte[] content) {
        return delegate.readMeterPhoto(roomId, meterType, contentType, content);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MeterReading attachPhoto(UUID readingId, String contentType, byte[] content) {
        return delegate.attachPhoto(readingId, contentType, content);
    }

    @Override
    @Transactional(readOnly = true)
    public URI photoSignedUrl(UUID readingId) {
        return delegate.photoSignedUrl(readingId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ServiceRate createRate(RateCommand command) {
        return delegate.createRate(command);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ServiceRate updateRate(UUID rateId, RateCommand command) {
        return delegate.updateRate(rateId, command);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceRate> listRates(UUID propertyId) {
        return delegate.listRates(propertyId);
    }
}
