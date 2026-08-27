package com.mediparse.document;

import java.io.IOException;
import java.io.InputStream;

/**
 * Abstraction over where document bytes physically live. The default
 * implementation writes to the local filesystem (or a mounted volume in
 * Docker); swapping in an object-store-backed implementation later only
 * means implementing this interface, nothing above it changes.
 */
public interface DocumentStorageService {

    StoredFile store(InputStream content, String suggestedFilename) throws IOException;

    InputStream load(String relativePath) throws IOException;

    void delete(String relativePath) throws IOException;
}
