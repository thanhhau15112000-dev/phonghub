package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.phonghub.adapter.in.scheduling.ContractExpiryScheduler;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.application.port.in.ContractLifecycleUseCase.RunResult;
import com.phonghub.domain.model.ContractStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/** #87: khi bật job, scheduler được tạo và chạy trong transaction của ứng dụng (profile test tắt job mặc định). */
@TestPropertySource(properties = "phonghub.contracts.expiry-job-enabled=true")
class ContractExpirySchedulerIntegrationTest extends SeededWebTest {

    @Autowired private ContractExpiryScheduler scheduler;
    @Autowired private InMemoryContractRepository contracts;

    @Test
    @DisplayName("Scheduler expires the overdue contract once and keeps running on later days")
    void schedulerRunsInTransaction() {
        LocalDate end = contracts.findById(DataSeeder.CONTRACT_1_ID).orElseThrow().getEndDate();

        assertEquals(1, scheduler.run(end.plusDays(1)).expired());
        assertEquals(ContractStatus.EXPIRED, contracts.findById(DataSeeder.CONTRACT_1_ID).orElseThrow().getStatus());
        assertEquals(new RunResult(0, 0), scheduler.run(end.plusDays(2)));
    }
}
