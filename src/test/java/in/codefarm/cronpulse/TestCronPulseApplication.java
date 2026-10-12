package in.codefarm.cronpulse;

import org.springframework.boot.SpringApplication;

/** Runs the app locally against a Testcontainers Postgres instead of compose.yaml (handy for quick experiments). */
public class TestCronPulseApplication {

    public static void main(String[] args) {
        SpringApplication.from(CronPulseApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
