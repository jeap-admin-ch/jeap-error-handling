package ch.admin.bit.jeap.errorhandling;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@Slf4j
@EnableAsync
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args).getEnvironment();
    }
}
