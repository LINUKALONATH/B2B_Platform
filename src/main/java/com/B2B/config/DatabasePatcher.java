package com.B2B.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class DatabasePatcher implements CommandLineRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabasePatcher.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        try {
            logger.info("Patching products table to support LONGTEXT base64 images...");
            jdbcTemplate.execute("ALTER TABLE products MODIFY COLUMN image_url LONGTEXT;");
            jdbcTemplate.execute("ALTER TABLE products MODIFY COLUMN additional_images LONGTEXT;");
            logger.info("Database patch success!");
        } catch (Exception e) {
            logger.warn("Database patch skipped or encountered an error (this is usually fine if already patched). Error: {}", e.getMessage());
        }
    }
}
