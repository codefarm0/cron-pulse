package in.codefarm.cronpulse.ping;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PingBodyTest {

    @Test
    void emptyBodyIsStoredAsNull() throws IOException {
        assertThat(PingBody.read(stream(new byte[0]))).isEqualTo(PingBody.EMPTY);
    }

    @Test
    void bodyWithinTheLimitIsKeptWhole() throws IOException {
        var body = PingBody.read(stream("backup done: 42 tables".getBytes(UTF_8)));

        assertThat(body.text()).isEqualTo("backup done: 42 tables");
        assertThat(body.truncated()).isFalse();
    }

    @Test
    void bodyOfExactlyTheLimitIsNotTruncated() throws IOException {
        var body = PingBody.read(stream("x".repeat(PingBody.MAX_BYTES).getBytes(UTF_8)));

        assertThat(body.text()).hasSize(PingBody.MAX_BYTES);
        assertThat(body.truncated()).isFalse();
    }

    @Test
    void oversizedBodyIsCutAtTheLimitAndTheRestIsNeverRead() throws IOException {
        var input = new CountingStream("x".repeat(1024 * 1024).getBytes(UTF_8));

        var body = PingBody.read(input);

        assertThat(body.truncated()).isTrue();
        assertThat(body.text().getBytes(UTF_8)).hasSize(PingBody.MAX_BYTES);
        assertThat(input.bytesRead).isEqualTo(PingBody.MAX_BYTES + 1);
    }

    @Test
    void neverCutsAMultiByteCharacterInHalf() throws IOException {
        // "é" is 2 bytes in UTF-8; 9 bytes of "é" can't end on a character boundary at 9.
        var body = PingBody.read(stream("éééééééééé".getBytes(UTF_8)), 9);

        assertThat(body.text()).isEqualTo("éééé");
        assertThat(body.truncated()).isTrue();
    }

    @Test
    void invalidUtf8NeverGrowsPastTheLimit() throws IOException {
        // Each invalid byte decodes to U+FFFD, which is 3 bytes when stored.
        var bytes = new byte[12];
        Arrays.fill(bytes, (byte) 0xFF);

        var body = PingBody.read(stream(bytes), 12);

        assertThat(body.text().getBytes(UTF_8).length).isLessThanOrEqualTo(12);
        assertThat(body.truncated()).isTrue();
    }

    @Test
    void stripsNulCharactersPostgresCannotStore() throws IOException {
        var body = PingBody.read(stream("a\u0000b".getBytes(UTF_8)));

        assertThat(body.text()).isEqualTo("ab");
    }

    private static InputStream stream(byte[] bytes) {
        return new ByteArrayInputStream(bytes);
    }

    private static final class CountingStream extends ByteArrayInputStream {

        int bytesRead;

        CountingStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public synchronized int read(byte[] b, int off, int len) {
            int n = super.read(b, off, len);
            bytesRead += Math.max(n, 0);
            return n;
        }
    }
}
