package com.mediparse.search;

import com.mediparse.config.OpenSearchProperties;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Full-text search over indexed documents with relevance ranking, plus the
 * structured filters (document type, patient, date range) laid over it as
 * bool filter clauses so they narrow the result set without affecting score.
 */
@Service
public class SearchService {

    private final OpenSearchClient client;
    private final OpenSearchProperties properties;

    public SearchService(OpenSearchClient client, OpenSearchProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public SearchResponse search(SearchQuery query) {
        try {
            var response = client.search(s -> s
                    .index(properties.documentsIndex())
                    .from(query.page() * query.size())
                    .size(query.size())
                    .query(buildQuery(query)), IndexedDocument.class);

            List<SearchHit> hits = response.hits().hits().stream()
                    .map(this::toSearchHit)
                    .toList();

            long total = response.hits().total() != null ? response.hits().total().value() : hits.size();
            return new SearchResponse(hits, total, query.page(), query.size());
        } catch (IOException e) {
            throw new UncheckedIOException("Search request failed", e);
        }
    }

    private SearchHit toSearchHit(Hit<IndexedDocument> hit) {
        double score = hit.score() != null ? hit.score() : 0.0;
        return SearchHit.from(hit.source(), score);
    }

    private Query buildQuery(SearchQuery query) {
        BoolQuery.Builder bool = new BoolQuery.Builder();

        if (query.text() != null && !query.text().isBlank()) {
            bool.must(m -> m.multiMatch(mm -> mm
                    .query(query.text())
                    .fields("content", "patientName^2", "originalFilename")));
        } else {
            bool.must(m -> m.matchAll(ma -> ma));
        }

        if (query.documentType() != null) {
            bool.filter(f -> f.term(t -> t.field("documentType").value(FieldValue.of(query.documentType().name()))));
        }

        if (query.patientId() != null) {
            bool.filter(f -> f.term(t -> t.field("patientId").value(FieldValue.of(query.patientId().toString()))));
        }

        if (query.fromDate() != null || query.toDate() != null) {
            bool.filter(f -> f.range(r -> {
                r.field("createdAt");
                if (query.fromDate() != null) {
                    r.gte(JsonData.of(query.fromDate().toString()));
                }
                if (query.toDate() != null) {
                    r.lte(JsonData.of(query.toDate().toString()));
                }
                return r;
            }));
        }

        return Query.of(q -> q.bool(bool.build()));
    }
}
