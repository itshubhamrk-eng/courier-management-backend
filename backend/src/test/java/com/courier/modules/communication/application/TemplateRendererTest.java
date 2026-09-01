package com.courier.modules.communication.application;

import com.courier.modules.communication.domain.CommunicationEventType;
import com.courier.modules.communication.domain.ShipmentSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateRendererTest {

    private final TemplateRenderer renderer =
            new TemplateRenderer("http://localhost:4200/track/{trackingNumber}");

    private ShipmentSnapshot sample() {
        ShipmentSnapshot.Party sender = new ShipmentSnapshot.Party(
                "Rahul", "9876500000", null, "rahul@test.com", true, true, true);
        ShipmentSnapshot.Party receiver = new ShipmentSnapshot.Party(
                "Priya", "9876500001", null, "priya@test.com", true, true, true);
        return new ShipmentSnapshot(UUID.randomUUID(), "SHP-001", "AWB123", "Acme Logistics",
                sender, receiver, "Pune Hub", "Mumbai Hub",
                new BigDecimal("450.00"), LocalDate.of(2026, 8, 25), "https://store/pod.jpg");
    }

    @Test
    void substitutesEveryDocumentedVariable() {
        String content = "Hi {{customerName}} from {{companyName}}: {{shipmentNumber}}/{{trackingNumber}} "
                + "{{pickupLocation}}->{{deliveryLocation}} amount {{amount}} by {{deliveryDate}} "
                + "receiver {{receiverName}} track {{trackingUrl}} pod {{podUrl}}";

        String rendered = renderer.render(content, sample(), CommunicationEventType.SHIPMENT_BOOKED, "Rahul");

        assertThat(rendered)
                .contains("Hi Rahul from Acme Logistics")
                .contains("SHP-001/AWB123")
                .contains("Pune Hub->Mumbai Hub")
                .contains("amount 450")
                .contains("receiver Priya")
                .contains("track http://localhost:4200/track/AWB123")
                .contains("pod https://store/pod.jpg");
    }

    @Test
    void leavesUnrecognisedPlaceholderVerbatim() {
        String rendered = renderer.render("Hello {{doesNotExist}}", sample(),
                CommunicationEventType.SHIPMENT_BOOKED, "Rahul");
        assertThat(rendered).isEqualTo("Hello {{doesNotExist}}");
    }

    @Test
    void blankFieldsRenderAsEmptyStringNotNull() {
        ShipmentSnapshot noAmount = new ShipmentSnapshot(UUID.randomUUID(), "SHP-002", "AWB999", "Acme",
                sample().sender(), sample().receiver(), "A", "B", null, null, null);
        String rendered = renderer.render("[{{amount}}][{{deliveryDate}}][{{podUrl}}]", noAmount,
                CommunicationEventType.SHIPMENT_BOOKED, "Rahul");
        assertThat(rendered).isEqualTo("[][][]");
    }
}
