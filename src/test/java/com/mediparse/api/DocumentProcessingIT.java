package com.mediparse.api;

import com.mediparse.document.DocumentResponse;
import com.mediparse.document.DocumentStatus;
import com.mediparse.document.DocumentType;
import com.mediparse.patient.CreatePatientRequest;
import com.mediparse.patient.PatientResponse;
import com.mediparse.search.SearchResponse;
import com.mediparse.support.IntegrationTestSupport;
import com.mediparse.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Exercises the full pipeline end to end against real Postgres, RabbitMQ and
 * OpenSearch containers: upload over HTTP, asynchronous processing, and the
 * result showing up in search — not mocks standing in for any of it.
 */
class DocumentProcessingIT extends IntegrationTestSupport {

    @Test
    void uploadedLabReportIsClassifiedExtractedAndBecomesSearchable() {
        String token = loginAs(uniqueEmail("clinician"), Role.CLINICIAN);
        UUID patientId = createPatient(token, "John Kamau");

        DocumentResponse uploaded = upload(token, "lab-report-sample.pdf", patientId);
        assertThat(uploaded.status()).isIn(DocumentStatus.UPLOADED, DocumentStatus.QUEUED, DocumentStatus.PROCESSING);

        DocumentResponse completed = awaitStatus(token, uploaded.id(), DocumentStatus.COMPLETED);
        assertThat(completed.documentType()).isEqualTo(DocumentType.LAB_REPORT);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            SearchResponse results = search(token, "hemoglobin");
            assertThat(results.results()).anySatisfy(hit -> assertThat(hit.documentId()).isEqualTo(uploaded.id().toString()));
        });

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            SearchResponse results = search(token, "John Kamau");
            assertThat(results.results()).anySatisfy(hit -> assertThat(hit.documentId()).isEqualTo(uploaded.id().toString()));
        });
    }

    @Test
    void resubmittingTheSameFileReturnsTheOriginalDocument() {
        String token = loginAs(uniqueEmail("clinician"), Role.CLINICIAN);
        UUID patientId = createPatient(token, "Mary Njoroge");

        DocumentResponse first = upload(token, "prescription-sample.docx", patientId);
        DocumentResponse second = upload(token, "prescription-sample.docx", patientId);

        assertThat(second.id()).isEqualTo(first.id());
    }

    @Test
    void newVersionIncrementsVersionNumberAndSharesTheVersionGroup() {
        String token = loginAs(uniqueEmail("clinician"), Role.CLINICIAN);
        UUID patientId = createPatient(token, "Grace Achieng");

        DocumentResponse v1 = upload(token, "discharge-summary-sample.txt", patientId);
        awaitStatus(token, v1.id(), DocumentStatus.COMPLETED);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(fixture("referral-letter-sample.txt")));
        ResponseEntity<DocumentResponse> response = restTemplate.postForEntity(
                "/api/v1/documents/{id}/versions", new HttpEntity<>(body, multipartHeaders(token)),
                DocumentResponse.class, v1.id());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        DocumentResponse v2 = response.getBody();
        assertThat(v2.versionNumber()).isEqualTo(2);
        assertThat(v2.versionGroupId()).isEqualTo(v1.id());
        assertThat(v2.parentDocumentId()).isEqualTo(v1.id());
    }

    @Test
    void staffCannotViewAnotherStaffMembersDocument() {
        String uploaderToken = loginAs(uniqueEmail("staff-owner"), Role.STAFF);
        String otherToken = loginAs(uniqueEmail("staff-other"), Role.STAFF);

        DocumentResponse uploaded = upload(uploaderToken, "referral-letter-sample.txt", null);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/documents/{id}", HttpMethod.GET, new HttpEntity<>(authHeaders(otherToken)),
                String.class, uploaded.id());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void disguisedExecutableIsRejectedBeforeStorage() {
        String token = loginAs(uniqueEmail("clinician"), Role.CLINICIAN);

        HttpHeaders headers = multipartHeaders(token);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(fixture("disguised-executable.pdf")));

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/documents", new HttpEntity<>(body, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private DocumentResponse upload(String token, String fixtureName, UUID patientId) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(fixture(fixtureName)));
        if (patientId != null) {
            body.add("patientId", patientId.toString());
        }

        ResponseEntity<DocumentResponse> response = restTemplate.postForEntity(
                "/api/v1/documents", new HttpEntity<>(body, multipartHeaders(token)), DocumentResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private DocumentResponse awaitStatus(String token, UUID documentId, DocumentStatus expected) {
        AtomicReference<DocumentResponse> latest = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            DocumentResponse doc = restTemplate.exchange("/api/v1/documents/{id}", HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)), DocumentResponse.class, documentId).getBody();
            latest.set(doc);
            assertThat(doc.status()).isIn(expected, DocumentStatus.FAILED);
        });
        return latest.get();
    }

    private UUID createPatient(String token, String fullName) {
        var request = new CreatePatientRequest("MRN-" + UUID.randomUUID(), fullName, LocalDate.of(1990, 1, 1), "F");
        ResponseEntity<PatientResponse> response = restTemplate.postForEntity(
                "/api/v1/patients", new HttpEntity<>(request, authHeaders(token)), PatientResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().id();
    }

    private SearchResponse search(String token, String query) {
        ResponseEntity<SearchResponse> response = restTemplate.exchange(
                "/api/v1/search?q={q}", HttpMethod.GET, new HttpEntity<>(authHeaders(token)),
                SearchResponse.class, query);
        return response.getBody();
    }

    private String fixture(String name) {
        return "test-data/" + name;
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private HttpHeaders multipartHeaders(String token) {
        HttpHeaders headers = authHeaders(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return headers;
    }
}
