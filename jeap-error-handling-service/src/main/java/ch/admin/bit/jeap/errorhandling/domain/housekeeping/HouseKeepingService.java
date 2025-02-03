package ch.admin.bit.jeap.errorhandling.domain.housekeeping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

import static ch.admin.bit.jeap.errorhandling.domain.housekeeping.RepositoryHousekeeping.ERROR_STATES;

@Component
@Slf4j
@RequiredArgsConstructor
public class HouseKeepingService {
    private final RepositoryHousekeeping repositoryHousekeeping;
    private final HouseKeepingServiceConfigProperties configProperties;

    public void cleanup() {
        deleteOldErrors();
        deleteOldCausingEvents();
        deleteOldErrorGroups();
    }

    private void deleteOldErrors() {
        ZonedDateTime olderThan = ZonedDateTime.now().minus(configProperties.getErrorMaxAge());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy - HH:mm:ss Z");
        log.info("Housekeeping: Delete Errors which are older than {} ({}) with State in {}",
                configProperties.getErrorMaxAge(), olderThan.format(formatter), ERROR_STATES);

        executeInTransactionPerPage(() -> repositoryHousekeeping.deleteErrorsOlderThan(olderThan));

        log.info("Housekeeping: Deleted all old errors");
    }

    private void deleteOldCausingEvents() {
        log.info("Housekeeping: Delete causing events without errors relation");

        executeInTransactionPerPage(repositoryHousekeeping::deleteCausingEventsWithoutRelatedErrors);

        log.info("Housekeeping: Deleted all old causing events");
    }

    private void deleteOldErrorGroups() {
        log.info("Housekeeping: Delete error groups without errors)");

        executeInTransactionPerPage(repositoryHousekeeping::deleteErrorGroupsNotReferencedByAnyError);

        log.info("Housekeeping: Deleted all old error groups");
    }

    /**
     * The mix of JPQL and native queries in housekeeping requires care when querying for objects deleted by native
     * queries. A hibernate session flush is forced after every page by using a new transaction. This also reduces
     * transaction size and minimizes the duration of locks during housekeeping.
     */
    private void executeInTransactionPerPage(Supplier<Boolean> callback) {
        int pages = 0;
        while (pages < configProperties.getMaxPages()) {
            boolean hasMorePages = callback.get();
            if (!hasMorePages) {
                break;
            }
            pages++;
        }
    }
}
