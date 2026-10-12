package in.codefarm.cronpulse.api;

import static org.assertj.core.api.Assertions.assertThat;

import in.codefarm.cronpulse.TestcontainersConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keeps docs/api/openapi.json in sync with the code. If the API changes, this test fails until the
 * spec is regenerated with {@code ./gradlew test -Popenapi.update=true}, which makes every API
 * change show up in code review as a diff of the contract.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiSpecTest {

    static final Path SPEC = Path.of("docs", "api", "openapi.json");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JsonMapper json;

    @Test
    void committedSpecMatchesTheCode() throws Exception {
        var result = mvc.get().uri("/v3/api-docs").exchange();
        assertThat(result).hasStatusOk();

        var pretty = json.rebuild().enable(SerializationFeature.INDENT_OUTPUT).build()
                .writeValueAsString(json.readTree(result.getResponse().getContentAsString())) + "\n";

        if (Boolean.getBoolean("openapi.update") || !Files.exists(SPEC)) {
            Files.writeString(SPEC, pretty);
        }
        assertThat(Files.readString(SPEC))
                .as("docs/api/openapi.json is out of date; run ./gradlew test -Popenapi.update=true")
                .isEqualTo(pretty);
    }
}
