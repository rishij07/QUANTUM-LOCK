package com.quantumlock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class QuantumLockApplication {
    public static void main(String[] args) {
        SpringApplication.run(QuantumLockApplication.class, args);
    }
}
