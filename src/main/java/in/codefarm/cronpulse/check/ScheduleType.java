package in.codefarm.cronpulse.check;

public enum ScheduleType {
    /** Every N seconds, e.g. every hour. */
    SIMPLE,
    /** A cron expression evaluated in the check's time zone. */
    CRON
}
