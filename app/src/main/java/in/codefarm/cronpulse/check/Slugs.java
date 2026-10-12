package in.codefarm.cronpulse.check;

import java.text.Normalizer;
import java.util.Locale;

final class Slugs {

    private Slugs() {
    }

    /** "Nightly DB Backup!" becomes "nightly-db-backup". */
    static String fromName(String name) {
        var ascii = Normalizer.normalize(name, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        var slug = ascii.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
        if (slug.length() > 60) {
            slug = slug.substring(0, 60).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? "check" : slug;
    }
}
