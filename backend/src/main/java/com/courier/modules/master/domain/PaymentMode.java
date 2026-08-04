package com.courier.modules.master.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import com.courier.shared.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLRestriction;

/**
 * How a shipment is paid for — PAID, TO_PAY, TBB, COD, and any variant a company sells.
 *
 * <p>There is deliberately no {@code PaymentModeType} enum beside these rows. The four
 * canonical modes <i>are</i> rows; an enum repeating them would be a second source of
 * truth that drifts the first time a company adds "PAID_ONLINE". What the shipment and
 * wallet modules actually need to branch on is behaviour, so the behaviour is flags:
 *
 * <ul>
 *   <li>PAID — collect at booking</li>
 *   <li>TO_PAY — collect at delivery</li>
 *   <li>TBB (to be billed) — neither; settles against a credit account on an invoice</li>
 *   <li>COD — collect at delivery, and the amount is the consignee's, not the freight</li>
 * </ul>
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "master_payment_modes",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_master_payment_modes_code",
                        columnNames = {"company_id", "code"}),
                @UniqueConstraint(name = "uk_master_payment_modes_name",
                        columnNames = {"company_id", "name"})
        },
        indexes = @Index(name = "idx_master_payment_modes_status",
                columnList = "company_id, status"))
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class PaymentMode extends MasterDataEntity {

    @Column(name = "collect_at_booking", nullable = false)
    private boolean collectAtBooking = false;

    @Column(name = "collect_at_delivery", nullable = false)
    private boolean collectAtDelivery = false;

    /** True for TBB: the shipment is billed to an account rather than collected. */
    @Column(name = "requires_credit_account", nullable = false)
    private boolean requiresCreditAccount = false;

    /** True for COD: money is collected from the consignee on behalf of the shipper. */
    @Column(name = "is_cash_on_delivery", nullable = false)
    private boolean cashOnDelivery = false;

    @Override
    protected void applySpecificInvariants() {
        if (collectAtBooking && collectAtDelivery) {
            throw new BusinessRuleException(
                    "A payment mode cannot collect both at booking and at delivery.");
        }
        if (cashOnDelivery && !collectAtDelivery) {
            throw new BusinessRuleException(
                    "Cash on delivery must collect at delivery.");
        }
        if (requiresCreditAccount && (collectAtBooking || collectAtDelivery)) {
            throw new BusinessRuleException(
                    "A billed mode settles on an invoice, so it cannot also collect cash.");
        }
    }
}
