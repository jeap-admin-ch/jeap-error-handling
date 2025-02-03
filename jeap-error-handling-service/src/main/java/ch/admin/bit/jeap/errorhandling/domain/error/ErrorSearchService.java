package ch.admin.bit.jeap.errorhandling.domain.error;

import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ErrorRepository;
import ch.admin.bit.jeap.errorhandling.web.api.ErrorSearchCriteria;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ErrorSearchService {

    private final ErrorRepository errorRepository;

    public ErrorList search(ErrorSearchCriteria criteria) {
        Page<Error> errors = errorRepository.search(criteria, criteria.getPageable());
        return new ErrorList(errors.getTotalElements(), errors.getContent());
    }

    @Cacheable("eventSources")
    public List<String> getAllEventSources() {
        return errorRepository.getAllEventSources();
    }

    @Cacheable("errorCodes")
    public List<String> getAllErrorCodes() {
        return errorRepository.getAllErrorCodes();
    }

    @Cacheable("eventNames")
    public List<String> getAllEventNames() {
        return errorRepository.getAllEventNames();
    }
}
