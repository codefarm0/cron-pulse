package in.codefarm.cronpulse.ping;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import org.junit.jupiter.api.Test;

class PingSourceTest {

    @Test
    void parsesIpv4AndIpv6Literals() throws Exception {
        assertThat(PingSource.of("203.0.113.7", null, "GET").ip()).isEqualTo(InetAddress.getByName("203.0.113.7"));
        assertThat(PingSource.of("2001:db8::1", null, "GET").ip()).isEqualTo(InetAddress.getByName("2001:db8::1"));
    }

    @Test
    void neverResolvesAHostName() {
        // getByName("example.com") would do a DNS lookup on the hot path.
        assertThat(PingSource.of("example.com", null, "GET").ip()).isNull();
        assertThat(PingSource.of(null, null, "GET").ip()).isNull();
    }

    @Test
    void truncatesLongUserAgents() {
        var source = PingSource.of("127.0.0.1", "a".repeat(2000), "GET");

        assertThat(source.userAgent()).hasSize(PingSource.MAX_USER_AGENT_LENGTH);
    }
}
