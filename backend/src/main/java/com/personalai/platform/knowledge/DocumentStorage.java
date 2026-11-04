package com.personalai.platform.knowledge;

import java.io.IOException;
import java.io.InputStream;

/**
 * Provider-independent byte-storage port, with a private local adapter and no upload HTTP endpoint.
 * Adapters must generate opaque keys, enforce upload limits and inspect bytes independently
 * of the client-supplied file name and media type. Callers close supplied and returned streams.
 * Storage keys must never be accepted as arbitrary local paths.
 */
public interface DocumentStorage {
    String store(DocumentUpload metadata, InputStream content) throws IOException;

    InputStream open(String storageKey) throws IOException;

    void delete(String storageKey) throws IOException;
}
