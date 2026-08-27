package com.mediparse.document;

import com.mediparse.common.BadRequestException;
import com.mediparse.config.StorageProperties;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileValidationServiceTest {

    private static final Path TEST_DATA = Path.of("test-data");

    private final FileValidationService service = new FileValidationService(
            new StorageProperties("./build-tmp", 5_000_000, List.of("pdf", "docx", "txt")));

    @Test
    void acceptsFileWithinSizeAndExtensionAllowlist() {
        service.validateMetadata("report.pdf", 1024);
    }

    @Test
    void rejectsDisallowedExtension() {
        assertThatThrownBy(() -> service.validateMetadata("report.exe", 1024))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unsupported file type");
    }

    @Test
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> service.validateMetadata("report.pdf", 0))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void rejectsFileOverTheSizeLimit() {
        assertThatThrownBy(() -> service.validateMetadata("report.pdf", 10_000_000))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("maximum allowed size");
    }

    @Test
    void acceptsGenuinePdfContent() throws IOException {
        withFixture("lab-report-sample.pdf", in -> service.validateContent(in, "lab-report-sample.pdf"));
    }

    @Test
    void acceptsGenuineDocxContent() throws IOException {
        withFixture("prescription-sample.docx", in -> service.validateContent(in, "prescription-sample.docx"));
    }

    @Test
    void rejectsPlainTextMasqueradingAsPdf() throws IOException {
        withFixture("malformed-wrong-content.pdf", in ->
                assertThatThrownBy(() -> service.validateContent(in, "malformed-wrong-content.pdf"))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessageContaining("does not match its extension"));
    }

    @Test
    void rejectsExecutableDisguisedAsPdf() throws IOException {
        withFixture("disguised-executable.pdf", in ->
                assertThatThrownBy(() -> service.validateContent(in, "disguised-executable.pdf"))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessageContaining("does not match its extension"));
    }

    private void withFixture(String filename, ThrowingConsumer<InputStream> assertion) throws IOException {
        assertThat(Files.exists(TEST_DATA.resolve(filename)))
                .withFailMessage("Missing test fixture: " + filename)
                .isTrue();
        try (InputStream in = Files.newInputStream(TEST_DATA.resolve(filename))) {
            assertion.accept(in);
        }
    }

    @FunctionalInterface
    private interface ThrowingConsumer<T> {
        void accept(T value) throws IOException;
    }
}
