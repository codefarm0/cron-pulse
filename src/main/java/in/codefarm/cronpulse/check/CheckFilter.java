package in.codefarm.cronpulse.check;

/** Optional filters for listing checks; null means "don't filter on this". */
public record CheckFilter(CheckStatus status, String tag, String search) {

    public static CheckFilter none() {
        return new CheckFilter(null, null, null);
    }
}
