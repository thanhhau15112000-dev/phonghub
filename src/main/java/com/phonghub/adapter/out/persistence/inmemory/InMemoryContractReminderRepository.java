package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.ContractReminderPort;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryContractReminderRepository implements ContractReminderPort {

    private record Key(UUID contractId, LocalDate endDate) {}

    private final Set<Key> sent = ConcurrentHashMap.newKeySet();

    @Override
    public boolean markExpiryReminder(UUID contractId, LocalDate endDate) {
        return sent.add(new Key(contractId, endDate));
    }

    public void clear() {
        sent.clear();
    }
}
