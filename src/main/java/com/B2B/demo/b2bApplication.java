package com.B2B.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.B2B")
@EnableJpaRepositories(basePackages = "com.B2B.repository")
@EntityScan(basePackages = "com.B2B.model")
public class b2bApplication {

    public static void main(String[] args) {
        SpringApplication.run(b2bApplication.class, args);
    }
}