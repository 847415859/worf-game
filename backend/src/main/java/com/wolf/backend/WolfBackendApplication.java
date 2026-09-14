package com.wolf.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class WolfBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(WolfBackendApplication.class, args);
    }
}
