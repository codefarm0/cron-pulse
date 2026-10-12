package in.codefarm.cronpulse.check;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every lookup a user can trigger is scoped by organisation. Finding a check by public id alone
 * would let one tenant read another tenant's check just by guessing (or leaking) its id.
 */
public interface CheckRepository extends JpaRepository<Check, Long> {

    Optional<Check> findByOrganizationIdAndPublicId(Long organizationId, UUID publicId);

    boolean existsByOrganizationIdAndSlug(Long organizationId, String slug);

    /** Used by the ping endpoint (Phase 2): the ping UUID is the only credential a ping carries. */
    Optional<Check> findByPingUuid(UUID pingUuid);

    /**
     * Lists checks with optional filters. A null parameter means "no filter".
     * <p>
     * Native SQL on purpose. In HQL, null parameters were bound with types Postgres can't compare
     * (bytea), and Hibernate rewrote {@code array_position} into an expression that is never null,
     * which silently turned the tag filter off. Plain SQL with explicit casts says exactly what runs.
     * Ordering is fixed here, so callers pass an unsorted {@link Pageable}.
     *
     * @param search a LIKE pattern fragment, already escaped by the caller
     */
    @Query(nativeQuery = true, value = """
            SELECT * FROM checks c
            WHERE c.organization_id = :organizationId
              AND (CAST(:status AS text) IS NULL OR c.status = CAST(:status AS text))
              AND (CAST(:tag AS text) IS NULL OR CAST(:tag AS text) = ANY (c.tags))
              AND (CAST(:search AS text) IS NULL OR c.name ILIKE '%' || CAST(:search AS text) || '%')
            ORDER BY c.name, c.id
            """, countQuery = """
            SELECT count(*) FROM checks c
            WHERE c.organization_id = :organizationId
              AND (CAST(:status AS text) IS NULL OR c.status = CAST(:status AS text))
              AND (CAST(:tag AS text) IS NULL OR CAST(:tag AS text) = ANY (c.tags))
              AND (CAST(:search AS text) IS NULL OR c.name ILIKE '%' || CAST(:search AS text) || '%')
            """)
    Page<Check> search(@Param("organizationId") long organizationId, @Param("status") String status,
            @Param("tag") String tag, @Param("search") String search, Pageable pageable);

    @Query("""
            select new in.codefarm.cronpulse.check.CheckRepository$StatusCount(c.status, count(c))
            from Check c
            where c.organizationId = :organizationId
            group by c.status
            """)
    List<StatusCount> countByStatus(@Param("organizationId") long organizationId);

    record StatusCount(CheckStatus status, long total) {
    }
}
