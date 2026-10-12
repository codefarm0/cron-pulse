package in.codefarm.cronpulse.tenant;

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

/** A tenant. Every check and channel belongs to exactly one organisation. */
@Entity
@Table(name = "organizations")
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private UUID publicId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Plan plan;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Organization() {
        // for JPA
    }

    public Organization(String name, String slug, Plan plan, Instant now) {
        this.publicId = UUID.randomUUID();
        this.name = name;
        this.slug = slug;
        this.plan = plan;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public Plan getPlan() {
        return plan;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
