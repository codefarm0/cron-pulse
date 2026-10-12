package in.codefarm.cronpulse.check;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One transition in a check's history: the status timeline and uptime are built from these rows. */
@Entity
@Table(name = "status_changes")
public class StatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long checkId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private CheckStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private CheckStatus toStatus;

    @Column(nullable = false, updatable = false)
    private String reason;

    @Column(nullable = false, updatable = false)
    private Instant changedAt;

    protected StatusChange() {
        // for JPA
    }

    public StatusChange(Long checkId, CheckStatus fromStatus, CheckStatus toStatus, String reason, Instant changedAt) {
        this.checkId = checkId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.changedAt = changedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getCheckId() {
        return checkId;
    }

    public CheckStatus getFromStatus() {
        return fromStatus;
    }

    public CheckStatus getToStatus() {
        return toStatus;
    }

    public String getReason() {
        return reason;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
