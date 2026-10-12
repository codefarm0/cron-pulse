package in.codefarm.cronpulse.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadata for the generated OpenAPI document (served at /v3/api-docs, UI at /swagger-ui.html). */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    @Bean
    OpenAPI cronPulseOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CronPulse API")
                .version("v1")
                .description("""
                        Manage heartbeat checks for cron jobs and background workers.

                        Errors use RFC 9457 problem+json with a stable `code` property.
                        Create requests accept an `Idempotency-Key` header; updates accept `If-Match` with the ETag from a GET."""));
    }
}
