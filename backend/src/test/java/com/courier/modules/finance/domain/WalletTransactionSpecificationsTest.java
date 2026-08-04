package com.courier.modules.finance.domain;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The one behaviour of the ledger specification that must never regress: with no wallet
 * scope it matches nothing.
 *
 * <p>A money query that falls back to "no filter" returns every branch's statement to
 * whoever asked. Failing closed is the difference between an empty page and a data breach,
 * so it gets its own test rather than being implied by the service tests.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WalletTransactionSpecificationsTest {

    @Mock private Root<WalletTransaction> root;
    @Mock private CriteriaQuery<?> query;
    @Mock private CriteriaBuilder builder;
    @Mock private Predicate disjunction;
    @Mock private Predicate conjunction;

    @Test
    @DisplayName("criteria without a wallet id match nothing")
    void failsClosedWithoutWalletScope() {
        when(builder.disjunction()).thenReturn(disjunction);

        WalletTransactionSpecifications.matching(WalletTransactionCriteria.none())
                .toPredicate(root, query, builder);

        verify(builder).disjunction();
        verify(builder, never()).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("a null criteria object matches nothing either")
    void failsClosedOnNull() {
        when(builder.disjunction()).thenReturn(disjunction);

        WalletTransactionSpecifications.matching(null).toPredicate(root, query, builder);

        verify(builder).disjunction();
    }

    @Test
    @DisplayName("with a wallet scope the predicates are built normally")
    void buildsWithWalletScope() {
        when(builder.and(any(Predicate[].class))).thenReturn(conjunction);
        when(root.get("walletId")).thenReturn(null);

        WalletTransactionCriteria scoped =
                WalletTransactionCriteria.none().scopedTo(UUID.randomUUID(), UUID.randomUUID());

        WalletTransactionSpecifications.matching(scoped).toPredicate(root, query, builder);

        verify(builder, never()).disjunction();
        verify(builder).and(any(Predicate[].class));
    }
}
