package in.codefarm.cronpulse.tenant;

/**
 * Answers "which organisation is this request acting for?".
 * <p>
 * Controllers ask this interface instead of reading a header or session themselves, so the way
 * tenants are identified can change (default organisation now, login and API keys in Phase 4)
 * without touching any controller or service.
 */
public interface TenantResolver {

    long currentOrganizationId();
}
