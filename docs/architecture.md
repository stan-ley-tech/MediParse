# Architecture

## Overview

MediParse is a single Spring Boot service backed by three pieces of
infrastructure: PostgreSQL for system-of-record data, RabbitMQ for
asynchronous work, and OpenSearch for full-text search. There is no
microservice split — the "worker" that processes documents is the same
deployable artifact as the API, just consuming from a queue instead of
handling HTTP requests. For the scale this project targets, splitting them
into separate services would add operational overhead (two deployables, two
sets of health checks, coordinated releases) without a corresponding benefit;
the queue already gives the two halves of the workload their own scaling
knob if that ever changes.

```
                       ┌────────────────────┐
        HTTP           │                    │
   ───────────────────▶│   Spring Boot app  │
                        │                    │
                        │  ┌──────────────┐  │        ┌────────────┐
                        │  │  Controllers │  │        │ PostgreSQL │
                        │  └──────┬───────┘  │◀──────▶│ (system of │
                        │         │          │  JPA   │  record)   │
                        │  ┌──────▼───────┐  │        └────────────┘
                        │  │   Services   │  │
                        │  └──────┬───────┘  │
                        │         │          │        ┌────────────┐
                        │  ┌──────▼───────┐  │        │  RabbitMQ  │
                        │  │ Rabbit       │──┼───────▶│  (job      │
                        │  │ listener     │◀─┼────────│   queue)   │
                        │  └──────┬───────┘  │        └────────────┘
                        │         │          │
                        │  ┌──────▼───────┐  │        ┌────────────┐
                        │  │  Processing  │──┼───────▶│ OpenSearch │
                        │  │  pipeline    │  │        │ (search    │
                        │  └──────────────┘  │        │  index)    │
                        └────────────────────┘        └────────────┘
```

## Why these pieces

**PostgreSQL** holds everything that must be transactionally consistent:
users, patients, documents, processing job state, extracted entities, and
the audit log. It's the source of truth — if OpenSearch's index is ever
wiped or corrupted, it can be rebuilt from Postgres.

**RabbitMQ** decouples "a document was uploaded" from "a document was
processed." Uploads return as soon as the file is validated and stored;
processing happens on a background listener. This is what keeps a large PDF
from tying up an HTTP thread — see [processing-pipeline.md](processing-pipeline.md)
for how retries, idempotency, and concurrency are handled around that queue.

**OpenSearch** is a derived index, not a second source of truth. Every
document indexed there is rebuildable from its Postgres row plus its
extracted entities. It exists purely to make free-text queries like
"hemoglobin" or "amoxicillin" fast and relevance-ranked, which Postgres
isn't built for at this scale.

## Package layout

The code is organized by domain area rather than by technical layer
(`controller`/`service`/`repository` packages per feature would scatter a
single concept across the codebase):

| Package | Responsibility |
|---|---|
| `user` | Accounts and roles (the `UserDetails` implementation used by Spring Security) |
| `security` | JWT issuing/verification, the auth filter, and the security filter chain |
| `auth` | Registration, login, and admin-driven user provisioning |
| `patient` | Patient records that documents get associated with |
| `document` | Upload, storage, file validation, versioning, signed downloads, access control |
| `processing` | The RabbitMQ queue, the job-claiming logic, and the processing pipeline itself |
| `extraction` | Document classification and medical entity extraction/normalization |
| `search` | The OpenSearch client, indexer, and search API |
| `audit` | Audit log writes and the admin-facing query endpoints |
| `config` | Typed `@ConfigurationProperties` for storage, downloads, OpenSearch, and processing |
| `common` | Shared exception types and the global error handler |

## Data flow: upload to searchable

1. `POST /api/v1/documents` validates the file (extension, size, then actual
   content via Tika-based type sniffing), streams it to disk while hashing
   it, and writes a `documents` row with status `UPLOADED`.
2. A `processing_jobs` row is created in the same transaction, then a
   `JobMessage` is published to RabbitMQ *after* that transaction commits
   (see [processing-pipeline.md](processing-pipeline.md) for why the ordering
   matters).
3. `DocumentProcessingWorker` consumes the message, atomically claims the
   job, and runs the pipeline: extract text (Tika) → classify → extract
   entities → normalize → persist → index in OpenSearch → mark `COMPLETED`.
4. `GET /api/v1/search` queries OpenSearch directly; document metadata reads
   go through Postgres.

## Storage

Document bytes live on a mounted volume (`STORAGE_ROOT`, defaulting to
`./storage` locally and a named Docker volume in Compose), addressed by a
generated path, never by the original filename. `DocumentStorageService` is
a narrow interface for exactly this reason — swapping the filesystem
implementation for an S3-backed one later is a single new class, not a
rewrite of anything above it.

## What's deliberately not here

- **No microservice split.** One deployable, one queue, one database. See
  above.
- **No ML-based classification or NER.** Classification is keyword scoring
  and entity extraction is pattern-based (see
  [processing-pipeline.md](processing-pipeline.md)). Both are cheap,
  explainable, and easy to extend with more patterns; a trained model would
  need labeled data this project doesn't have.
- **No API gateway or service mesh.** There's one service; that
  infrastructure would have no job to do.
