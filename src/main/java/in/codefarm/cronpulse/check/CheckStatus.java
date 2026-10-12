package in.codefarm.cronpulse.check;

/** The lifecycle of a check. See the state diagram in CLAUDE.md §4.4. */
public enum CheckStatus {
    /** Created, never pinged. Never alerts. */
    NEW,
    /** Last ping arrived on time and was successful. */
    UP,
    /** Past the expected time but still inside the grace period. No alert yet. */
    LATE,
    /** Past the grace period, or a fail ping arrived. Alerted. */
    DOWN,
    /** Monitoring switched off by the user. */
    PAUSED
}
