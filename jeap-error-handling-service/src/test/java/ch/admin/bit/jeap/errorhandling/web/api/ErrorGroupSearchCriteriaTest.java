package ch.admin.bit.jeap.errorhandling.web.api;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorGroupSearchCriteriaTest {

    @Test
    void getPageable_shouldUseRequestedSortWhenValid() {
        ErrorGroupSearchCriteria criteria = ErrorGroupSearchCriteria.builder()
                .pageIndex(1)
                .pageSize(20)
                .sortField("errorCount")
                .sortOrder("asc")
                .build();

        Pageable pageable = criteria.getPageable("latestErrorAt", "DESC");

        Sort.Order order = pageable.getSort().getOrderFor("errorCount");
        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void getPageable_shouldUseConfiguredDefaultWhenRequestedSortIsInvalid() {
        ErrorGroupSearchCriteria criteria = ErrorGroupSearchCriteria.builder()
                .sortField("unsupportedField")
                .sortOrder("unsupportedOrder")
                .build();

        Pageable pageable = criteria.getPageable("firstErrorAt", "ASC");

        Sort.Order order = pageable.getSort().getOrderFor("firstErrorAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void getPageable_shouldUseCodeFallbackWhenRequestedAndConfiguredSortAreInvalid() {
        ErrorGroupSearchCriteria criteria = ErrorGroupSearchCriteria.builder()
                .sortField("unsupportedField")
                .sortOrder("unsupportedOrder")
                .build();

        Pageable pageable = criteria.getPageable("anotherUnsupportedField", "anotherUnsupportedOrder");

        Sort.Order order = pageable.getSort().getOrderFor("latestErrorAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }
}
