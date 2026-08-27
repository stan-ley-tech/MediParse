package com.mediparse.document;

import com.mediparse.config.DownloadProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Issues short-lived, tamper-evident download links (HMAC-SHA256 over the
 * document id and expiry) so a document can be fetched without embedding a
 * bearer token in a URL that ends up in logs, browser history or a shared link.
 */
@Service
public class SignedUrlService {

    private final DownloadProperties properties;

    public SignedUrlService(DownloadProperties properties) {
        this.properties = properties;
    }

    public SignedDownload sign(UUID documentId) {
        long expiresAt = Instant.now().getEpochSecond() + properties.urlTtlSeconds();
        return new SignedDownload(documentId, expiresAt, computeSignature(documentId, expiresAt));
    }

    public boolean isValid(UUID documentId, long expiresAt, String signature) {
        if (Instant.now().getEpochSecond() > expiresAt) {
            return false;
        }
        String expected = computeSignature(documentId, expiresAt);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    private String computeSignature(UUID documentId, long expiresAt) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.signingSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal((documentId + ":" + expiresAt).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Could not compute download signature", e);
        }
    }
}
