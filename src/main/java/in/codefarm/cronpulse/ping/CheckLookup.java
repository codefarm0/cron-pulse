package in.codefarm.cronpulse.ping;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import in.codefarm.cronpulse.check.CheckDeletedEvent;
import in.codefarm.cronpulse.check.CheckRepository;
import in.codefarm.cronpulse.config.CronPulseProperties;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Finds the check a ping UUID belongs to, with an in-memory cache in front of the database.
 * <p>
 * Every ping starts with this lookup, and a job pings the same URL over and over, so a cache saves
 * one query per ping and lets the rate limiter reject floods without touching the database at all.
 * <p>
 * Only the check's id is cached, never its state, so a cached entry can't go stale in a way that
 * matters: the ping is still applied to a freshly loaded, locked row. The one stale case is a deleted
 * check; this instance evicts it at once, and other instances find out when the locked load comes
 * back empty (or the entry expires).
 * <p>
 * Unknown UUIDs are not cached. A flood of random UUIDs is better stopped per client IP (Phase 5)
 * than by filling the cache with entries that will never be used again.
 */
@Component
class CheckLookup {

    private final CheckRepository checks;
    private final Cache<UUID, Long> checkIds;

    CheckLookup(CheckRepository checks, CronPulseProperties properties) {
        this.checks = checks;
        this.checkIds = Caffeine.newBuilder()
                .maximumSize(properties.ping().lookupCacheSize())
                .expireAfterWrite(properties.ping().lookupCacheTtl())
                .build();
    }

    Optional<Long> checkIdFor(UUID pingUuid) {
        // Not cache.get(key, loader): that runs the loader while holding a lock inside the cache,
        // and a database call is too slow to run under it. Two threads may both load the same id
        // on a miss, which is harmless.
        var cached = checkIds.getIfPresent(pingUuid);
        if (cached != null) {
            return Optional.of(cached);
        }
        var found = checks.findIdByPingUuid(pingUuid);
        found.ifPresent(id -> checkIds.put(pingUuid, id));
        return found;
    }

    void evict(UUID pingUuid) {
        checkIds.invalidate(pingUuid);
    }

    /** Runs after the delete commits; evicting earlier would let a concurrent ping re-cache the id. */
    @TransactionalEventListener(fallbackExecution = true)
    void onCheckDeleted(CheckDeletedEvent event) {
        evict(event.pingUuid());
    }
}
