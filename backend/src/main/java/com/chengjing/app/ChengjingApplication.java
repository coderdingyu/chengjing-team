package com.chengjing.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.chengjing")
public class ChengjingApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChengjingApplication.class, args);
    }
}
