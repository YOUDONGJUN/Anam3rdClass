package com.anam3.storage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Cloudflare R2 (S3 호환). 버킷은 비공개 — 사진은 서명된 주소로만 오간다 */
public class R2PhotoStorage implements PhotoStorage {

    private static final Logger log = LoggerFactory.getLogger(R2PhotoStorage.class);
    /** 올리는 주소는 15분 */
    private static final long UPLOAD_SECONDS = 15 * 60;
    /** 보는 주소: 6시간마다 새로 만들고 12시간 동안 쓸 수 있다 (그 사이 같은 주소 → 브라우저 캐시) */
    static final Duration VIEW_WINDOW = Duration.ofHours(6);
    private static final long VIEW_SECONDS = 12 * 60 * 60;

    private final SigV4 signer;
    private final String host;
    private final String bucket;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public R2PhotoStorage(StorageProperties.R2 r2, Clock clock) {
        this.signer = new SigV4(r2.accessKeyId().strip(), r2.secretAccessKey().strip(), "auto", "s3");
        this.host = r2.accountId().strip() + ".r2.cloudflarestorage.com";
        this.bucket = r2.bucket().strip();
        this.clock = clock;
    }

    @Override
    public String uploadUrl(String key, String contentType) {
        return sign("PUT", key, Map.of("content-type", contentType), clock.instant(), UPLOAD_SECONDS);
    }

    @Override
    public String viewUrl(String key) {
        long window = VIEW_WINDOW.toSeconds();
        Instant start = Instant.ofEpochSecond(clock.instant().getEpochSecond() / window * window);
        return sign("GET", key, Map.of(), start, VIEW_SECONDS);
    }

    @Override
    public Optional<Long> size(String key) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(sign("HEAD", key, Map.of(), clock.instant(), 60)))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(10))
                .build();
        try {
            HttpResponse<Void> res = http.send(request, HttpResponse.BodyHandlers.discarding());
            if (res.statusCode() == 404) {
                return Optional.empty();
            }
            if (res.statusCode() / 100 != 2) {
                throw new IllegalStateException("R2 HEAD " + res.statusCode());
            }
            return res.headers().firstValueAsLong("content-length").stream().boxed().findFirst();
        } catch (IOException e) {
            throw new IllegalStateException("R2 에 사진이 올라왔는지 확인하지 못했어요", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void delete(String key) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(sign("DELETE", key, Map.of(), clock.instant(), 60)))
                .DELETE()
                .timeout(Duration.ofSeconds(10))
                .build();
        try {
            HttpResponse<Void> res = http.send(request, HttpResponse.BodyHandlers.discarding());
            if (res.statusCode() / 100 != 2 && res.statusCode() != 404) {
                log.warn("R2 사진을 지우지 못했습니다 ({}): {}", res.statusCode(), key);
            }
        } catch (IOException e) {
            log.warn("R2 사진을 지우지 못했습니다: {}", key, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String sign(String method, String key, Map<String, String> headers, Instant at, long seconds) {
        return signer.presign(method, "https", host, "/" + bucket + "/" + key, headers, at, seconds);
    }
}
