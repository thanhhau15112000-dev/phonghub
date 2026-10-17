package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.MeterReadingRepositoryPort;
import com.phonghub.domain.exception.MeterReadingConflictException;
import com.phonghub.domain.model.MeterReading;
import com.phonghub.domain.model.MeterType;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryMeterReadingRepository implements MeterReadingRepositoryPort {

    private final Map<UUID, MeterReading> store = new ConcurrentHashMap<>();

    @Override
    public synchronized MeterReading save(MeterReading reading) {
        boolean duplicate = store.values().stream().anyMatch(existing -> !existing.id().equals(reading.id())
            && existing.roomId().equals(reading.roomId()) && existing.meterType() == reading.meterType()
            && existing.period().equals(reading.period()));
        if (duplicate) {
            throw new MeterReadingConflictException("Phòng đã có chỉ số cho kỳ " + reading.period());
        }
        store.put(reading.id(), reading);
        return reading;
    }

    @Override
    public Optional<MeterReading> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<MeterReading> findByRoomId(UUID roomId) {
        return store.values().stream().filter(reading -> reading.roomId().equals(roomId))
            .sorted(Comparator.comparing(MeterReading::period).reversed()
                .thenComparing(MeterReading::meterType)).toList();
    }

    @Override
    public Optional<MeterReading> findLatest(UUID roomId, MeterType type) {
        return store.values().stream()
            .filter(reading -> reading.roomId().equals(roomId) && reading.meterType() == type)
            .max(Comparator.comparing(MeterReading::period));
    }

    @Override
    public Optional<MeterReading> findByPeriod(UUID roomId, MeterType type, int year, int month) {
        return store.values().stream().filter(reading -> reading.roomId().equals(roomId)
            && reading.meterType() == type && reading.periodYear() == year && reading.periodMonth() == month)
            .findFirst();
    }

    public void clear() {
        store.clear();
    }
}
