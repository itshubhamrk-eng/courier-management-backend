package com.courier.modules.master.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The guards that make assembling this SQL by concatenation defensible.
 *
 * <p>Table and column names come from constants in this module, but the check is enforced
 * rather than assumed: the day someone passes a variable is the day the argument that this
 * is safe stops holding on its own.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MasterUniquenessCheckerTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Mock private EntityManager entityManager;
    @Mock private Query query;

    private MasterUniquenessChecker checker;

    @BeforeEach
    void setUp() {
        checker = new MasterUniquenessChecker(entityManager);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(0L);
    }

    @Test
    @DisplayName("a table outside the allow-list is refused before any SQL is built")
    void unknownTableRefused() {
        assertThatThrownBy(() -> checker.isCodeTaken("users", TENANT, null, "ADMIN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown master table");

        verify(entityManager, never()).createNativeQuery(anyString());
    }

    @Test
    @DisplayName("a column name that is not a plain identifier is refused")
    void injectedColumnRefused() {
        assertThatThrownBy(() -> checker.isTaken(MasterTable.COUNTRIES, TENANT, null,
                Map.of("name = 'x' OR 1=1 --", "anything")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Illegal column name");
    }

    @Test
    @DisplayName("values are bound, never interpolated")
    void valuesAreBound() {
        checker.isCodeTaken(MasterTable.COUNTRIES, TENANT, null, "IN'; DROP TABLE x; --");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sql.capture());

        assertThat(sql.getValue()).doesNotContain("DROP TABLE");
        assertThat(sql.getValue()).contains("LOWER(code) = LOWER(:p0)");
        verify(query).setParameter("p0", "IN'; DROP TABLE x; --");
    }

    @Test
    @DisplayName("the row being updated is excluded so it cannot clash with itself")
    void excludesSelf() {
        UUID self = UUID.randomUUID();
        checker.isCodeTaken(MasterTable.STATES, TENANT, self, "MH");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sql.capture());

        assertThat(sql.getValue()).contains(":excludeId IS NULL OR id <> :excludeId");
    }

    @Test
    @DisplayName("a multi-column scope produces one predicate per column")
    void multiColumnScope() {
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("country_id", UUID.randomUUID());
        scope.put("name", "Maharashtra");

        checker.isTaken(MasterTable.STATES, TENANT, null, scope);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sql.capture());

        assertThat(sql.getValue()).contains("country_id = :p0");
        assertThat(sql.getValue()).contains("LOWER(name) = LOWER(:p1)");
    }

    @Test
    @DisplayName("the count is compared in Java, because MySQL returns BIGINT")
    void countComparedInJava() {
        // A repository that declared `boolean` for `COUNT(*) > 0` made every create a 500
        // in this project once already - see CHANGELOG 0.3.0.
        when(query.getSingleResult()).thenReturn(2L);
        assertThat(checker.isCodeTaken(MasterTable.CITIES, TENANT, null, "PUNE")).isTrue();

        when(query.getSingleResult()).thenReturn(0L);
        assertThat(checker.isCodeTaken(MasterTable.CITIES, TENANT, null, "PUNE")).isFalse();
    }

    @Test
    @DisplayName("a check with no columns at all is a programming error, not an empty query")
    void requiresAtLeastOneColumn() {
        assertThatThrownBy(() -> checker.isTaken(MasterTable.AREAS, TENANT, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
