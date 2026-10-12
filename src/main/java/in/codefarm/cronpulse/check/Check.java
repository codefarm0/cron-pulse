package in.codefarm.cronpulse.check;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OptimisticLock;
import org.hibernate.type.SqlTypes;

/**
 * One monitored job.
 * <p>
 * The organisation is stored as a plain id rather than a {@code @ManyToOne}: every query filters on
 * it, and nothing needs to load the organisation itself. That keeps tenant scoping explicit and
 * avoids accidental lazy loading.
 * <p>
 * The {@code @Version} (exposed as the API's ETag) protects what a <em>user</em> edits. The ping
 * times are written by jobs, many times an hour, so they are excluded from it: otherwise every ping
 * would make the ETag a user just fetched stale, and their next edit would fail with 412.
 * {@code @DynamicUpdate} makes Hibernate write only the columns that changed, so a user's edit can't
 * overwrite a ping time with the old value it loaded, and a ping can't overwrite a user's edit.
 */
@Entity
@Table(name = "checks")
@DynamicUpdate
public class Check {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private UUID publicId;

    @Column(nullable = false, updatable = false)
    private Long organizationId;

    @Column(nullable = false)
    private UUID pingUuid;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String slug;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> tags;

    @Embedded
    private Schedule schedule;

    @Column(nullable = false)
    private int graceSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheckStatus status;

    /** When the last SUCCESS or FAIL ping arrived. A START ping doesn't count: the job hasn't finished. */
    @OptimisticLock(excluded = true)
    private Instant lastPingAt;

    @OptimisticLock(excluded = true)
    private Instant lastStartAt;

    private Instant nextExpectedAt;
    private Instant pausedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Check() {
        // for JPA
    }

    public Check(Long organizationId, String name, String slug, List<String> tags,
            Schedule schedule, Duration grace, Instant now) {
        this.publicId = UUID.randomUUID();
        this.pingUuid = UUID.randomUUID();
        this.organizationId = organizationId;
        this.status = CheckStatus.NEW;
        this.createdAt = now;
        update(name, slug, tags, schedule, grace, now);
    }

    /** Changes the user-editable fields. Status and ping times are not user-editable. */
    public void update(String name, String slug, List<String> tags, Schedule schedule,
            Duration grace, Instant now) {
        this.name = name;
        this.slug = slug;
        this.tags = List.copyOf(tags);
        this.schedule = schedule;
        this.graceSeconds = Math.toIntExact(grace.toSeconds());
        this.updatedAt = now;
    }

    // --- Only CheckStateMachine (same package) may call the methods below. ---

    void changeStatus(CheckStatus newStatus, Instant now) {
        this.status = newStatus;
        this.updatedAt = now;
    }

    void setPausedAt(Instant pausedAt) {
        this.pausedAt = pausedAt;
    }

    void setNextExpectedAt(Instant nextExpectedAt) {
        this.nextExpectedAt = nextExpectedAt;
    }

    void setLastPingAt(Instant lastPingAt) {
        this.lastPingAt = lastPingAt;
    }

    void setLastStartAt(Instant lastStartAt) {
        this.lastStartAt = lastStartAt;
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

    public UUID getPingUuid() {
        return pingUuid;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public List<String> getTags() {
        return tags;
    }

    public Schedule getSchedule() {
        return schedule;
    }

    public Duration getGrace() {
        return Duration.ofSeconds(graceSeconds);
    }

    public CheckStatus getStatus() {
        return status;
    }

    public Instant getLastPingAt() {
        return lastPingAt;
    }

    public Instant getLastStartAt() {
        return lastStartAt;
    }

    public Instant getNextExpectedAt() {
        return nextExpectedAt;
    }

    public Instant getPausedAt() {
        return pausedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
