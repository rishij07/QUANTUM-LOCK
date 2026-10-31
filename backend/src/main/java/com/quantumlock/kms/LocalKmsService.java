package com.quantumlock.kms;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.quantumlock.config.ApiException;
import com.quantumlock.config.QlockProperties;

/** Demo KMS: the KEK comes from the environment (QL_KEK_BASE64). It is never stored in the database or MinIO. */
@Service
public class LocalKmsService implements KmsService {
    private static final String KEY_ID = "local-kek-v1";
    private final byte[] kek;
    private final byte[] blindKey;
    private final SecureRandom random = new SecureRandom();

    public LocalKmsService(QlockProperties props) {
        this.kek = Base64.getDecoder().decode(props.kekBase64().trim());
        if (kek.length != 32) {
            throw new IllegalStateException("QL_KEK_BASE64 must decode to exactly 32 bytes (AES-256)");
        }
        this.blindKey = hmac(kek, "quantum-lock/blind-index/v1".getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String activeKeyId() {
        return KEY_ID;
    }

    @Override
    public byte[] wrap(byte[] dek, String context) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(kek, "AES"), new GCMParameterSpec(128, iv));
            c.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            byte[] ct = c.doFinal(dek);
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw keyFailure();
        }
    }

    @Override
    public byte[] unwrap(byte[] wrapped, String keyId, String context) {
        if (!KEY_ID.equals(keyId) || wrapped == null || wrapped.length < 12 + 16) {
            throw keyFailure();
        }
        try {
            byte[] iv = Arrays.copyOfRange(wrapped, 0, 12);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(kek, "AES"), new GCMParameterSpec(128, iv));
            c.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            return c.doFinal(wrapped, 12, wrapped.length - 12);
        } catch (GeneralSecurityException e) {
            throw keyFailure();
        }
    }

    @Override
    public String blindIndex(byte[] data, String scope) {
        Mac mac = newMac(blindKey);
        mac.update(scope.getBytes(StandardCharsets.UTF_8));
        mac.update((byte) 0);
        return HexFormat.of().formatHex(mac.doFinal(data));
    }

    private static ApiException keyFailure() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "KEY_UNAVAILABLE", "Key retrieval failed \u2014 failing closed",
                "The data key could not be unwrapped. No plaintext was released.");
    }

    private static Mac newMac(byte[] key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        return newMac(key).doFinal(data);
    }
}
