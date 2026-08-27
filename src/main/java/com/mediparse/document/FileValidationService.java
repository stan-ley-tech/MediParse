package com.mediparse.document;

import com.mediparse.common.BadRequestException;
import com.mediparse.config.StorageProperties;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

/**
 * Guards against two distinct problems: obviously wrong uploads (wrong
 * extension, empty file, oversized file) caught before we touch disk, and
 * disguised uploads (an executable renamed to .pdf) caught by sniffing the
 * actual file content once it has been written to storage.
 */
@Service
public class FileValidationService {

    private final StorageProperties storageProperties;
    private final Tika tika = new Tika();

    public FileValidationService(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    public void validateMetadata(String filename, long declaredSizeBytes) {
        if (filename == null || filename.isBlank()) {
            throw new BadRequestException("Uploaded file must have a filename");
        }
        String extension = Filenames.extensionWithoutDot(filename);
        if (!storageProperties.allowedExtensions().contains(extension)) {
            throw new BadRequestException("Unsupported file type: ." + extension
                    + ". Allowed types are: " + storageProperties.allowedExtensions());
        }
        if (declaredSizeBytes <= 0) {
            throw new BadRequestException("Uploaded file is empty");
        }
        if (declaredSizeBytes > storageProperties.maxFileSizeBytes()) {
            throw new BadRequestException("File exceeds the maximum allowed size of "
                    + storageProperties.maxFileSizeBytes() + " bytes");
        }
    }

    /**
     * Confirms the file's actual bytes match what its extension claims.
     * Tika only reads the leading header bytes off the stream to do this,
     * so it stays cheap regardless of file size and works the same whether
     * the bytes come from disk, memory or eventually an object store.
     */
    public void validateContent(InputStream content, String originalFilename) {
        String extension = Filenames.extensionWithoutDot(originalFilename);
        String detectedMimeType;
        try {
            detectedMimeType = tika.detect(content);
        } catch (IOException e) {
            throw new BadRequestException("Could not read the uploaded file to verify its contents");
        }

        if (!matchesExtension(extension, detectedMimeType)) {
            throw new BadRequestException("File content does not match its extension (." + extension
                    + " but detected " + detectedMimeType + ")");
        }
    }

    private boolean matchesExtension(String extension, String detectedMimeType) {
        return switch (extension) {
            case "pdf" -> "application/pdf".equals(detectedMimeType);
            case "docx" -> detectedMimeType.contains("wordprocessingml")
                    || "application/x-tika-ooxml".equals(detectedMimeType);
            case "txt" -> detectedMimeType.startsWith("text/");
            default -> false;
        };
    }
}
