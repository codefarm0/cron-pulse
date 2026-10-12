package in.codefarm.cronpulse.check;

import java.util.UUID;

/**
 * Published when a check is deleted, so caches keyed by its ping UUID can drop it.
 * <p>
 * An event keeps the dependency pointing one way: the ping feature knows about checks, but the
 * check feature doesn't need to know which caches exist.
 */
public record CheckDeletedEvent(UUID pingUuid) {
}
