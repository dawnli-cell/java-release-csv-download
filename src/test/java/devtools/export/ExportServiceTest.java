package devtools.export;

import java.util.List;

public final class ExportServiceTest {
    public static void main(String[] args) {
        ReleaseSnapshot snapshot = new ReleaseSnapshot("lesson-7",
                List.of(new BuildEvent("b1", "failed", "quiz asset missing")),
                List.of(new ReleaseOperation("r1", "rollback-check", "ready")));
        String csv = ExportService.toCsv(snapshot);
        require(csv.startsWith("release_id,kind,id,status,diagnostic\n"));
        require(csv.contains("lesson-7,build,b1,failed,\"quiz asset missing\""));
        require(csv.contains("lesson-7,release,r1,ready,\"rollback-check\""));
        System.out.println("ExportServiceTest passed: failed build and release readiness are exported.");
    }

    private static void require(boolean condition) { if (!condition) throw new AssertionError("CSV decision mismatch"); }
}
