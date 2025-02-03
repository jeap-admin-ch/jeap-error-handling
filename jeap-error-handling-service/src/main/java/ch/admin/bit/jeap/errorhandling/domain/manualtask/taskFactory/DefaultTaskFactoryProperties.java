package ch.admin.bit.jeap.errorhandling.domain.manualtask.taskFactory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration properties for {@link DefaultTaskFactory}
 */
@Configuration
@ConfigurationProperties(prefix = DefaultTaskFactoryProperties.PREFIX)
@Data
@Validated
public class DefaultTaskFactoryProperties {
    public static final String PREFIX = "jeap.errorhandling.task-management.default-factory";

    /**
     * The base URL of the error service for links back here
     */
    private String errorServiceBaseUrl;

    /**
     * The system for which to generate the tasks
     */
    private String system;

    /**
     * The priority of the generated tasks
     */
    private String priority = "HIGH";

    /**
     * The duration you have time to solve the tasks
     */
    private Duration timeToHandle = Duration.of(1, ChronoUnit.DAYS);

    /**
     * Task reference name
     * for details.
     */
    private String taskReferenceName = "Error Service";

    /**
     * Task domain
     * for details.
     */
    private String domain = "error-handling";

    /**
     * Task display configuration for details.
     */
    @NotEmpty
    private Map<String, TaskDisplayProperties> display = defaultDisplay();

    public void validate() {
        Objects.requireNonNull(system, PREFIX + ".system is not configured");
        Objects.requireNonNull(errorServiceBaseUrl, PREFIX + ".errorServiceBaseUrl is not configured");
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static class TaskDisplayProperties {
        @NotBlank
        private String displayName;

        @NotBlank
        private String displayDomain;

        @NotBlank
        private String title;

        @NotBlank
        private String description;
    }

    private static Map<String, TaskDisplayProperties> defaultDisplay() {
        return Map.of(
                "DE", TaskDisplayProperties.builder()
                        .title("Fehlgeschlagene Nachrichten-Verarbeitung")
                        .description("Ein technischer Fehler ist aufgrund einer unverarbeitbaren Nachricht aufgetreten.")
                        .displayName("Nachrichten-Verarbeitungsfehler")
                        .displayDomain("Error Handling")
                        .build(),
                "FR", TaskDisplayProperties.builder()
                        .title("Erreur du traitement des messages")
                        .description("Une erreur technique s'est produite en raison d'un message qui ne peut être traité.")
                        .displayName("Erreur de traitement des messages")
                        .displayDomain("Error Handling")
                        .build(),
                "IT", TaskDisplayProperties.builder()
                        .title("Errore durante l'elaborazione di un messaggio")
                        .description("Si è prodotto un errore tecnico a causa di un messaggio non processabile.")
                        .displayName("Errore durante l'elaborazione di un messaggio")
                        .displayDomain("Error Handling")
                        .build(),
                "EN", TaskDisplayProperties.builder()
                        .title("Message processing failure")
                        .description("A technical error has occurred due to an unprocessable message.")
                        .displayName("Message processing error")
                        .displayDomain("Error Handling")
                        .build());
    }
}
