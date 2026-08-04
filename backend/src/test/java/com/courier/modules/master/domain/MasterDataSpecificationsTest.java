package com.courier.modules.master.domain;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyChar;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The two behaviours of the shared master specification that are worth pinning down: an
 * explicit but empty id scope must match nothing, and a search term's LIKE wildcards must
 * be escaped.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MasterDataSpecificationsTest {

    @Mock private Root<Country> root;
    @Mock private CriteriaQuery<?> query;
    @Mock private CriteriaBuilder builder;
    @Mock private Predicate disjunction;
    @Mock private Predicate conjunction;

    @Test
    @DisplayName("no criteria at all is an unconstrained query, not an empty one")
    void emptyCriteriaMatchesEverything() {
        // The company boundary is the Hibernate filter, not this. A master list with no
        // filters legitimately shows the whole company's rows.
        when(builder.conjunction()).thenReturn(conjunction);

        MasterDataSpecifications.<Country>matching(MasterDataCriteria.none())
                .toPredicate(root, query, builder);

        verify(builder).conjunction();
        verify(builder, never()).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("an explicit but empty id scope matches nothing")
    void emptyIdScopeMatchesNothing() {
        // "The ids this caller may see, of which there are none" must not silently
        // degrade into "no id filter" — that would show them everything.
        when(builder.disjunction()).thenReturn(disjunction);
        when(builder.and(any(Predicate[].class))).thenReturn(conjunction);

        MasterDataSpecifications.<Country>matching(MasterDataCriteria.none().withIds(Set.of()))
                .toPredicate(root, query, builder);

        verify(builder).disjunction();
    }

    @Test
    @DisplayName("LIKE wildcards in a search term are escaped")
    void escapesLikeWildcards() {
        // Without this, searching for "100%" matches every row rather than the pincode
        // zone actually called that.
        Path<String> path = mock(Path.class);
        Expression<String> lowered = mock(Expression.class);
        when(root.get(anyString())).thenReturn((Path) path);
        when(builder.lower(any())).thenReturn(lowered);
        when(builder.and(any(Predicate[].class))).thenReturn(conjunction);
        when(builder.or(any(Predicate[].class))).thenReturn(conjunction);

        MasterDataSpecifications.<Country>matching(
                        new MasterDataCriteria(null, null, "100%_x", null, Map.of()))
                .toPredicate(root, query, builder);

        ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
        verify(builder, org.mockito.Mockito.atLeastOnce())
                .like(eq(lowered), pattern.capture(), anyChar());

        assertThat(pattern.getValue()).isEqualTo("%100\\%\\_x%");
    }

    @Test
    @DisplayName("extra equality filters are applied against the named attribute")
    void appliesEqualityFilters() {
        Path<Object> path = mock(Path.class);
        when(root.get("countryId")).thenReturn(path);
        when(builder.and(any(Predicate[].class))).thenReturn(conjunction);

        UUID countryId = UUID.randomUUID();
        MasterDataSpecifications.<Country>matching(
                        MasterDataCriteria.none().with("countryId", countryId))
                .toPredicate(root, query, builder);

        verify(root).get("countryId");
        verify(builder).equal(path, countryId);
    }

    @Test
    @DisplayName("a null filter value is dropped rather than becoming IS NULL")
    void nullFiltersAreDropped() {
        // So a controller can chain every optional query parameter unconditionally and an
        // absent one does not silently mean "where the column is null".
        when(builder.conjunction()).thenReturn(conjunction);

        MasterDataCriteria criteria = MasterDataCriteria.none().with("countryId", null);
        assertThat(criteria.equalities()).isEmpty();

        MasterDataSpecifications.<Country>matching(criteria).toPredicate(root, query, builder);
        verify(root, never()).get("countryId");
    }
}
