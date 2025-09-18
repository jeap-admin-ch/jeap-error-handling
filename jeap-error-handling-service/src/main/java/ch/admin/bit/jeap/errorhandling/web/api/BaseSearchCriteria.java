package ch.admin.bit.jeap.errorhandling.web.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class BaseSearchCriteria {
    public static final Integer PAGE_DEFAULT_VALUE = 0;
    public static final Integer SIZE_DEFAULT_VALUE = 10;

    @JsonIgnore
    public Pageable getPageable() {
        return PageRequest.of(
                ObjectUtils.firstNonNull(getPageIndex(), PAGE_DEFAULT_VALUE),
                ObjectUtils.firstNonNull(getPageSize(), SIZE_DEFAULT_VALUE),
                Sort.by(getOrders()));
    }

    protected List<Sort.Order> getOrders() {
        String[] sort = getSort();
        List<Sort.Order> orders = new ArrayList<>();
        if (sort != null && sort.length > 0) {
            if (hasMultipleColumns(sort)) {
                for (String sortOrder : sort) {
                    String[] propertyDirection = sortOrder.split(",");
                    Assert.isTrue(hasPropertyAndDirection(propertyDirection), String.format("Invalid sort value '%s' ", Arrays.toString(propertyDirection)));
                    orders.add(new Sort.Order(getDirection(propertyDirection[1]), propertyDirection[0]));
                }
            } else {
                Assert.isTrue(hasPropertyAndDirection(sort), String.format("Invalid sort value '%s' ", Arrays.toString(sort)));
                orders.add(new Sort.Order(getDirection(sort[1]), sort[0]));
            }
        }
        return orders;
    }

    private boolean hasPropertyAndDirection(String[] array) {
        return (array.length == 2);
    }

    private boolean hasMultipleColumns(String[] sort) {
        return sort[0].contains(",");
    }

    private Sort.Direction getDirection(String direction) {
        return Sort.Direction.fromString(direction);
    }

    protected abstract Integer getPageIndex();
    protected abstract Integer getPageSize();
    protected abstract String[] getSort();
}

