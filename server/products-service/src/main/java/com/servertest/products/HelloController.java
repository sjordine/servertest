package com.servertest.products;

import java.time.OffsetDateTime;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class HelloController {

    record HelloResponse(String service, String language, String message, OffsetDateTime dbTime) {}

    private final JdbcTemplate jdbc;

    public HelloController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/hello")
    public HelloResponse hello() {
        return jdbc.queryForObject(
                "SELECT message, now() AS db_time FROM greeting ORDER BY id LIMIT 1",
                (rs, rowNum) -> new HelloResponse(
                        "products",
                        "java",
                        rs.getString("message"),
                        rs.getObject("db_time", OffsetDateTime.class)));
    }
}
