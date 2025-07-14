package com.chatapp.whatsapp.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class HealthController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/health/db")
    public Map<String, Object> checkDatabase() {
        Map<String, Object> response = new HashMap<>();
        try {
            String result = jdbcTemplate.queryForObject("SELECT 1", String.class);
            response.put("status", "Connected");
            response.put("database", "PostgreSQL");
            response.put("test_query", "SELECT 1 = " + result);
        } catch (Exception e) {
            response.put("status", "Failed");
            response.put("error", e.getMessage());
        }
        return response;
    }

    @GetMapping("/health/db-version")
    public Map<String, Object> getDatabaseVersion() {
        Map<String, Object> response = new HashMap<>();
        try {
            String version = jdbcTemplate.queryForObject("SELECT version()", String.class);
            response.put("status", "Connected");
            response.put("version", version);
        } catch (Exception e) {
            response.put("status", "Failed");
            response.put("error", e.getMessage());
        }
        return response;
    }
}