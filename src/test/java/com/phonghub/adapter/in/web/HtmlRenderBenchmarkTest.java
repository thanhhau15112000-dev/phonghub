package com.phonghub.adapter.in.web;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMaintenanceTicketRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HtmlRenderBenchmarkTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryUserRepository userRepository;

    @Autowired
    private InMemoryPropertyRepository propertyRepository;

    @Autowired
    private InMemoryStaffPropertyAssignmentRepository assignmentRepository;

    @Autowired
    private InMemoryRoomRepository roomRepository;

    @Autowired
    private InMemoryTenantRepository tenantRepository;

    @Autowired
    private InMemoryContractRepository contractRepository;

    @Autowired
    private InMemoryMaintenanceTicketRepository ticketRepository;

    @BeforeEach
    void resetData() {
        userRepository.clear();
        propertyRepository.clear();
        assignmentRepository.clear();
        roomRepository.clear();
        tenantRepository.clear();
        contractRepository.clear();
        ticketRepository.clear();

        DataSeeder.seedAll(
            userRepository,
            propertyRepository,
            assignmentRepository,
            roomRepository,
            tenantRepository,
            contractRepository,
            ticketRepository
        );
    }

    private record BenchmarkResult(
        String endpoint,
        long coldMs,
        double avgMs,
        long minMs,
        long p50Ms,
        long p90Ms,
        long maxMs,
        int byteSize
    ) {}

    private BenchmarkResult benchmarkEndpoint(String endpoint, int iterations) throws Exception {
        String adminId = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();

        long coldStart = System.nanoTime();
        MvcResult coldResult = mockMvc.perform(get(endpoint).header("X-User-Id", adminId))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("/js/vendor/htmx.min.js")))
            .andExpect(content().string(containsString("htmx:configRequest")))
            .andReturn();
        long coldDurationMs = (System.nanoTime() - coldStart) / 1_000_000;
        int byteSize = coldResult.getResponse().getContentAsString().getBytes().length;

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get(endpoint).header("X-User-Id", adminId))
                .andExpect(status().isOk());
        }

        List<Long> latencies = new ArrayList<>();
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            mockMvc.perform(get(endpoint).header("X-User-Id", adminId))
                .andExpect(status().isOk());
            latencies.add((System.nanoTime() - start) / 1_000_000);
        }

        Collections.sort(latencies);
        long min = latencies.getFirst();
        long max = latencies.getLast();
        long p50 = latencies.get(latencies.size() / 2);
        long p90 = latencies.get((int) (latencies.size() * 0.90));
        double avg = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);

        return new BenchmarkResult(endpoint, coldDurationMs, avg, min, p50, p90, max, byteSize);
    }

    @Test
    @DisplayName("Verify HTMX assets and benchmark optimized render latency")
    void benchmarkHtmlRendering() throws Exception {
        String[] endpoints = new String[] {
            "/dashboard",
            "/properties",
            "/properties/" + DataSeeder.PROP_1_ID,
            "/contracts",
            "/maintenance"
        };

        System.out.println("=========================================================================================================");
        System.out.println("BENCHMARK POST-OPTIMIZATION (spring.thymeleaf.cache=${SPRING_THYMELEAF_CACHE:true}, HTMX included)");
        System.out.printf("%-25s | %-10s | %-10s | %-8s | %-8s | %-8s | %-8s | %-10s%n",
            "Endpoint", "Cold (ms)", "Avg (ms)", "Min (ms)", "P50 (ms)", "P90 (ms)", "Max (ms)", "Size (KB)");
        System.out.println("---------------------------------------------------------------------------------------------------------");

        for (String endpoint : endpoints) {
            BenchmarkResult res = benchmarkEndpoint(endpoint, 30);
            System.out.printf("%-25s | %-10d | %-10.2f | %-8d | %-8d | %-8d | %-8d | %-10.2f%n",
                res.endpoint(), res.coldMs(), res.avgMs(), res.minMs(), res.p50Ms(), res.p90Ms(), res.maxMs(), res.byteSize() / 1024.0);

            // Assert steady-state render is within tight bounds (< 50ms in-memory)
            assertTrue(res.avgMs() < 50.0, "Average render time should be below 50ms with cache enabled");
        }
        System.out.println("=========================================================================================================");
    }

    @Test
    @DisplayName("Verify static HTMX asset and CSRF meta header presence")
    void testHtmxAssetsAndCsrfMeta() throws Exception {
        // 1. Static asset is directly servable
        mockMvc.perform(get("/js/vendor/htmx.min.js"))
            .andExpect(status().isOk());

        // 2. Unauthenticated page (login) loads layout head and htmx without error
        mockMvc.perform(get("/login"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("/js/vendor/htmx.min.js")));

        // 3. Authenticated page includes CSRF headers
        mockMvc.perform(get("/dashboard").header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("/js/vendor/htmx.min.js")))
            .andExpect(content().string(containsString("htmx:configRequest")));
    }
}
