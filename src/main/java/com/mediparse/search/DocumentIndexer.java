package com.mediparse.search;

import com.mediparse.config.OpenSearchProperties;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Owns the OpenSearch index lifecycle: creates it with an explicit mapping
 * on startup if it doesn't exist yet, and indexes/removes individual
 * documents as the processing pipeline completes or a document is deleted.
 */
@Component
@Order(0)
public class DocumentIndexer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DocumentIndexer.class);

    private final OpenSearchClient client;
    private final OpenSearchProperties properties;

    public DocumentIndexer(OpenSearchClient client, OpenSearchProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        ensureIndexExists();
    }

    private void ensureIndexExists() throws IOException {
        boolean exists = client.indices().exists(e -> e.index(properties.documentsIndex())).value();
        if (exists) {
            return;
        }

        client.indices().create(c -> c
                .index(properties.documentsIndex())
                .mappings(m -> m
                        .properties("documentId", p -> p.keyword(k -> k))
                        .properties("patientId", p -> p.keyword(k -> k))
                        .properties("patientName", p -> p.text(t -> t))
                        .properties("documentType", p -> p.keyword(k -> k))
                        .properties("status", p -> p.keyword(k -> k))
                        .properties("originalFilename", p -> p.text(t -> t))
                        .properties("content", p -> p.text(t -> t))
                        .properties("createdAt", p -> p.date(d -> d))
                )
        );
        log.info("Created OpenSearch index '{}'", properties.documentsIndex());
    }

    public void index(IndexedDocument document) throws IOException {
        client.index(i -> i
                .index(properties.documentsIndex())
                .id(document.getDocumentId())
                .document(document));
    }

    public void delete(UUID documentId) {
        try {
            client.delete(d -> d.index(properties.documentsIndex()).id(documentId.toString()));
        } catch (Exception e) {
            log.warn("Failed to remove document {} from the search index", documentId, e);
        }
    }
}
