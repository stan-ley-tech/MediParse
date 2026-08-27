# Processing Pipeline

## Stages

```
Document uploaded
       │  FileValidationService: extension allowlist, size limit,
       │  then Tika content sniffing once bytes are readable
       ▼
File validated & stored
       │  FileSystemDocumentStorageService streams the upload to disk
       │  while computing its SHA-256 hash in the same pass
       ▼
Job queued (RabbitMQ, published after the DB transaction commits)
       ▼
DocumentProcessingWorker claims the job
       │  TextExtractionService (Tika, streamed)
       ▼
Text extracted
       │  DocumentClassifier (keyword scoring)
       ▼
Document classified
       │  MedicalEntityExtractor (pattern-based)
       ▼
Entities extracted
       │  EntityNormalizer (derives lab result status from value + range)
       ▼
Normalized & persisted (Postgres)
       │  DocumentIndexer
       ▼
Indexed (OpenSearch) → searchable
```

Each arrow is a real module boundary — `TextExtractionService`,
`DocumentClassifier`, `MedicalEntityExtractor`, and `EntityNormalizer` don't
know about each other; `DocumentProcessingPipeline` is the only thing that
calls all four in sequence.

## Classification

`DocumentClassifier` scores extracted text against a fixed set of phrases
per document type (`"reference range"` and `"specimen"` lean lab report,
`"discharge diagnosis"` leans discharge summary, etc.) and returns whichever
type scored highest. It's a few dozen lines, fully explainable, and cheap to
extend by adding phrases as real documents surface edge cases — a
reasonable starting point before reaching for a trained classifier, which
would need labeled training data this project doesn't have.

## Entity extraction

The extractor is line-oriented and pattern-based rather than a general NLP
model, because the documents this system targets already follow
conventions: `Label: value` for demographics (`Patient:`, `Doctor:`,
`Facility:`, `Date:`, `Diagnosis:`, `Allergy:`), a test-result convention
for labs (`Hemoglobin: 13.4 g/dL (Reference: 12-16) NORMAL`), and a
`Name dosage` convention for medications (`Amoxicillin 500mg`). Three
regular expressions cover all three, checked in order of specificity per
line so a lab-result line is never mis-parsed as a generic labeled field.

This is a real limitation, not a hidden one: a document that doesn't follow
these conventions will extract fewer (or no) entities, and it's why the test
dataset's sample documents were written in these formats — the same way a
real deployment would need to calibrate the extractor against whatever
templates its actual source systems produce.

## Normalization

The one rule worth automating: when a lab result states a value and a
reference range but not a status, `EntityNormalizer` derives `NORMAL`,
`HIGH`, or `LOW` by comparing the parsed value against the range's bounds.
An explicitly stated status is left untouched. Everything else extracted is
already in a directly usable shape, so there's nothing else to normalize.

## Asynchronous processing

Uploads never run this pipeline inline. `POST /api/v1/documents` does
exactly three things before returning: validate, store, and enqueue — the
extraction/classification/indexing work happens on a RabbitMQ consumer.

**Transactional publish.** The `processing_jobs` row is written in the same
database transaction as the `documents` row, but the RabbitMQ message isn't
published until that transaction actually commits (`ProcessingJobPublisher`
uses a `@TransactionalEventListener(phase = AFTER_COMMIT)` for this). Without
that split, a worker could receive the message and query for a document that
— from a separate transaction's point of view — doesn't exist yet.

**Idempotency and duplicate submissions.** Two layers:
- At upload time, the file's SHA-256 hash is checked against
  `(uploaded_by, file_hash)`. Re-submitting the same bytes returns the
  existing document instead of creating a duplicate and a duplicate job.
- At processing time, `processing_jobs` has a partial unique index that
  allows only one `PENDING`/`IN_PROGRESS` job per document, and
  `ProcessingJobRepository.claim()` is an atomic
  `UPDATE ... WHERE status = 'PENDING'` compare-and-swap. A message
  redelivered after a crash (RabbitMQ's normal at-least-once behavior) finds
  the job already claimed and is a no-op.

**Concurrency.** Several listener threads
(`mediparse.rabbitmq.listener.simple.concurrency`) consume the same queue.
The claim step above is what makes that safe — two threads racing to handle
the same job will only ever have one of them win the `UPDATE`.

**Retries and failure.** `DocumentProcessingService.process()` retries
transient failures in-process with exponential backoff
(`mediparse.processing.max-attempts`, default 3). If every attempt fails,
the document and job are marked `FAILED` with the error message recorded,
and the exception is allowed to propagate out of the RabbitMQ listener. The
queue is configured with a dead-letter exchange
(`default-requeue-rejected: false`), so a permanently failed message lands
on `mediparse.document-processing.dlq` instead of being silently dropped or
retried forever — it stays visible for an operator without blocking the
documents behind it in the main queue.

Retry and transaction management deliberately live on different beans
(`DocumentProcessingService` vs. `DocumentProcessingPipeline`). Spring's
`@Retryable` and `@Transactional` are both proxy-based; stacking them on one
method only works if the proxy ordering is right, and a same-class call
between them would silently skip whichever proxy the call bypasses. Two
beans sidesteps the whole problem.

**Timeouts.** There's no explicit processing timeout — RabbitMQ's manual
acknowledgment means a message stays unacked (and therefore invisible to
other consumers) only until the consumer's connection drops, at which point
it's redelivered. Combined with the idempotent claim step, a worker that
hangs or is killed mid-processing doesn't lose the job or double-process it.

## Streaming and memory

Nothing in the upload or processing path reads a whole file into a `byte[]`:

- Upload: `MultipartFile.getInputStream()` is copied straight to disk
  through a `DigestInputStream`, computing the SHA-256 hash in the same pass
  as the write.
- Extraction: `TextExtractionService` hands the file's `InputStream`
  directly to Tika's `AutoDetectParser`, which parses incrementally. The
  only thing held in memory afterward is the extracted text, and even that
  is capped by `mediparse.processing.text-extraction-char-limit` — past the
  limit, extraction stops and the document is processed with the truncated
  text rather than failing outright or risking unbounded memory growth on a
  huge file.
- Content-type sniffing (`FileValidationService`): Tika's detector only
  reads the file's leading bytes to identify it, regardless of file size.
