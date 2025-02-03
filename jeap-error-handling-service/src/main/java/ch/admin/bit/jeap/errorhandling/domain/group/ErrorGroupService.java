package ch.admin.bit.jeap.errorhandling.domain.group;

import ch.admin.bit.jeap.errorhandling.domain.exceptions.ErrorGroupNotFoundException;
import ch.admin.bit.jeap.errorhandling.domain.exceptions.TicketNumberAlreadyExistsException;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ErrorGroup;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ErrorGroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.springframework.util.StringUtils.hasText;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ErrorGroupService {

    private final ErrorGroupConfigProperties errorGroupConfigProperties;
    private final ErrorGroupRepository errorGroupRepository;
    private final PlatformTransactionManager transactionManager;

    public ErrorGroup assignToErrorGroup(Error error) {
        // error grouping is based on the error's stack trace hash: no hash -> no group
        if (errorGroupConfigProperties.isErrorGroupingEnabled() && hasText(error.getErrorEventData().getStackTraceHash())) {
            ErrorGroup errorGroup = findOrCreateMatchingErrorGroup(error);
            error.setErrorGroup(errorGroup);
            return errorGroup;
        } else {
            return null;
        }
    }

    public ErrorGroup updateTicketNumber(UUID errorGroupId, String ticketNumber) {
        ErrorGroup errorGroup = findErrorGroup(errorGroupId);
        // check if ticketNumber is already assigned to another group
        if (errorGroupRepository.existsByTicketNumber(ticketNumber)) {
            throw new TicketNumberAlreadyExistsException("Ticket number " + ticketNumber + " already exists");
        }
        errorGroup.setTicketNumber(ticketNumber);
        return errorGroup;
    }

    public ErrorGroup updateFreeText(UUID errorGroupId, String freeText) {
        ErrorGroup errorGroup = findErrorGroup(errorGroupId);
        errorGroup.setFreeText(freeText);
        return errorGroup;
    }

    public ErrorGroupAggregatedData getErrorGroupAggregatedData(UUID errorGroupId) {
        return errorGroupRepository.findErrorGroupAggregatedData(errorGroupId).
                orElseThrow(() -> new ErrorGroupNotFoundException(errorGroupId));
    }

    public ErrorGroupAggregatedDataList findErrorGroupAggregatedData(Pageable pageable) {
        Page<ErrorGroupAggregatedData> groupAggregatedData = errorGroupRepository.findErrorGroupAggregatedData(pageable);
        return new ErrorGroupAggregatedDataList(groupAggregatedData.getTotalElements(), groupAggregatedData.getContent());
    }


    private ErrorGroup findOrCreateMatchingErrorGroup(Error error) {
        ErrorGroup errorGroup = ErrorGroup.from(error);
        try {
            return saveOrGetErrorGroup(errorGroup);
        } catch (TransactionException e) {
            if (e.contains(DataIntegrityViolationException.class)) {
                // Error group already created by a concurrent transaction
                // -> retry to get the now existing error group
                return saveOrGetErrorGroup(errorGroup);
            }
            throw e;
        }
    }

    private ErrorGroup saveOrGetErrorGroup(ErrorGroup errorGroup) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> {
            Optional<ErrorGroup> existingErrorGroup = findMatchingErrorGroup(errorGroup);
            if (existingErrorGroup.isEmpty()) {
                log.debug("Creating new error group: {}.", errorGroup);
                return errorGroupRepository.save(errorGroup);
            }
            else {
                log.debug("Found existing error group: {}.", existingErrorGroup.get());
                return existingErrorGroup.get();
            }
        });
    }

    private ErrorGroup findErrorGroup(UUID errorGroupId) {
        return errorGroupRepository.findById(errorGroupId).
                orElseThrow(() -> new ErrorGroupNotFoundException(errorGroupId));
    }

    private Optional<ErrorGroup> findMatchingErrorGroup(ErrorGroup errorGroup) {
        return errorGroupRepository.findByErrorPublisherAndErrorCodeAndEventNameAndErrorStackTraceHash(
                errorGroup.getErrorPublisher(), errorGroup.getErrorCode(), errorGroup.getEventName(),
                errorGroup.getErrorStackTraceHash());
    }

}
