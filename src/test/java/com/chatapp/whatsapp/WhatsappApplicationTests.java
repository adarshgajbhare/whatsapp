package com.chatapp.whatsapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "logging.level.org.springframework=DEBUG",
    "logging.level.com.chatapp.whatsapp=DEBUG",
    "logging.level.org.hibernate.SQL=DEBUG",
    "logging.level.org.hibernate.type.descriptor.sql=TRACE"
})
class WhatsappApplicationTests {

    @Test
    void contextLoads() {
        try {
            System.out.println("[DEBUG_LOG] Running context load test");
        } catch (Exception e) {
            System.out.println("[DEBUG_LOG] Test failed with exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
