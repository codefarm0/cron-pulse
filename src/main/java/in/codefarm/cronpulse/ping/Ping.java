package in.codefarm.cronpulse.ping;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.net.InetAddress;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One request received on a check's ping URL. Rows are immutable once written. */
@Entity
@Table(name = "pings")
public class Ping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long checkId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private PingType type;

    @Column(updatable = false)
    private Integer exitCode;

    @Column(nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(updatable = false)
    private Long durationMs;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(updatable = false, columnDefinition = "inet")
    private InetAddress sourceIp;

    @Column(updatable = false)
    private String userAgent;

    @Column(nullable = false, updatable = false)
    private String method;

    @Column(updatable = false)
    private String body;

    @Column(nullable = false, updatable = false)
    private boolean bodyTruncated;

    protected Ping() {
        // for JPA
    }

    public Ping(Long checkId, PingType type, Integer exitCode, Instant receivedAt, Long durationMs,
            InetAddress sourceIp, String userAgent, String method, String body, boolean bodyTruncated) {
        this.checkId = checkId;
        this.type = type;
        this.exitCode = exitCode;
        this.receivedAt = receivedAt;
        this.durationMs = durationMs;
        this.sourceIp = sourceIp;
        this.userAgent = userAgent;
        this.method = method;
        this.body = body;
        this.bodyTruncated = bodyTruncated;
    }

    public Long getId() {
        return id;
    }

    public Long getCheckId() {
        return checkId;
    }

    public PingType getType() {
        return type;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public InetAddress getSourceIp() {
        return sourceIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getMethod() {
        return method;
    }

    public String getBody() {
        return body;
    }

    public boolean isBodyTruncated() {
        return bodyTruncated;
    }
}
