package com.mediparse.search;

import java.util.List;

public record SearchResponse(List<SearchHit> results, long totalHits, int page, int size) {
}
