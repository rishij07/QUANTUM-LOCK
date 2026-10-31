package com.quantumlock.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qlock")
public record QlockProperties(String jwtSecret, int jwtTtlMinutes, String kekBase64, boolean demoMode, Minio minio) {
    public record Minio(String endpoint, String accessKey, String secretKey, String bucket) {}
}
