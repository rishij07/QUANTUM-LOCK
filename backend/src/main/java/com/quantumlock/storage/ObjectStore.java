package com.quantumlock.storage;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.quantumlock.config.ApiException;
import com.quantumlock.config.QlockProperties;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;

/** Thin MinIO wrapper. Only ciphertext is ever passed to put(). */
@Component
public class ObjectStore {
    private static final Logger log = LoggerFactory.getLogger(ObjectStore.class);
    private final MinioClient client;
    private final String bucket;

    public ObjectStore(QlockProperties props) {
        var m = props.minio();
        this.client = MinioClient.builder().endpoint(m.endpoint()).credentials(m.accessKey(), m.secretKey()).build();
        this.bucket = m.bucket();
    }

    @PostConstruct
    void ensureBucket() {
        for (int attempt = 1; attempt <= 40; attempt++) {
            try {
                if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                    client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                }
                log.info("MinIO bucket '{}' ready", bucket);
                return;
            } catch (Exception e) {
                log.warn("Waiting for MinIO (attempt {}): {}", attempt, e.getMessage());
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw new IllegalStateException("MinIO is not reachable");
    }

    public void put(String key, byte[] ciphertext) {
        try {
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(key)
                    .stream(new ByteArrayInputStream(ciphertext), ciphertext.length, -1)
                    .contentType("application/octet-stream").build());
        } catch (Exception e) {
            throw unavailable();
        }
    }

    public byte[] get(String key) {
        try (InputStream in = client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            return in.readAllBytes();
        } catch (Exception e) {
            throw unavailable();
        }
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE", "Object storage unavailable \u2014 failing closed");
    }
}
