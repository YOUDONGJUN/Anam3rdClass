package com.anam3.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** AWS 문서의 서명된 주소 예제 (Authenticating Requests: Using Query Parameters) 와 똑같이 나와야 한다 */
class SigV4Test {

    @Test
    void AWS_문서_예제와_같은_서명() {
        SigV4 signer = new SigV4("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY", "us-east-1", "s3");

        String url = signer.presign("GET", "https", "examplebucket.s3.amazonaws.com", "/test.txt", Map.of(),
                Instant.parse("2013-05-24T00:00:00Z"), 86400);

        assertThat(url).isEqualTo("https://examplebucket.s3.amazonaws.com/test.txt"
                + "?X-Amz-Algorithm=AWS4-HMAC-SHA256"
                + "&X-Amz-Credential=AKIAIOSFODNN7EXAMPLE%2F20130524%2Fus-east-1%2Fs3%2Faws4_request"
                + "&X-Amz-Date=20130524T000000Z&X-Amz-Expires=86400&X-Amz-SignedHeaders=host"
                + "&X-Amz-Signature=aeeed9bbccd4d02ee5c0109b86d86835f995330da4c265957d157751f604d404");
    }

    @Test
    void 경로의_한글과_공백은_인코딩하고_슬래시는_둔다() {
        assertThat(SigV4.encode("/b/chat/1/a b.jpg", false)).isEqualTo("/b/chat/1/a%20b.jpg");
        assertThat(SigV4.encode("a/b", true)).isEqualTo("a%2Fb");
    }
}
