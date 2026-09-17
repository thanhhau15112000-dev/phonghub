package com.phonghub.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest
class ProfileSeparationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private PropertyRepositoryPort propertyRepositoryPort;

    @Test
    void inMemoryRepositoriesAndSeederAreActiveInDefaultProfile() {
        // Assert that the InMemoryPropertyRepository bean is active
        assertTrue(propertyRepositoryPort instanceof InMemoryPropertyRepository);

        // Assert that seedInitialData bean was executed
        assertTrue(applicationContext.containsBean("seedInitialData"));
        assertNotNull(applicationContext.getBean("seedInitialData"));

        // Assert that property repository contains seeded properties
        assertTrue(propertyRepositoryPort.findAll().size() >= 2);
    }
}
