package com.courier.modules.shipment.infrastructure;

import com.courier.modules.communication.domain.ShipmentDirectoryPort;
import com.courier.modules.communication.domain.ShipmentSnapshot;
import com.courier.modules.company.domain.Branch;
import com.courier.modules.company.domain.BranchRepository;
import com.courier.modules.company.domain.Company;
import com.courier.modules.company.domain.CompanyRepository;
import com.courier.modules.customer.domain.Customer;
import com.courier.modules.customer.domain.CustomerRepository;
import com.courier.modules.shipment.domain.Shipment;
import com.courier.modules.shipment.domain.ShipmentAssetRepository;
import com.courier.modules.shipment.domain.ShipmentAssetType;
import com.courier.modules.shipment.domain.ShipmentChargeRepository;
import com.courier.modules.shipment.domain.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * The data-owning side of {@link ShipmentDirectoryPort} — same arrangement {@code
 * company.infrastructure.TicketDirectory}/{@code AuthBranchDirectory} already use: the
 * interface is owned by the consumer ({@code communication}), implemented here where the
 * data lives. Goes straight to repositories, deliberately never through a
 * {@code @PreAuthorize}-guarded service method — {@code CommunicationDispatchJob} calls
 * this from a bare scheduler thread with no authenticated caller at all, the same reason
 * {@code TicketDirectory}/{@code AuthBranchDirectory} do the same. This class only ever
 * answers "what is this shipment's data" — it never decides to send anything, which is what
 * keeps Shipment Booking/Movement themselves (still just {@code ShipmentServiceImpl},
 * publishing plain events) ignorant that a Communication Center exists at all.
 *
 * <p>Sender/receiver {@code Customer} rows are looked up (never created) by exact mobile
 * match — the same row {@code ShipmentServiceImpl.create}'s own {@code
 * CustomerService.findOrCreateForBooking} call already wrote, synchronously, inside the
 * same booking transaction this snapshot is read after. A party with no matching row (a
 * shipment booked before this lookup existed, or a blank contact) simply has no
 * {@code customerId}/{@code email} and defaults every preference to opted-in.
 */
@Component
@RequiredArgsConstructor
public class CommunicationShipmentDirectoryAdapter implements ShipmentDirectoryPort {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentChargeRepository chargeRepository;
    private final ShipmentAssetRepository assetRepository;
    private final BranchRepository branchRepository;
    private final CompanyRepository companyRepository;
    private final CustomerRepository customerRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<ShipmentSnapshot> findSnapshot(UUID companyId, UUID shipmentId) {
        Optional<Shipment> found = shipmentRepository.findByIdWithinCompany(shipmentId, companyId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Shipment shipment = found.get();

        String companyName = companyRepository.findByCompanyId(companyId)
                .map(Company::effectiveDisplayName).orElse("");
        String pickupLocation = branchName(shipment.getBookingBranchId(), companyId);
        String deliveryLocation = branchName(shipment.getDeliveryBranchId(), companyId);

        BigDecimal amount = chargeRepository.findByShipmentIdWithinCompany(shipment.getId(), companyId)
                .map(charge -> charge.getNetAmount()).orElse(null);

        String podUrl = assetRepository.findAllByShipmentIdWithinCompany(shipment.getId(), companyId).stream()
                .filter(a -> a.getAssetType() == ShipmentAssetType.POD && "PHOTO".equals(a.getKind()))
                .findFirst()
                .map(a -> a.getAssetUrl())
                .orElse(null);

        ShipmentSnapshot.Party sender = party(companyId, shipment.getSenderName(), shipment.getSenderContact());
        ShipmentSnapshot.Party receiver = party(companyId, shipment.getReceiverName(), shipment.getReceiverContact());

        return Optional.of(new ShipmentSnapshot(
                shipment.getId(), shipment.getShipmentNumber(), shipment.getTrackingNumber(), companyName,
                sender, receiver, pickupLocation, deliveryLocation, amount, shipment.getExpectedDeliveryDate(),
                podUrl));
    }

    private ShipmentSnapshot.Party party(UUID companyId, String name, String contact) {
        Customer customer = (contact == null || contact.isBlank())
                ? null : customerRepository.findByCompanyIdAndMobile(companyId, contact.trim()).orElse(null);
        return new ShipmentSnapshot.Party(
                name, contact,
                customer == null ? null : customer.getId(),
                customer == null ? null : customer.getEmail(),
                customer == null || customer.isWhatsappEnabled(),
                customer == null || customer.isSmsEnabled(),
                customer != null && customer.getEmail() != null && !customer.getEmail().isBlank()
                        && customer.isEmailEnabled());
    }

    private String branchName(UUID branchId, UUID companyId) {
        if (branchId == null) {
            return null;
        }
        return branchRepository.findByIdWithinCompany(branchId, companyId).map(Branch::getBranchName).orElse(null);
    }
}
