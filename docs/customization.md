# Customization

An Error Handling Service (EHS) instance can replace selected default behaviours with its own
implementations.

## Custom resending strategy

The `DefaultResendingStrategy` treats all failed messages alike (configurable delay, exponential back-off,
maximum retries — see [Configuration](configuration.md#resending)). If a system needs different resend
behaviour per message type or error, it can provide its own implementation of the `ResendingStrategy`
interface as a Spring bean. The implementation receives extensive information about the failed message and
returns the next resend time — or an empty result to escalate the error to a permanent error. The
`DefaultResendingStrategy` can serve as a template.

```java
@Component
@Primary
public class CustomResendingStrategy implements ResendingStrategy {

    @Override
    public Optional<ZonedDateTime> determineResend(int errorCountForEvent,
                                                   EventMetadata eventMetadata,
                                                   EventMetadata errorEventMetadata,
                                                   ErrorEventData errorEventData,
                                                   EventMessage message) {
        // ...
    }
}
```

The `@Primary` annotation makes Spring prefer the custom bean. For Spring to pick the class up, register it
in an auto-configuration: create the file
`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
containing the fully qualified class name:

```text
ch.admin.bit.jeap.yoursystem.error.CustomResendingStrategy
```

## Custom task factory

The definition of the manual tasks created in Agir is done by the `DefaultTaskFactory`
(see [Configuration](configuration.md#agir-task-management) for its properties). Alternatively, a custom
`TaskFactory` implementation can be provided as a Spring bean. Like the custom resending strategy, it must
be declared in an auto-configuration to be picked up.

## Custom Kafka back-off for EHS consumption retries

When the EHS itself hits a transient failure while consuming, it retries with a fixed back-off by default:

```java
@Bean(name = KafkaErrorHandlingConfiguration.BACKOFF_BEAN_NAME)
BackOff ehsKafkaErrorHandlingBackOff() {
    return new FixedBackOff(30000, UNLIMITED_ATTEMPTS);
}
```

An EHS instance can provide its own Spring Kafka `BackOff` bean under the bean name
`KafkaErrorHandlingConfiguration.BACKOFF_BEAN_NAME` to change this behaviour.

## Related

- [Configuration](configuration.md) — properties of the default implementations
- [Getting Started](getting-started.md) — setting up an instance repository
