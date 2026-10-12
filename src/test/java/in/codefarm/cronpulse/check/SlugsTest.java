package in.codefarm.cronpulse.check;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SlugsTest {

    @ParameterizedTest
    @CsvSource({
            "'Nightly DB Backup!', nightly-db-backup",
            "'  spaces   everywhere ', spaces-everywhere",
            "'Café Résumé sync', cafe-resume-sync",
            "'!!!', check",
    })
    void derivesAUrlFriendlySlugFromTheName(String name, String expected) {
        assertThat(Slugs.fromName(name)).isEqualTo(expected);
    }
}
