package com.courier.modules.dashboard.infrastructure;

import com.courier.modules.communication.domain.DailyBusinessMetrics;
import com.courier.modules.communication.domain.DailyBusinessMetricsPort;
import com.courier.modules.company.domain.Company;
import com.courier.modules.company.domain.CompanyRepository;
import com.courier.modules.finance.domain.SubTransactionType;
import com.courier.modules.finance.domain.TransactionType;
import com.courier.modules.finance.domain.WalletTransactionRepository;
import com.courier.modules.shipment.domain.ShipmentChargeRepository;
import com.courier.modules.shipment.domain.ShipmentRepository;
import com.courier.modules.shipment.domain.ShipmentStatus;
import com.courier.modules.shipment.domain.ShipmentStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The data-owning side of {@link DailyBusinessMetricsPort}. Every query takes the company id
 * explicitly (this runs on a scheduler thread; see {@code DashboardServiceImpl}'s ISSUE-001 note
 * on never trusting the implicit company filter for a read like this).
 *
 * <p>Definitions, so the numbers can be defended: bookings/revenue use {@code bookingDate} and
 * exclude cancelled shipments, exactly like the dashboard tiles; delivered/cancelled count the
 * status transition that happened inside the day; recharge is every {@code WRC} credit in the
 * company's wallets; "COD / To-Pay settled" is every {@code COD} or {@code TPY} wallet entry
 * that day in either direction.
 */
@Component
@RequiredArgsConstructor
public class DailyBusinessMetricsAdapter implements DailyBusinessMetricsPort {

    private static final List<SubTransactionType> RECHARGE = List.of(SubTransactionType.WRC);
    private static final List<SubTransactionType> COD_TO_PAY = List.of(SubTransactionType.COD, SubTransactionType.TPY);
    private static final List<ShipmentStatus> IN_TRANSIT = List.of(ShipmentStatus.DISPATCHED, ShipmentStatus.IN_SCAN);

    private final ShipmentRepository shipmentRepository;
    private final ShipmentChargeRepository chargeRepository;
    private final ShipmentStatusHistoryRepository historyRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final CompanyRepository companyRepository;

    @Override
    @Transactional(readOnly = true)
    public DailyBusinessMetrics metricsFor(UUID companyId, LocalDate day, Instant from, Instant to) {
        String companyName = companyRepository.findByCompanyId(companyId)
                .map(Company::effectiveDisplayName).orElse("");

        long bookings = shipmentRepository.countByCompanyIdAndStatusNotAndBookingDateBetween(
                companyId, ShipmentStatus.CANCELLED, day, day);
        BigDecimal revenue = zero(chargeRepository.sumNetAmountByCompanyIdAndBookingDateBetween(companyId, day, day));

        BigDecimal rechargeAmount = zero(walletTransactionRepository.sumForCompanyBetween(
                companyId, TransactionType.CR, RECHARGE, from, to));
        long rechargeCount = walletTransactionRepository.countForCompanyBetween(
                companyId, TransactionType.CR, RECHARGE, from, to);

        long delivered = historyRepository.countEnteredStatusBetween(companyId, ShipmentStatus.DELIVERED, from, to);
        long cancelled = historyRepository.countEnteredStatusBetween(companyId, ShipmentStatus.CANCELLED, from, to);
        BigDecimal codToPay = zero(walletTransactionRepository.sumAnyDirectionForCompanyBetween(
                companyId, COD_TO_PAY, from, to));

        long inTransit = shipmentRepository.countByCompanyIdAndStatusIn(companyId, IN_TRANSIT);
        long outForDelivery = shipmentRepository.countByCompanyIdAndStatus(companyId, ShipmentStatus.OUT_FOR_DELIVERY);

        return new DailyBusinessMetrics(companyName, day, bookings, revenue, rechargeAmount, rechargeCount,
                delivered, cancelled, codToPay, inTransit, outForDelivery);
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
