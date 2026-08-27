package com.mediparse.search;

import com.mediparse.audit.AuditAction;
import com.mediparse.audit.AuditLogService;
import com.mediparse.document.DocumentType;
import com.mediparse.security.CurrentUserService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
public class SearchController {

    private final SearchService searchService;
    private final AuditLogService auditLogService;
    private final CurrentUserService currentUserService;

    public SearchController(SearchService searchService, AuditLogService auditLogService,
                             CurrentUserService currentUserService) {
        this.searchService = searchService;
        this.auditLogService = auditLogService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/api/v1/search")
    public SearchResponse search(@RequestParam(required = false) String q,
                                  @RequestParam(required = false) DocumentType documentType,
                                  @RequestParam(required = false) UUID patientId,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        SearchResponse response = searchService.search(
                new SearchQuery(q, documentType, patientId, from, to, page, Math.min(size, 100)));

        auditLogService.record(currentUserService.require().getId(), AuditAction.SEARCH, "search", null, q);
        return response;
    }
}
