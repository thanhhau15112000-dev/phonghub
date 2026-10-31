package com.phonghub.migration;

import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Property;
import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Khởi động cả ứng dụng với profile docker (xác thực local-demo, không cần Supabase) trên CSDL trống: Flyway phải tạo schema trước khi Hibernate kiểm tra
 * ánh xạ entity (validate), sau đó repository JPA đọc được dữ liệu mẫu.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles({"docker", "local-demo"})
class DockerProfileJpaBootPostgresTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("phonghub.security.tenant-data.key-id", () -> "v1");
        registry.add("phonghub.security.tenant-data.encryption-key",
            () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        registry.add("phonghub.security.tenant-data.hmac-key",
            () -> "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=");
    }

    @Autowired private PropertyRepositoryPort properties;
    @Autowired private RoomRepositoryPort rooms;
    @Autowired private ContractRepositoryPort contracts;
    @Autowired private DataSource dataSource;

    @Test
    @DisplayName("Pool Hikari nhận cấu hình spring.datasource.hikari.*")
    void hikariPoolUsesConfiguredSettings() {
        HikariDataSource hikari = assertInstanceOf(HikariDataSource.class, dataSource);
        assertEquals("phonghub-db", hikari.getPoolName());
        assertEquals(5, hikari.getMaximumPoolSize());
        assertEquals(1, hikari.getMinimumIdle());
        assertEquals(10000, hikari.getConnectionTimeout());
    }

    @Test
    @DisplayName("Boot trên CSDL trống: migrate rồi validate, đọc dữ liệu mẫu qua JPA")
    void bootsOnEmptyDatabaseAndReadsSeedThroughJpa() {
        List<Property> all = properties.findAll();
        assertTrue(all.stream().anyMatch(property -> !rooms.findByPropertyId(property.id()).isEmpty()));

        Contract contract = contracts.findAll().getFirst();
        Contract reloaded = contracts.findById(contract.getId()).orElseThrow();
        assertEquals(contract.getOccupants(), reloaded.getOccupants());
        assertEquals(contract.getVersion(), reloaded.getVersion());
    }
}
