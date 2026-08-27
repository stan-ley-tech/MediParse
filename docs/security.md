# Security

## Authentication

Stateless JWT (HS256). `POST /api/v1/auth/login` returns an access token
(`mediparse.jwt.access-token-ttl-minutes`, default 30) and a longer-lived
refresh token; every other endpoint (other than the two exceptions below)
requires `Authorization: Bearer <token>`. `JwtAuthenticationFilter` loads the
user by the token's subject on every request rather than trusting claims
blindly, so a disabled or deleted account stops working immediately instead
of waiting for the token to expire.

Two endpoint families are intentionally outside the authenticated zone:
- `/api/v1/auth/**` — you can't authenticate to get a token if the
  token-issuing endpoint itself requires one.
- `/api/v1/downloads/**` — governed by a signed URL instead (see below),
  not a bearer token, so it works from contexts that can't hold one, like
  a link opened in a PDF viewer.

## Password storage

BCrypt via Spring Security's `BCryptPasswordEncoder`. Nothing about a
password is ever logged or included in an audit record — only the fact that
a login happened.

## Authorization

Two layers, deliberately kept separate:

**Role-based (RBAC).** Three roles: `ADMIN`, `CLINICIAN`, `STAFF`.
Self-registration (`POST /api/v1/auth/register`) always creates a `STAFF`
account — there is no way for a new signup to grant itself `ADMIN` or
`CLINICIAN`. Elevated accounts are created deliberately by an existing
administrator through `POST /api/v1/admin/users`
(`@PreAuthorize("hasRole('ADMIN')")`). The very first administrator is
created by `AdminBootstrapRunner` on startup, from `ADMIN_BOOTSTRAP_EMAIL`
/ `ADMIN_BOOTSTRAP_PASSWORD`, and only when no `ADMIN` exists yet — after
that first run it's a permanent no-op, so those variables don't need to
stay set (and shouldn't).

**Document-level.** `DocumentAccessService` sits below the role check:
`ADMIN` and `CLINICIAN` can view any document, but `STAFF` can only view and
download documents they personally uploaded. Deletion is stricter still —
`ADMIN` or the original uploader, nobody else. This is checked in the
service layer against the actual `documents` row (uploader id), not
inferred from the request, so it can't be bypassed by role alone.

## Signed download URLs

`POST /api/v1/documents/{id}/download-url` (authenticated, subject to the
document-level check above) returns a URL good for
`mediparse.download.url-ttl-seconds` (default 5 minutes):

```
/api/v1/downloads/{id}?expires={epochSeconds}&signature={hex}
```

The signature is HMAC-SHA256 over `documentId:expiresAt`, keyed by
`mediparse.download.signing-secret`, and compared with
`MessageDigest.isEqual` (constant-time) rather than `String.equals` to avoid
a timing side-channel. The actual `GET /api/v1/downloads/{id}` endpoint
verifies the signature and expiry itself — it isn't behind Spring Security
at all, which is what lets the link work without an `Authorization` header.
Anyone who obtains a valid link can use it until it expires; the access
control decision happens once, at link-issuance time.

## File-type validation

Two checks, at two different points, because they catch different things:

1. **Metadata check** (`FileValidationService.validateMetadata`), before
   anything touches disk: extension against an allowlist
   (`mediparse.storage.allowed-extensions` — pdf, docx, txt) and size
   against `mediparse.storage.max-file-size-bytes`.
2. **Content check** (`FileValidationService.validateContent`), against the
   file's actual bytes via Tika's magic-byte detector: an upload named
   `report.pdf` whose content is actually a Windows executable (or plain
   text, or anything else) is rejected even though its extension passed
   step 1. This is what stops a disguised file from being accepted just
   because someone renamed it.

`test-data/disguised-executable.pdf` and `test-data/malformed-wrong-content.pdf`
in the test dataset exist specifically to exercise this path.

## Secure storage

Uploaded files are stored under a generated path
(`yyyy/MM/<random-uuid>.<ext>`), never under the original filename — so an
uploaded `../../etc/passwd`-style filename never becomes a path, and two
users uploading files with the same name never collide.
`FileSystemDocumentStorageService` also re-validates that every resolved
path stays inside the configured storage root before touching the
filesystem, as a second line of defense against path traversal even though
the generated paths can't produce one on their own.

## Audit logging

`AuditLogService` writes one row per significant action — upload, view,
download, search, delete, plus the pipeline's own `PROCESSING_COMPLETED`
and `PROCESSING_FAILED` events — with the actor (nullable, for
system-initiated events like a queue-driven download), the resource, the
client IP, and a timestamp. Every write commits in its own transaction
(`REQUIRES_NEW`), specifically so that a failed operation still leaves an
audit trail instead of having its own record rolled back along with it —
the `PROCESSING_FAILED` entry for a document that just failed extraction is
the case this matters most for. `GET /api/v1/audit-logs` is `ADMIN`-only.

## Transport and secrets

OpenSearch runs with its security plugin enabled in Docker Compose
(`OPENSEARCH_INITIAL_ADMIN_PASSWORD`), reached over HTTPS. The bundled
certificate is self-signed, so the application's OpenSearch client is
configured to trust any certificate — acceptable for a local/dev stack, and
called out explicitly in `OpenSearchClientConfig` so it isn't mistaken for
an oversight. A real deployment should point that client at a proper CA
chain instead.

None of the default secrets in `application.yml` or `docker-compose.yml`
(`JWT_SECRET`, `DOWNLOAD_SIGNING_SECRET`, database and RabbitMQ credentials,
the OpenSearch admin password) are safe to run in anything other than local
development — they exist so the stack boots without extra setup, and every
one of them is overridable via environment variable for that reason.
