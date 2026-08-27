package com.mediparse.document;

import com.mediparse.config.DownloadProperties;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SignedUrlServiceTest {

    private final SignedUrlService signedUrlService = new SignedUrlService(
            new DownloadProperties("test-download-signing-secret", 300));

    @Test
    void signedUrlValidatesForTheSameDocument() {
        UUID documentId = UUID.randomUUID();

        SignedDownload signed = signedUrlService.sign(documentId);

        assertThat(signedUrlService.isValid(documentId, signed.expiresAt(), signed.signature())).isTrue();
    }

    @Test
    void rejectsSignatureForADifferentDocument() {
        UUID documentId = UUID.randomUUID();
        SignedDownload signed = signedUrlService.sign(documentId);

        assertThat(signedUrlService.isValid(UUID.randomUUID(), signed.expiresAt(), signed.signature())).isFalse();
    }

    @Test
    void rejectsTamperedSignature() {
        UUID documentId = UUID.randomUUID();
        SignedDownload signed = signedUrlService.sign(documentId);

        String tampered = signed.signature().equals("0") ? "1" : "0" + signed.signature().substring(1);

        assertThat(signedUrlService.isValid(documentId, signed.expiresAt(), tampered)).isFalse();
    }

    @Test
    void rejectsExpiredLink() {
        UUID documentId = UUID.randomUUID();
        long expiredAt = System.currentTimeMillis() / 1000 - 60;

        SignedUrlService shortLived = new SignedUrlService(new DownloadProperties("test-download-signing-secret", -1));
        SignedDownload signed = shortLived.sign(documentId);

        assertThat(shortLived.isValid(documentId, expiredAt, signed.signature())).isFalse();
    }
}
