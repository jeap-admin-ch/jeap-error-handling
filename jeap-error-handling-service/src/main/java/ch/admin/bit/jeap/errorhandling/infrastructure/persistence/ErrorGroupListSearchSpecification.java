package ch.admin.bit.jeap.errorhandling.infrastructure.persistence;

import ch.admin.bit.jeap.errorhandling.web.api.ErrorGroupListSearchCriteria;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.constraints.NotNull;
import lombok.experimental.UtilityClass;
import org.springframework.data.jpa.domain.Specification;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@UtilityClass
public class ErrorGroupListSearchSpecification {

    public static Specification<Error> fromCriteria(UUID errorGroupId, ErrorGroupListSearchCriteria criteria) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> specs = new ArrayList<>();
            specs.add(ErrorGroupListSearchSpecification.withErrorGroupId(errorGroupId).toPredicate(root, query, criteriaBuilder));
            specs.add(ErrorGroupListSearchSpecification.withStatePermanentOrSendToManualTask().toPredicate(root, query, criteriaBuilder));
            if (criteria != null) {
                criteria.getDateFrom().ifPresent(from -> specs.add(ErrorGroupListSearchSpecification.withFrom(from).toPredicate(root, query, criteriaBuilder)));
                criteria.getDateTo().ifPresent(to -> specs.add(ErrorGroupListSearchSpecification.withTo(to).toPredicate(root, query, criteriaBuilder)));
                criteria.getStacktracePattern().ifPresent(stackTrace -> specs.add(ErrorGroupListSearchSpecification.withStacktrace(stackTrace).toPredicate(root, query, criteriaBuilder)));
                criteria.getMessagePattern().ifPresent(message -> specs.add(ErrorGroupListSearchSpecification.withMessage(message ).toPredicate(root, query, criteriaBuilder)));
            }
            return criteriaBuilder.and(specs.toArray(new Predicate[0]));
        };
    }

    private static Specification<Error> withStatePermanentOrSendToManualTask() {
        return (root, query, builder) -> root.get("state").in("PERMANENT", "SEND_TO_MANUALTASK");
    }

    private Specification<Error> withErrorGroupId(@NotNull UUID errorGroupId) {
        return (errorRoot, q, builder) -> builder.equal(errorRoot.get("errorGroup").get("id"), errorGroupId);
    }

    private Specification<Error> withFrom(@NotNull ZonedDateTime from) {
        return (errorRoot, q, builder) -> builder.greaterThanOrEqualTo(errorRoot.get("created"), from);
    }

    private Specification<Error> withTo(@NotNull ZonedDateTime to) {
        return (errorRoot, q, builder) -> builder.lessThanOrEqualTo(errorRoot.get("created"), to);
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

    private Specification<Error> withMessage(@NotNull Pattern message) {
        return (errorRoot, q, builder) -> {
            Expression<String> stackTrace = errorRoot.get("errorEventData").get("message");
            return builder.isTrue(
                    builder.function(
                            "textregexeq",
                            Boolean.class,
                            stackTrace,
                            builder.literal(message.pattern())
                    )
            );
        };
    }



}
