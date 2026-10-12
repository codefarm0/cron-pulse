package in.codefarm.cronpulse.check;

import in.codefarm.cronpulse.ping.PingType;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * The only place a check's status changes. Every transition is recorded as a {@link StatusChange}.
 * <p>
 * Keeping transitions in one class means the rules (which moves are allowed, what gets recorded,
 * later which alerts are queued) can be read and tested in one place. The status mutators on
 * {@link Check} are package-private, so code outside this package can't bypass it.
 * <p>
 * Phase 1 handles pause and resume. Step 2.1 records ping times; step 2.3 adds the ping and
 * detector transitions.
 */
@Component
public class CheckStateMachine {

    private final StatusChangeRepository statusChanges;
    private final Clock clock;

    CheckStateMachine(StatusChangeRepository statusChanges, Clock clock) {
        this.statusChanges = statusChanges;
        this.clock = clock;
    }

    /** Stops monitoring. Pausing an already paused check changes nothing. */
    public void pause(Check check, String reason) {
        if (check.getStatus() == CheckStatus.PAUSED) {
            return;
        }
        var now = clock.instant();
        check.setPausedAt(now);
        // A paused check expects nothing, so the detector must not pick it up.
        check.setNextExpectedAt(null);
        transition(check, CheckStatus.PAUSED, reason);
    }

    /**
     * Restarts monitoring. The check goes to NEW, not UP: we don't know when the next ping will
     * come, and marking it UP with an old expected time would make the detector flag it at once.
     * The next ping moves it to UP (Phase 2).
     */
    public void resume(Check check, String reason) {
        if (check.getStatus() != CheckStatus.PAUSED) {
            return;
        }
        check.setPausedAt(null);
        transition(check, CheckStatus.NEW, reason);
    }

    /**
     * Applies a received ping to its check. The caller must hold the check's row lock
     * ({@link CheckRepository#findByIdForUpdate}) so concurrent pings are applied one at a time.
     * <p>
     * For now this only records when the ping arrived; step 2.3 adds the status transitions.
     *
     * @param receivedAt when the ping arrived; the same instant is stored on the ping row
     */
    public void applyPing(Check check, PingType type, Instant receivedAt) {
        switch (type) {
            case START -> check.setLastStartAt(receivedAt);
            case SUCCESS, FAIL -> check.setLastPingAt(receivedAt);
        }
    }

    private void transition(Check check, CheckStatus to, String reason) {
        var now = clock.instant();
        var from = check.getStatus();
        check.changeStatus(to, now);
        statusChanges.save(new StatusChange(check.getId(), from, to, reason, now));
    }
}
