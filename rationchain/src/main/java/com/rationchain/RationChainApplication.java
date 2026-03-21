package com.rationchain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class RationChainApplication {
    public static void main(String[] args) {
        SpringApplication.run(RationChainApplication.class, args);
    }
}
