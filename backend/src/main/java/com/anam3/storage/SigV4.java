package com.anam3.storage;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * S3 호환 저장소(Cloudflare R2)의 서명된 주소(presigned URL)를 만든다 — AWS Signature Version 4, 쿼리 방식.
 * SDK 를 넣으면 서버 메모리(512MB)를 많이 먹어서 서명만 직접 한다.
 */
public final class SigV4 {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final String accessKeyId;
    private final String secretAccessKey;
    private final String region;
    private final String service;

    public SigV4(String accessKeyId, String secretAccessKey, String region, String service) {
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;
        this.region = region;
        this.service = service;
    }

    /**
     * @param path         맨 앞 / 부터 (예: /bucket/chat/1/a.jpg). 조각마다 인코딩한다
     * @param extraHeaders 서명에 넣을 머리글 (예: content-type). 요청할 때 같은 값을 보내야 한다
     */
    public String presign(String method, String scheme, String host, String path, Map<String, String> extraHeaders,
            Instant at, long expiresSeconds) {
        String stamp = STAMP.format(at);
        String day = DAY.format(at);
        String scope = day + "/" + region + "/" + service + "/aws4_request";

        TreeMap<String, String> headers = new TreeMap<>();
        headers.put("host", host);
        extraHeaders.forEach((k, v) -> headers.put(k.toLowerCase(), v.strip()));
        String signedHeaders = String.join(";", headers.keySet());

        TreeMap<String, String> query = new TreeMap<>();
        query.put("X-Amz-Algorithm", "AWS4-HMAC-SHA256");
        query.put("X-Amz-Credential", accessKeyId + "/" + scope);
        query.put("X-Amz-Date", stamp);
        query.put("X-Amz-Expires", Long.toString(expiresSeconds));
        query.put("X-Amz-SignedHeaders", signedHeaders);
        String canonicalQuery = query.entrySet().stream()
                .map(e -> encode(e.getKey(), true) + "=" + encode(e.getValue(), true))
                .collect(Collectors.joining("&"));

        String canonicalPath = encode(path, false);
        String canonicalHeaders = headers.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue() + "\n")
                .collect(Collectors.joining());
        String canonicalRequest = method + "\n" + canonicalPath + "\n" + canonicalQuery + "\n" + canonicalHeaders
                + "\n" + signedHeaders + "\nUNSIGNED-PAYLOAD";

        String stringToSign = "AWS4-HMAC-SHA256\n" + stamp + "\n" + scope + "\n" + sha256Hex(canonicalRequest);
        byte[] key = hmac(("AWS4" + secretAccessKey).getBytes(StandardCharsets.UTF_8), day);
        key = hmac(key, region);
        key = hmac(key, service);
        key = hmac(key, "aws4_request");
        String signature = HexFormat.of().formatHex(hmac(key, stringToSign));

        return scheme + "://" + host + canonicalPath + "?" + canonicalQuery + "&X-Amz-Signature=" + signature;
    }

    /** RFC 3986. 경로(path)는 / 를 그대로 둔다 */
    static String encode(String value, boolean encodeSlash) {
        StringBuilder out = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xff);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~' || (c == '/' && !encodeSlash)) {
                out.append(c);
            } else {
                out.append('%').append(String.format("%02X", b & 0xff));
            }
        }
        return out.toString();
    }

    static String sha256Hex(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
