package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.InvoiceReminderPort;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryInvoiceReminderRepository implements InvoiceReminderPort {

    private record Key(UUID invoiceId, LocalDate dueDate) {}

    private final Set<Key> sent = ConcurrentHashMap.newKeySet();

    @Override
    public boolean markDueReminder(UUID invoiceId, LocalDate dueDate) {
        return sent.add(new Key(invoiceId, dueDate));
    }

    public void clear() {
        sent.clear();
    }
}
