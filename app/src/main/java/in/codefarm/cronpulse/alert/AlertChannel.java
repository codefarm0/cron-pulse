package in.codefarm.cronpulse.alert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A destination for alerts: an email address, a Slack incoming webhook, or a generic webhook. */
@Entity
@Table(name = "alert_channels")
public class AlertChannel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private UUID publicId;

    @Column(nullable = false, updatable = false)
    private Long organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChannelType type;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String target;

    @Column(nullable = false)
    private boolean verified;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected AlertChannel() {
        // for JPA
    }

    public AlertChannel(Long organizationId, ChannelType type, String name, String target, Instant now) {
        this.publicId = UUID.randomUUID();
        this.organizationId = organizationId;
        this.type = type;
        this.name = name;
        this.target = target;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public ChannelType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getTarget() {
        return target;
    }

    public boolean isVerified() {
        return verified;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
