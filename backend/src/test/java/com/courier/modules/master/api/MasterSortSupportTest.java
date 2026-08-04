package com.courier.modules.master.api;

import com.courier.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The sort whitelist and the page cap.
 *
 * <p>{@code Pageable} binds whatever property name arrives in the query string straight
 * into an {@code order by}. Unchecked, that is both a 500 on an unmapped name and a way to
 * read a column that is not in the response one row at a time from the ordering.
 */
class MasterSortSupportTest {

    @Test
    @DisplayName("an unknown sort property is a 400 that names the allowed set")
    void unknownSortRejected() {
        // Not a silent fallback: a client whose sort quietly stopped working never finds out.
        Pageable pageable = PageRequest.of(0, 20, Sort.by("passwordHash"));

        assertThatThrownBy(() -> MasterSortSupport.sanitise(pageable))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cannot sort by 'passwordHash'")
                .hasMessageContaining("code");
    }

    @Test
    @DisplayName("a request name is translated to the entity attribute")
    void translatesNames() {
        Pageable sanitised = MasterSortSupport.sanitise(
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdDate")));

        Sort.Order order = sanitised.getSort().getOrderFor("createdAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("no sort at all falls back to display order then name")
    void defaultsToPickerOrder() {
        // The order an administrator arranged the picker in is the one they expect to see.
        Pageable sanitised = MasterSortSupport.sanitise(PageRequest.of(0, 20));

        assertThat(sanitised.getSort()).containsExactly(
                Sort.Order.asc("displayOrder"), Sort.Order.asc("name"));
    }

    @Test
    @DisplayName("the page size is capped at 100")
    void capsPageSize() {
        assertThat(MasterSortSupport.sanitise(PageRequest.of(0, 5000)).getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("a list may add one sortable column of its own without losing the common set")
    void extraColumn() {
        var sortable = MasterSortSupport.withExtra("minWeight", "minWeight");

        assertThat(sortable).containsKeys("code", "name", "status", "displayOrder", "minWeight");

        Pageable sanitised = MasterSortSupport.sanitise(
                PageRequest.of(0, 20, Sort.by("minWeight")), sortable);
        assertThat(sanitised.getSort().getOrderFor("minWeight")).isNotNull();
    }
}
