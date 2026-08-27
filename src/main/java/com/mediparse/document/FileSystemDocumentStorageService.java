package com.mediparse.document;

import com.mediparse.config.StorageProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class FileSystemDocumentStorageService implements DocumentStorageService {

    private static final DateTimeFormatter MONTH_PATH = DateTimeFormatter.ofPattern("yyyy/MM");

    private final Path root;

    public FileSystemDocumentStorageService(StorageProperties properties) {
        this.root = Path.of(properties.rootPath()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create storage root at " + root, e);
        }
    }

    @Override
    public StoredFile store(InputStream content, String suggestedFilename) throws IOException {
        String extension = Filenames.extensionWithoutDot(suggestedFilename);
        String suffix = extension.isEmpty() ? "" : "." + extension;
        String relativePath = LocalDate.now().format(MONTH_PATH) + "/" + UUID.randomUUID() + suffix;
        Path target = resolveWithinRoot(relativePath);
        Files.createDirectories(target.getParent());

        MessageDigest digest = newSha256();
        try (DigestInputStream digestStream = new DigestInputStream(content, digest)) {
            Files.copy(digestStream, target);
        }

        long sizeBytes = Files.size(target);
        String hash = HexFormat.of().formatHex(digest.digest());
        return new StoredFile(relativePath, sizeBytes, hash);
    }

    @Override
    public InputStream load(String relativePath) throws IOException {
        return Files.newInputStream(resolveWithinRoot(relativePath));
    }

    @Override
    public void delete(String relativePath) throws IOException {
        Files.deleteIfExists(resolveWithinRoot(relativePath));
    }

    private Path resolveWithinRoot(String relativePath) {
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Refusing to resolve path outside storage root: " + relativePath);
        }
        return resolved;
    }

    private MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available on this JVM", e);
        }
    }
}
