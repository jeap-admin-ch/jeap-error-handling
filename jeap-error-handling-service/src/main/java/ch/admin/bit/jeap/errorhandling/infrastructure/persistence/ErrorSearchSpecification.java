package ch.admin.bit.jeap.errorhandling.infrastructure.persistence;

import ch.admin.bit.jeap.errorhandling.web.api.ErrorSearchCriteria;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.constraints.NotNull;
import lombok.experimental.UtilityClass;
import org.springframework.data.jpa.domain.Specification;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@UtilityClass
public class ErrorSearchSpecification {

    public static Specification<Error> fromCriteria(ErrorSearchCriteria criteria) {
        return (root, query, criteriaBuilder) -> {
            if (criteria == null) {
                return null;
            }
            List<Predicate> specs = new ArrayList<>();
            criteria.getFrom().ifPresent(from -> specs.add(ErrorSearchSpecification.withFrom(from).toPredicate(root, query, criteriaBuilder)));
            criteria.getTo().ifPresent(to -> specs.add(ErrorSearchSpecification.withTo(to).toPredicate(root, query, criteriaBuilder)));
            criteria.getEventName().ifPresent(eventName -> specs.add(ErrorSearchSpecification.withEventName(eventName).toPredicate(root, query, criteriaBuilder)));
            criteria.getTraceId().ifPresent(traceId -> specs.add(ErrorSearchSpecification.withTraceId(traceId).toPredicate(root, query, criteriaBuilder)));
            criteria.getEventId().ifPresent(eventId -> specs.add(ErrorSearchSpecification.withEventId(eventId).toPredicate(root, query, criteriaBuilder)));
            criteria.getServiceName().ifPresent(serviceName -> specs.add(ErrorSearchSpecification.withServiceName(serviceName).toPredicate(root, query, criteriaBuilder)));
            criteria.getStates().ifPresent(states -> specs.add(ErrorSearchSpecification.withState(states).toPredicate(root, query, criteriaBuilder)));
            criteria.getErrorCode().ifPresent(errorCode -> specs.add(ErrorSearchSpecification.withErrorCode(errorCode).toPredicate(root, query, criteriaBuilder)));
            criteria.getStacktrace().ifPresent(stackTrace -> specs.add(ErrorSearchSpecification.withStacktrace(stackTrace).toPredicate(root, query, criteriaBuilder)));
            criteria.getClosingReason().ifPresent(closingReason -> specs.add(ErrorSearchSpecification.withClosingReason(closingReason).toPredicate(root, query, criteriaBuilder)));
            criteria.getTicketNumber().ifPresent(ticketNumber -> specs.add(ErrorSearchSpecification.withTicketNumber(ticketNumber).toPredicate(root, query, criteriaBuilder)));
            return criteriaBuilder.and(specs.toArray(new Predicate[0]));
        };
    }

    private Specification<Error> withFrom(@NotNull ZonedDateTime from) {
        return (errorRoot, q, builder) -> builder.greaterThanOrEqualTo(errorRoot.get("created"), from);
    }

    private Specification<Error> withTo(@NotNull ZonedDateTime to) {
        return (errorRoot, q, builder) -> builder.lessThanOrEqualTo(errorRoot.get("created"), to);
    }

    private Specification<Error> withEventName(@NotNull String eventName) {
        return (errorRoot, q, builder) -> builder.equal(errorRoot.get("causingEvent").get("metadata").get("type").get("name"), eventName);
    }

    private Specification<Error> withTraceId(@NotNull String traceId) {
        return (errorRoot, q, builder) -> builder.equal(errorRoot.get("originalTraceContext").get("traceIdString"), traceId);
    }

    private Specification<Error> withEventId(@NotNull String eventId) {
        return (errorRoot, q, builder) -> builder.equal(errorRoot.get("causingEvent").get("metadata").get("id"), eventId);
    }

    private Specification<Error> withServiceName(@NotNull String serviceName) {
        return (errorRoot, q, builder) -> builder.equal(errorRoot.get("errorEventMetadata").get("publisher").get("service"), serviceName);
    }

    private Specification<Error> withState(@NotNull List<Error.ErrorState> states) {
        return (errorRoot, q, builder) -> errorRoot.get("state").in(states);
    }

    private Specification<Error> withErrorCode(@NotNull String errorCode) {
        return (errorRoot, q, builder) -> builder.equal(errorRoot.get("errorEventData").get("code"), errorCode);
    }

    private Specification<Error> withStacktrace(@NotNull Pattern stacktrace) {
        return (errorRoot, q, builder) -> {
            Expression<String> stackTrace = errorRoot.get("errorEventData").get("stackTrace");
            return builder.isTrue(
                    builder.function(
                            "textregexeq",
                            Boolean.class,
                            stackTrace,
                            builder.literal(stacktrace.pattern())
                    )
            );
        };
    }

    private Specification<Error> withClosingReason(@NotNull String reason) {
        return (errorRoot, q, builder) -> builder.like(errorRoot.get("closingReason"), "%" + reason + "%");
    }

    private Specification<Error> withTicketNumber(@NotNull String ticketNumber) {
        return (errorRoot, q, builder) -> {
            Join<Error, ErrorGroup> errorGroupJoin = errorRoot.join("errorGroup", JoinType.INNER);
            return builder.equal(errorGroupJoin.get("ticketNumber"), ticketNumber);
        };
    }
}
