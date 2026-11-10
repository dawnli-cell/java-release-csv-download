package devtools.export;

import java.util.List;

public final class ExportApplication {
    public static void main(String[] args) throws Exception {
        ReleaseSnapshot snapshot = new ReleaseSnapshot("rel-2026-09",
                List.of(new BuildEvent("build-41", "failed", "course quiz asset missing")),
                List.of(new ReleaseOperation("deploy-41", "cutover", "ready")));
        boolean cleanup = List.of(args).contains("--cleanup");
        String bucket = cleanup ? "export-check-" + java.util.UUID.randomUUID().toString().substring(0, 12) : "course-release-reports";
        InfraiStorageClient storage = new InfraiStorageClient();
        Exception failure = null;
        System.out.println("bucket=" + bucket);
        try {
            ExportResult result = new ExportService(storage, bucket).export(snapshot);
            System.out.println(result.csv());
            System.out.println("downloadUrl=" + result.downloadUrl());
        } catch (Exception e) {
            failure = e;
            throw e;
        } finally {
            if (cleanup) {
                try {
                    storage.cleanup(bucket, "releases/" + snapshot.releaseId() + ".csv");
                    System.out.println("Cleanup verified: " + bucket);
                } catch (Exception e) {
                    if (failure != null) failure.addSuppressed(e);
                    else throw e;
                }
            }
        }
    }
}
