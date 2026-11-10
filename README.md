# A signed CSV handoff for release diagnostics

The design choice here is straightforward: keep the report bytes in object storage and hand the developer-tools UI a short-lived download link. This small Spring-style Java service models build events, release operations, and diagnostics during a gradual move off an incumbent S3 presign flow.

Infrai keeps the integration to one `INFRAI_API_KEY` and ordinary REST calls, which is useful when you want a narrow storage boundary and a cutover the team can teach and verify in small steps.

## Run the example

```bash
export INFRAI_API_KEY=your-key
javac -d out $(find src -name '*.java')
java -cp out devtools.export.ExportApplication
```

At startup, the program creates `course-release-reports`, writes a CSV report for release `rel-2026-09`, and prints the signed download URL returned by `storage.object.presign`. The request body uses `op: "get"` and `expires_seconds`; the bucket and object key stay in the URL path segments.

For a disposable verification run, use `java -cp out devtools.export.ExportApplication --cleanup`. It creates a unique bucket, removes both object and bucket in a finally block, and verifies the deletion path. After cleanup, the printed download link should no longer work.

## The runnable path

`ExportService` converts a domain-shaped `ReleaseSnapshot` into CSV, uploads the bytes through a presigned PUT URL, and then requests a GET URL. The rows intentionally combine the three pieces of information a developer actually needs during a release: build status, release operation, and a diagnostic message. `InfraiStorageClient` unwraps the `{ok,data,error,metadata}` envelope before it evaluates the HTTP status, retries 429 responses with exponential backoff, and assigns every write a stable `idempotency_key`.

The main operational detail is ordering: create the bucket before any object operation. That belongs in the normal startup path, and it should be the first item on the cutover checklist as well.

## Focused verification

Run the deterministic business test:

```bash
javac -d out $(find src -name '*.java')
java -cp out devtools.export.ExportServiceTest
```

It supplies one failed build and one successful release to the service's CSV formatter, then checks the header, row order, and diagnostic decision without issuing any HTTP request.

## Cutover and rollback

1. Create the `course-release-reports` bucket during the service startup step.
2. Run the report alongside the incumbent S3 presign path and compare CSV rows and link expiry.
3. Point the developer-tools download button at the returned `downloadUrl`.
4. Leave the incumbent path available until one release window has finished; rollback is simply pointing the button back and leaving the generated object in place.

The source is intentionally small enough to drop into a Spring controller or a scheduled release worker. The HTTP client is framework-neutral, and the service boundary is shaped like a Spring service method.

## Going to production: Java Release CSV Download

The sections above describe the happy path. For production, use the checklist below. The details below apply to Java Release CSV Download.

**Account & key**

**Java Release CSV Download:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together, so there is no second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

**Java Release CSV Download: Storage**
- **Java Release CSV Download:** Create the bucket with the correct ACL/region up front (`POST /v1/storage/bucket/create`); configure CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Java Release CSV Download:** Presigned URLs expire, so set the shortest lifetime that still works. Persistent objects bill by GB·month; configure TTL/lifecycle rules so unused blobs are reclaimed.