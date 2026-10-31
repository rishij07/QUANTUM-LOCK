package com.quantumlock.kms;

/**
 * Minimal KMS abstraction. Swap LocalKmsService for AWS KMS / Vault / HSM without touching callers.
 * The KEK never leaves the implementation; callers only see wrapped DEKs.
 */
public interface KmsService {
    String activeKeyId();

    /** Encrypt (wrap) a data-encryption key under the KEK. {@code context} is authenticated, not secret. */
    byte[] wrap(byte[] dek, String context);

    /** Decrypt (unwrap) a DEK. Fails closed with ApiException(503) when the key cannot be used. */
    byte[] unwrap(byte[] wrapped, String keyId, String context);

    /** Keyed hash of plaintext used for duplicate detection (reveals nothing without the KEK). */
    String blindIndex(byte[] data, String scope);
}
