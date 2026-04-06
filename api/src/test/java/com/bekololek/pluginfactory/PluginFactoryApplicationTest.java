package com.bekololek.pluginfactory;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PluginFactoryApplicationTest {

    @Test
    void contextLoads() {
        // Verifies that the Spring context starts successfully,
        // including Flyway migrations running without errors
    }
}
