# MediParse

MediParse turns unstructured medical documents — lab reports, prescriptions,
discharge summaries, referral letters — into structured, searchable data. You
upload a PDF, DOCX, or TXT file; the service extracts its text, works out
what kind of document it is, pulls out the medical facts (patient, doctor,
diagnosis, medications, lab results with normalized status), and makes all
of it searchable within seconds.

A lab report like this:

```
Hemoglobin: 13.4 g/dL (Reference: 12-16)
```

becomes this, searchable by test name, value, or patient:

```json
{
  "entityType": "LAB_RESULT",
  "label": "Hemoglobin",
  "numericValue": 13.4,
  "unit": "g/dL",
  "referenceRange": "12-16",
  "status": "NORMAL"
}
```

## Stack

Java 21 · Spring Boot 3 · PostgreSQL · RabbitMQ · Apache Tika · OpenSearch · Docker

## Why it's built this way

Uploading a file and processing it are two different problems with two
different latency budgets, so they're split by a queue: an upload returns as
soon as the file is validated and stored, and a background worker does the
actual extraction, classification, and indexing. That split is also what
makes retries, concurrency, and idempotent re-processing tractable — see
[docs/processing-pipeline.md](docs/processing-pipeline.md) for the detail.
[docs/architecture.md](docs/architecture.md) covers the system as a whole,
and [docs/security.md](docs/security.md) covers auth, authorization, and how
uploads are validated.

## Features

- **Upload & processing** — streamed to disk (never buffered fully in
  memory), hashed for deduplication, validated by both extension and actual
  file content (a renamed `.exe` won't pass as a `.pdf`)
- **Text extraction** — Apache Tika, streaming, with a bounded character
  limit so a huge file degrades to a truncated extraction instead of an
  out-of-memory error
- **Classification** — keyword-scored into lab report / prescription /
  discharge summary / referral letter
- **Entity extraction & normalization** — patient, doctor, facility, date,
  diagnosis, medication + dosage, allergy, and lab results with a status
  derived from value vs. reference range when the source document doesn't
  state one
- **Search** — full-text, relevance-ranked, via OpenSearch, with filters for
  document type, patient, and date range
- **Versioning** — re-upload against an existing document to create a new
  version; the full history stays queryable
- **Auth & access control** — JWT authentication, role-based access
  (ADMIN / CLINICIAN / STAFF), and document-level authorization on top of
  that (a STAFF account only ever sees what it uploaded)
- **Signed download links** — short-lived, tamper-evident URLs instead of
  bearer tokens in query strings
- **Audit log** — every upload, view, download, search, deletion, and
  processing outcome, queryable by an administrator
- **Async processing** — RabbitMQ-backed, with atomic job claiming
  (so two workers or a redelivered message never double-process a document),
  in-process retry with backoff, and a dead-letter queue for whatever's left
  after retries are exhausted

## Getting started

Requires Docker and Docker Compose. Nothing else needs to be installed —
the application itself is built inside the Docker image.

```bash
docker compose up --build
```

This starts PostgreSQL, RabbitMQ, OpenSearch, and the application, in that
dependency order (Compose waits on each service's health check). The API is
then available at `http://localhost:8080`.

On first boot, no administrator account exists yet. Set these two
environment variables before starting the stack to have one created
automatically:

```bash
export ADMIN_BOOTSTRAP_EMAIL=admin@example.com
export ADMIN_BOOTSTRAP_PASSWORD='ChangeMe123!'
docker compose up --build
```

That only runs once — after an ADMIN exists, those variables are ignored on
subsequent restarts. From there, use that account to provision CLINICIAN and
STAFF accounts (see below), or anyone can self-register as STAFF.

### Try it

```bash
# Register (self-registration always creates a STAFF account)
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"nurse@example.com","password":"Password123!","fullName":"Jane Nurse"}'

# Log in
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"nurse@example.com","password":"Password123!"}' | jq -r .accessToken)

# Create a patient
PATIENT_ID=$(curl -s -X POST http://localhost:8080/api/v1/patients \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"mrn":"MRN-1001","fullName":"John Kamau","dateOfBirth":"1985-04-12","sex":"M"}' | jq -r .id)

# Upload a document
curl -s -X POST http://localhost:8080/api/v1/documents \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@test-data/lab-report-sample.pdf" \
  -F "patientId=$PATIENT_ID"

# A few seconds later, once processing completes:
curl -s "http://localhost:8080/api/v1/search?q=hemoglobin" -H "Authorization: Bearer $TOKEN"
```

## Configuration

Everything in [`application.yml`](src/main/resources/application.yml) is
overridable via environment variable — the defaults there are for local
development only. The ones worth knowing about:

| Variable | Purpose | Local default |
|---|---|---|
| `JWT_SECRET` | Signs access/refresh tokens | dev-only value |
| `DOWNLOAD_SIGNING_SECRET` | Signs download links | dev-only value |
| `MAX_UPLOAD_SIZE` / `MAX_UPLOAD_SIZE_BYTES` | Upload size limit | 50MB |
| `PROCESSING_MAX_ATTEMPTS` | In-process retry attempts before a job is marked FAILED | 3 |
| `ADMIN_BOOTSTRAP_EMAIL` / `ADMIN_BOOTSTRAP_PASSWORD` | First ADMIN account, created once | unset |

None of the default secrets are safe outside local development — see
[docs/security.md](docs/security.md).

## Running the tests

```bash
mvn test          # unit tests — fast, no external services required
mvn verify         # unit + integration tests — spins up Postgres, RabbitMQ
                    # and OpenSearch via Testcontainers, so Docker must be running
```

Integration tests (`*IT.java`) exercise the real stack end to end: upload
over HTTP, wait for asynchronous processing to complete, confirm the result
is both correctly classified and searchable. [`test-data/`](test-data) holds
the sample documents they use, including deliberately malformed and
disguised files (an executable renamed to `.pdf`, a truncated PDF, an empty
file) to exercise the validation and failure paths.

## Project layout

```
mediparse/
├── src/main/java/com/mediparse/
│   ├── user/         accounts and roles
│   ├── security/      JWT issuing/verification, auth filter
│   ├── auth/          registration, login, admin user provisioning
│   ├── patient/        patient records
│   ├── document/      upload, storage, validation, versioning, signed downloads
│   ├── processing/    the RabbitMQ queue and the processing pipeline (the "workers")
│   ├── extraction/     classification and medical entity extraction
│   ├── search/          OpenSearch indexing and the search API
│   ├── audit/           audit log writes and queries
│   ├── config/           typed configuration properties
│   └── common/           shared exception types and error handling
├── src/main/resources/db/migration/   Flyway migrations
├── src/test/java/                       unit tests (*Test.java) and integration tests (*IT.java)
├── test-data/                            sample and malformed documents used by the tests
├── docs/                                  architecture, pipeline, and security write-ups
├── Dockerfile
├── docker-compose.yml
└── pom.xml
```

Flyway migrations live under Maven's standard resource path rather than a
top-level `migrations/` directory, and tests live under `src/test/java`
rather than a top-level `tests/` directory — both are where the Java/Maven
ecosystem expects them, which matters more for a Java project than matching
a language-agnostic layout.

## License

[MIT](LICENSE)
