package in.codefarm.cronpulse.ping;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * Where a ping came from, as stored on the ping row.
 * <p>
 * The source IP is the client's address as Tomcat reports it. Behind a load balancer that is the
 * load balancer, so {@code server.forward-headers-strategy: native} (application.yml) lets Tomcat
 * replace it with the {@code X-Forwarded-For} value, but only when the request came from a trusted
 * internal proxy. Reading {@code X-Forwarded-For} ourselves would let any client claim any IP.
 */
public record PingSource(InetAddress ip, String userAgent, String method) {

    /** Matches the {@code length(user_agent) <= 512} constraint on the pings table. */
    static final int MAX_USER_AGENT_LENGTH = 512;

    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");

    public static PingSource of(String remoteAddress, String userAgent, String method) {
        return new PingSource(parseIp(remoteAddress), truncate(userAgent), method);
    }

    /**
     * {@link InetAddress#getByName} does a DNS lookup when given a host name, which would put a
     * network call on the hot path. Only pass it IP literals, which it parses without any lookup.
     */
    private static InetAddress parseIp(String address) {
        if (address == null || !(IPV4.matcher(address).matches() || address.contains(":"))) {
            return null;
        }
        try {
            return InetAddress.getByName(address);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private static String truncate(String userAgent) {
        if (userAgent == null || userAgent.length() <= MAX_USER_AGENT_LENGTH) {
            return userAgent;
        }
        return userAgent.substring(0, MAX_USER_AGENT_LENGTH);
    }
}
