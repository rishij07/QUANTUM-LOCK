package com.quantumlock.crypto;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.quantumlock.config.ApiException;
import com.quantumlock.config.TamperingException;

/**
 * AES-256-GCM document encryption.
 * Blob layout: [12-byte random IV][ciphertext][16-byte GCM tag].
 * The AAD binds a ciphertext to its document id + version, so blobs cannot be swapped between documents.
 */
@Service
public class CryptoService {
    public static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String ALGO = "AES/GCM/NoPadding";
    private final SecureRandom random = new SecureRandom();

    public byte[] newDek() {
        byte[] dek = new byte[32];
        random.nextBytes(dek);
        return dek;
    }

    public byte[] encrypt(byte[] plaintext, byte[] dek, byte[] aad) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance(ALGO);
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(dek, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            c.updateAAD(aad);
            byte[] ct = c.doFinal(plaintext);
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ENCRYPTION_FAILED", "Encryption failed closed");
        }
    }

    /** Returns plaintext only if the authentication tag verifies; otherwise throws TamperingException. */
    public byte[] decrypt(byte[] blob, byte[] dek, byte[] aad) {
        if (blob == null || blob.length < IV_BYTES + 16) {
            throw new TamperingException();
        }
        try {
            byte[] iv = Arrays.copyOfRange(blob, 0, IV_BYTES);
            Cipher c = Cipher.getInstance(ALGO);
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(dek, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            c.updateAAD(aad);
            return c.doFinal(blob, IV_BYTES, blob.length - IV_BYTES);
        } catch (AEADBadTagException e) {
            throw new TamperingException();
        } catch (GeneralSecurityException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "DECRYPTION_FAILED", "Decryption failed closed");
        }
    }
}
