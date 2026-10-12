package in.codefarm.cronpulse.check;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every lookup a user can trigger is scoped by organisation. Finding a check by public id alone
 * would let one tenant read another tenant's check just by guessing (or leaking) its id.
 */
public interface CheckRepository extends JpaRepository<Check, Long> {

    Optional<Check> findByOrganizationIdAndPublicId(Long organizationId, UUID publicId);

    boolean existsByOrganizationIdAndSlug(Long organizationId, String slug);

    /** Used by the ping endpoint (Phase 2): the ping UUID is the only credential a ping carries. */
    Optional<Check> findByPingUuid(UUID pingUuid);
}
