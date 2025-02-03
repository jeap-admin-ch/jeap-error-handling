package ch.admin.bit.jeap.errorhandling.domain.housekeeping;

import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.*;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error.ErrorState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RepositoryHousekeeping {
    public static final List<ErrorState> ERROR_STATES = List.of(
            ErrorState.TEMPORARY_RETRIED,
            ErrorState.PERMANENT_RETRIED,
            ErrorState.DELETED,
            ErrorState.PERMANENT);
    private final ErrorRepository errorRepository;
    private final ErrorGroupRepository errorGroupRepository;
    private final CausingEventRepository causingEventRepository;
    private final ScheduledResendRepository scheduledResendRepository;
    private final AuditLogRepository auditLogRepository;
    private final HouseKeepingServiceConfigProperties configProperties;

    /**
     * @return true if there are more errors to delete, false otherwise
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean deleteErrorsOlderThan(ZonedDateTime olderThan) {
        final Slice<ErrorQueryResult> resultPage = errorRepository
                .findIdByStateInAndCreatedBefore(ERROR_STATES, olderThan, Pageable.ofSize(configProperties.getPageSize()));
        log.info("Housekeeping: found {} errors to delete", resultPage.getNumberOfElements());
        final Set<UUID> errorIds = resultPage.toSet().stream().map(ErrorQueryResult::getId).collect(Collectors.toSet());
        log.info("Housekeeping: delete scheduled resends...");
        scheduledResendRepository.deleteAllByErrorIdIn(errorIds);
        log.info("Housekeeping: delete audit logs...");
        auditLogRepository.deleteAllByErrorIdIn(errorIds);
        log.info("Housekeeping: delete errors...");
        errorRepository.deleteAllById(errorIds);
        log.info("Housekeeping: deleted {} errors", errorIds.size());
        return resultPage.hasNext();
    }

    /**
     * @return true if there are more causing events to delete, false otherwise
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean deleteCausingEventsWithoutRelatedErrors() {
        final Slice<UUID> resultPage = causingEventRepository
                .findCausingEventIdsWithoutError(Pageable.ofSize(configProperties.getPageSize()));
        log.info("Housekeeping: found {} causing events to delete", resultPage.getNumberOfElements());
        log.info("Housekeeping: delete causing events...");
        causingEventRepository.deleteAllHeadersByCausingEventIds(resultPage.getContent());
        causingEventRepository.deleteAllById(resultPage.getContent());
        log.info("Housekeeping: causing events deleted");
        return resultPage.hasNext();
    }

    /**
     * @return true if there are more error groups to delete, false otherwise
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean deleteErrorGroupsNotReferencedByAnyError() {
        final Slice<UUID> resultPage = errorGroupRepository
                .findUnreferencedErrorGroups(Pageable.ofSize(configProperties.getPageSize()));
        log.info("Housekeeping: found {} error groups to delete", resultPage.getNumberOfElements());
        log.info("Housekeeping: delete error groups...");
        errorGroupRepository.deleteAllById(resultPage.getContent());
        log.info("Housekeeping: error groups deleted");
        return resultPage.hasNext();
    }
}
