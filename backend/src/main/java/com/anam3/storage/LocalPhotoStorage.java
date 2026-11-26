package com.anam3.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.Optional;

/**
 * 폴더에 두는 사진 저장소 (내 PC·테스트). R2 처럼 서명된 주소로 올리고 본다 — /api/files/{key} (LocalFileController).
 */
public class LocalPhotoStorage implements PhotoStorage {

    private static final long UPLOAD_SECONDS = 15 * 60;
    private static final long VIEW_WINDOW_SECONDS = R2PhotoStorage.VIEW_WINDOW.toSeconds();

    private final Path root;
    private final byte[] secret;
    private final Clock clock;

    public LocalPhotoStorage(Path root, String secret, Clock clock) {
        this.root = root.toAbsolutePath().normalize();
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    @Override
    public String uploadUrl(String key, String contentType) {
        long exp = clock.instant().getEpochSecond() + UPLOAD_SECONDS;
        return "/api/files/" + key + "?m=PUT&exp=" + exp + "&ct=" + SigV4.encode(contentType, true)
                + "&sig=" + sign("PUT", key, exp, contentType);
    }

    @Override
    public String viewUrl(String key) {
        long now = clock.instant().getEpochSecond();
        long exp = now / VIEW_WINDOW_SECONDS * VIEW_WINDOW_SECONDS + 2 * VIEW_WINDOW_SECONDS;
        return "/api/files/" + key + "?m=GET&exp=" + exp + "&sig=" + sign("GET", key, exp, "");
    }

    @Override
    public Optional<Long> size(String key) {
        Path file = file(key);
        try {
            return Files.isRegularFile(file) ? Optional.of(Files.size(file)) : Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(file(key));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 서명이 맞고 기한 안인지 */
    boolean verify(String method, String key, long exp, String contentType, String sig) {
        if (sig == null || clock.instant().getEpochSecond() > exp) {
            return false;
        }
        byte[] expected = sign(method, key, exp, contentType == null ? "" : contentType)
                .getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, sig.getBytes(StandardCharsets.UTF_8));
    }

    void write(String key, InputStream in, long maxBytes) throws IOException {
        Path file = file(key);
        Files.createDirectories(file.getParent());
        byte[] data = in.readNBytes((int) maxBytes + 1);
        if (data.length > maxBytes) {
            throw new IllegalArgumentException("too large");
        }
        Files.write(file, data);
    }

    Optional<Path> read(String key) {
        Path file = file(key);
        return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
    }

    private Path file(String key) {
        if (!PhotoKeys.isValid(key)) {
            throw new IllegalArgumentException("bad key");
        }
        Path file = root.resolve(key).normalize();
        if (!file.startsWith(root)) {
            throw new IllegalArgumentException("bad key");
        }
        return file;
    }

    private String sign(String method, String key, long exp, String contentType) {
        byte[] mac = SigV4.hmac(secret, method + "\n" + key + "\n" + exp + "\n" + contentType);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac);
    }
}
