package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.MeterSubmissionRepositoryPort;
import com.phonghub.domain.exception.MeterReadingConflictException;
import com.phonghub.domain.model.MeterSubmission;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryMeterSubmissionRepository implements MeterSubmissionRepositoryPort {

    private final Map<UUID, MeterSubmission> store = new ConcurrentHashMap<>();

    @Override
    public synchronized MeterSubmission save(MeterSubmission submission) {
        boolean duplicatePending = submission.status() == MeterSubmission.Status.PENDING
            && store.values().stream().anyMatch(existing -> !existing.id().equals(submission.id())
                && existing.roomId().equals(submission.roomId()) && existing.meterType() == submission.meterType()
                && existing.status() == MeterSubmission.Status.PENDING);
        if (duplicatePending) {
            throw new MeterReadingConflictException("Đã có một lượt gửi chỉ số đang chờ duyệt cho đồng hồ này");
        }
        store.put(submission.id(), submission);
        return submission;
    }

    @Override
    public Optional<MeterSubmission> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<MeterSubmission> findByRoomId(UUID roomId) {
        return store.values().stream().filter(s -> s.roomId().equals(roomId))
            .sorted(Comparator.comparing(MeterSubmission::submittedAt).reversed()).toList();
    }

    @Override
    public List<MeterSubmission> findByRoomIds(Collection<UUID> roomIds) {
        return store.values().stream().filter(s -> roomIds.contains(s.roomId()))
            .sorted(Comparator.comparing(MeterSubmission::submittedAt).reversed()).toList();
    }

    public void clear() {
        store.clear();
    }
}
