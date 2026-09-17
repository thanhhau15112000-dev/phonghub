package com.phonghub.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ProfileSeparationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private PropertyRepositoryPort propertyRepositoryPort;

    @Test
    void inMemoryRepositoriesAndSeederAreActiveInTestProfile() {
        // Repository in-memory chỉ dùng cho profile test.
        assertTrue(propertyRepositoryPort instanceof InMemoryPropertyRepository);

        // Xác nhận fixture Java của test đã được nạp.
        assertTrue(applicationContext.containsBean("seedInitialData"));
        assertNotNull(applicationContext.getBean("seedInitialData"));

        // Xác nhận repository test có dữ liệu fixture.
        assertTrue(propertyRepositoryPort.findAll().size() >= 2);
    }
}
