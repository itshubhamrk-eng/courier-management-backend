package com.courier.modules.communication.application;

import com.courier.modules.communication.application.provider.WhatsAppProvider;
import com.courier.modules.communication.domain.CommunicationChannel;
import com.courier.modules.communication.domain.CommunicationEventType;
import com.courier.modules.communication.domain.CommunicationTemplate;
import com.courier.modules.communication.domain.TemplateButton;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsAppPayloadBuilderTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final TemplateRenderer renderer = new TemplateRenderer("http://x/{trackingNumber}");

    private CommunicationTemplate template(String content, String header, List<TemplateButton> buttons, String language) {
        return CommunicationTemplate.builder().eventType(CommunicationEventType.SHIPMENT_BOOKED)
                .channel(CommunicationChannel.WHATSAPP).templateName("shipment_booked").content(content)
                .headerText(header).language(language)
                .buttonsJson(TemplateComponentsCodec.writeButtons(mapper, buttons)).build();
    }

    @Test
    void bodyOnlyTemplateCarriesOnlyBodyParametersAndItsLanguage() {
        var msg = WhatsAppPayloadBuilder.build("9123456780", template("Hi {{customerName}}, AWB {{trackingNumber}}.",
                null, List.of(), "hi"), renderer, Map.of("customerName", "Asha", "trackingNumber", "T1"), mapper);

        assertThat(msg.templateParameters()).containsExactly("Asha", "T1");
        assertThat(msg.languageCode()).isEqualTo("hi");
        assertThat(msg.headerParameter()).isNull();
        assertThat(msg.buttonParameters()).isEmpty();
        assertThat(msg.approvedTemplateName()).isEqualTo("shipment_booked");
    }

    @Test
    void headerVariableBecomesTheHeaderParameter() {
        var msg = WhatsAppPayloadBuilder.build("9", template("Body", "Shipment {{shipmentNumber}}", List.of(), "en_US"),
                renderer, Map.of("shipmentNumber", "SHP-1"), mapper);
        assertThat(msg.headerParameter()).isEqualTo("SHP-1");
    }

    @Test
    void aStaticHeaderSendsNoHeaderParameter() {
        var msg = WhatsAppPayloadBuilder.build("9", template("Body", "Your shipment", List.of(), "en_US"),
                renderer, Map.of(), mapper);
        assertThat(msg.headerParameter()).isNull();
    }

    @Test
    void urlButtonVariablesAreSentByButtonPosition() {
        var buttons = List.of(
                new TemplateButton(TemplateButton.Type.PHONE_NUMBER, "Call us", null, "+919123456780"),
                new TemplateButton(TemplateButton.Type.URL, "Static", "https://x.in/help", null),
                new TemplateButton(TemplateButton.Type.URL, "Track", "https://x.in/t/{{trackingNumber}}", null));

        var msg = WhatsAppPayloadBuilder.build("9", template("Body", null, buttons, "en_US"),
                renderer, Map.of("trackingNumber", "AWB9"), mapper);

        assertThat(msg.buttonParameters()).containsExactly(new WhatsAppProvider.ButtonParameter(2, "AWB9"));
    }

    @Test
    void blankValuesGoOutAsADashBecauseMetaRejectsEmptyParameters() {
        var buttons = List.of(new TemplateButton(TemplateButton.Type.URL, "Track", "https://x.in/t/{{trackingNumber}}", null));
        var msg = WhatsAppPayloadBuilder.build("9", template("A {{podUrl}} B", "{{shipmentNumber}}", buttons, "en_US"),
                renderer, Map.of("podUrl", "", "shipmentNumber", " ", "trackingNumber", ""), mapper);

        assertThat(msg.templateParameters()).containsExactly("-");
        assertThat(msg.headerParameter()).isEqualTo("-");
        assertThat(msg.buttonParameters()).containsExactly(new WhatsAppProvider.ButtonParameter(0, "-"));
    }
}
