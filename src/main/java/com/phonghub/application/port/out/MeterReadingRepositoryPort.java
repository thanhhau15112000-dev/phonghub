package com.phonghub.application.port.out;

import com.phonghub.domain.model.MeterReading;
import com.phonghub.domain.model.MeterType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeterReadingRepositoryPort {

    MeterReading save(MeterReading reading);

    Optional<MeterReading> findById(UUID id);

    /** Mới nhất trước. */
    List<MeterReading> findByRoomId(UUID roomId);

    /** Bản ghi của kỳ gần nhất theo loại đồng hồ. */
    Optional<MeterReading> findLatest(UUID roomId, MeterType type);

    Optional<MeterReading> findByPeriod(UUID roomId, MeterType type, int year, int month);
}
