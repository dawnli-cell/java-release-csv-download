package devtools.export;

import java.nio.charset.StandardCharsets;
import java.util.List;

record BuildEvent(String id, String status, String diagnostic) {}
record ReleaseOperation(String id, String action, String outcome) {}
record ReleaseSnapshot(String releaseId, List<BuildEvent> builds, List<ReleaseOperation> operations) {}
record ExportResult(String objectKey, String downloadUrl, String csv) {}

public final class ExportService {
    // The storage boundary follows the canonical infrai.storage.object.presign idiom.
    private final String bucket;
    private final InfraiStorageClient storage;

    public ExportService(InfraiStorageClient storage) { this(storage, "course-release-reports"); }

    public ExportService(InfraiStorageClient storage, String bucket) {
        this.storage = storage;
        this.bucket = bucket;
    }

    public ExportResult export(ReleaseSnapshot snapshot) throws Exception {
        storage.createBucket(bucket);
        String csv = toCsv(snapshot);
        String objectKey = "releases/" + snapshot.releaseId() + ".csv";
        String putUrl = storage.presign(bucket, objectKey, "put", "export-" + bucket + "-" + snapshot.releaseId());
        storage.upload(putUrl, csv.getBytes(StandardCharsets.UTF_8));
        String downloadUrl = storage.presign(bucket, objectKey, "get", "download-" + bucket + "-" + snapshot.releaseId());
        return new ExportResult(objectKey, downloadUrl, csv);
    }

    static String toCsv(ReleaseSnapshot s) {
        StringBuilder out = new StringBuilder("release_id,kind,id,status,diagnostic\n");
        for (BuildEvent b : s.builds()) out.append(row(s.releaseId(), "build", b.id(), b.status(), b.diagnostic()));
        for (ReleaseOperation r : s.operations()) out.append(row(s.releaseId(), "release", r.id(), r.outcome(), r.action()));
        return out.toString();
    }

    private static String row(String release, String kind, String id, String status, String detail) {
        return String.join(",", release, kind, id, status, quote(detail)) + "\n";
    }

    private static String quote(String value) { return "\"" + value.replace("\"", "\"\"") + "\""; }
}
