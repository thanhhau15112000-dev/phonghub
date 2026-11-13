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
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RealHttpCompressionBenchmarkTest {

    @Value("${local.server.port}")
    private int port;

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

    private record HttpBenchmarkResult(
        String endpoint,
        String contentEncoding,
        long rawBytes,
        long gzippedBytes,
        double reductionPct,
        long coldMs,
        double avgMs,
        long p90Ms
    ) {}

    private HttpBenchmarkResult benchmarkHttp(HttpClient client, String endpoint, int iterations) throws Exception {
        String adminId = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();
        URI uri = URI.create("http://localhost:" + port + endpoint);

        // 1. Raw request (without gzip) to get uncompressed baseline byte count
        HttpRequest rawReq = HttpRequest.newBuilder(uri)
            .header("X-User-Id", adminId)
            .GET()
            .build();
        HttpResponse<byte[]> rawRes = client.send(rawReq, HttpResponse.BodyHandlers.ofByteArray());
        long rawBytes = rawRes.body().length;

        // 2. Gzip request (Cold request)
        HttpRequest gzipReq = HttpRequest.newBuilder(uri)
            .header("X-User-Id", adminId)
            .header("Accept-Encoding", "gzip")
            .GET()
            .build();

        long coldStart = System.nanoTime();
        HttpResponse<byte[]> coldRes = client.send(gzipReq, HttpResponse.BodyHandlers.ofByteArray());
        long coldMs = (System.nanoTime() - coldStart) / 1_000_000;

        String encoding = coldRes.headers().firstValue("Content-Encoding").orElse("none");
        long gzippedBytes = coldRes.body().length;
        double reductionPct = rawBytes > 0 ? (1.0 - ((double) gzippedBytes / rawBytes)) * 100.0 : 0.0;

        // Verify decompressed content matches
        if ("gzip".equalsIgnoreCase(encoding)) {
            try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(coldRes.body()))) {
                byte[] decompressed = gis.readAllBytes();
                assertEquals(rawBytes, decompressed.length, "Decompressed length must match raw length");
            }
        }

        // 3. Warmup
        for (int i = 0; i < 3; i++) {
            client.send(gzipReq, HttpResponse.BodyHandlers.discarding());
        }

        // 4. Steady-state iterations over real HTTP
        List<Long> latencies = new ArrayList<>();
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            client.send(gzipReq, HttpResponse.BodyHandlers.discarding());
            latencies.add((System.nanoTime() - start) / 1_000_000);
        }

        Collections.sort(latencies);
        long p90 = latencies.get((int) (latencies.size() * 0.90));
        double avg = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);

        return new HttpBenchmarkResult(endpoint, encoding, rawBytes, gzippedBytes, reductionPct, coldMs, avg, p90);
    }

    @Test
    @DisplayName("Measure Real HTTP Tomcat Gzip Compression & Network Latency")
    void testRealHttpTomcatCompression() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        String[] endpoints = new String[] {
            "/dashboard",
            "/properties",
            "/properties/" + DataSeeder.PROP_1_ID,
            "/contracts",
            "/maintenance"
        };

        System.out.println("========================================================================================================================");
        System.out.println("REAL TOMCAT HTTP BENCHMARK (server.compression.enabled=true, Accept-Encoding: gzip)");
        System.out.printf("%-20s | %-8s | %-12s | %-12s | %-10s | %-9s | %-9s | %-9s%n",
            "Endpoint", "Encoding", "Raw (KB)", "Gzip (KB)", "Giảm (%)", "Cold (ms)", "Avg (ms)", "P90 (ms)");
        System.out.println("------------------------------------------------------------------------------------------------------------------------");

        for (String endpoint : endpoints) {
            HttpBenchmarkResult res = benchmarkHttp(client, endpoint, 20);
            System.out.printf("%-20s | %-8s | %-12.2f | %-12.2f | %-10.1f | %-9d | %-9.2f | %-9d%n",
                res.endpoint(),
                res.contentEncoding(),
                res.rawBytes() / 1024.0,
                res.gzippedBytes() / 1024.0,
                res.reductionPct(),
                res.coldMs(),
                res.avgMs(),
                res.p90Ms());

            assertEquals("gzip", res.contentEncoding(), "Tomcat must compress response with gzip");
            assertTrue(res.reductionPct() > 60.0, "Gzip must reduce payload size by at least 60%");
        }
        System.out.println("========================================================================================================================");
    }
}
