package com.ghostnet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class GhostNetApplicationTests {

    @Test
    @DisplayName("GhostNet context loads successfully")
    void contextLoads() {
        assertTrue(true, "Application context loaded successfully in offline mode");
    }
}
