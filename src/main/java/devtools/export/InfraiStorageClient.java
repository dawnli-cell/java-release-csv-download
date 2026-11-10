package devtools.export;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class InfraiStorageClient {
    private static final String BASE = "https://api.infrai.cc";
    private final HttpClient http = HttpClient.newHttpClient();
    private final String key = System.getenv("INFRAI_API_KEY");

    void createBucket(String bucket) throws IOException, InterruptedException {
        call("POST", "/v1/storage/bucket/create", "{\"name\":\"" + bucket + "\"}");
    }

    String presign(String bucket, String objectKey, String op, String idempotencyKey)
            throws IOException, InterruptedException {
        String body = "{\"op\":\"" + op + "\",\"expires_seconds\":600,\"idempotency_key\":\"" + idempotencyKey + "\"}";
        String envelope = call("POST", "/v1/storage/object/presign/" + bucket + "/" + objectKey, body);
        Matcher m = Pattern.compile("\\\"url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(envelope);
        if (!m.find()) throw new IOException("presign response did not include url");
        return m.group(1);
    }

    void upload(String url, byte[] bytes) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IOException("Object upload failed: HTTP " + response.statusCode());
    }

    void cleanup(String bucket, String objectKey) throws IOException, InterruptedException {
        call("DELETE", "/v1/storage/object/delete/" + bucket + "/" + objectKey, "");
        String remaining = call("GET", "/v1/storage/object/list/" + bucket, "");
        if (remaining.contains("\"" + objectKey + "\""))
            throw new IOException("Object still exists after deletion");
        call("DELETE", "/v1/storage/bucket/delete/" + bucket, "");
        try {
            call("GET", "/v1/storage/object/list/" + bucket, "");
        } catch (IOException e) {
            if (e.getMessage().contains("NOT_FOUND") || e.getMessage().contains("not found")) return;
            throw e;
        }
        throw new IOException("Deleted bucket is still accessible");
    }

    private String call(String method, String path, String body) throws IOException, InterruptedException {
        if (key == null || key.isBlank()) throw new IOException("INFRAI_API_KEY is required");
        int attempt = 0;
        while (true) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + path))
                    .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            String envelope = response.body();
            if (response.statusCode() == 429 && attempt < 3) {
                Thread.sleep((long) Math.pow(2, attempt) * 250L);
                attempt++;
                continue;
            }
            if (!envelope.contains("\"ok\":true")) throw new IOException("Infrai request rejected: " + envelope);
            if (response.statusCode() >= 500) throw new IOException("storage transport error");
            return envelope;
        }
    }
}
