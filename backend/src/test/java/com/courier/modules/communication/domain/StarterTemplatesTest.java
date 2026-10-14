package com.courier.modules.communication.domain;

import com.courier.modules.communication.application.MetaTemplateConverter;
import com.courier.modules.communication.application.TemplateSampleValues;
import com.courier.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StarterTemplatesTest {

    @Test
    void aSeededEventAndChannelStartsAsExactlyItsSeed() {
        var seed = StarterTemplates.starter(CommunicationEventType.SHIPMENT_BOOKED, CommunicationChannel.WHATSAPP);

        assertThat(seed.templateName()).isEqualTo("shipment_booked");
        assertThat(seed.content()).startsWith("Hi {{customerName}}");
        assertThat(StarterTemplates.isSeeded(CommunicationEventType.SHIPMENT_BOOKED, CommunicationChannel.WHATSAPP)).isTrue();
    }

    @Test
    void anUnseededEventGetsAGeneratedStarterForEveryChannel() {
        for (var event : new CommunicationEventType[]{CommunicationEventType.SHIPMENT_RECEIVED,
                CommunicationEventType.SHIPMENT_CANCELLED, CommunicationEventType.RTO_INITIATED,
                CommunicationEventType.RTO_DELIVERED}) {
            for (var channel : CommunicationChannel.values()) {
                var s = StarterTemplates.starter(event, channel);
                assertThat(s.templateName()).as(event + "/" + channel).isNotBlank();
                assertThat(s.content()).as(event + "/" + channel).isNotBlank();
                assertThat(StarterTemplates.isSeeded(event, channel)).isFalse();
                assertThat(s.subject() != null).as("subject only for email").isEqualTo(channel == CommunicationChannel.EMAIL);
            }
        }
    }

    @Test
    void everyGeneratedWhatsAppStarterCanBeSubmittedToMetaAsItStands() {
        for (var event : CommunicationEventType.values()) {
            if (!StarterTemplates.isAllowed(event, CommunicationChannel.WHATSAPP)) {
                continue;
            }
            var s = StarterTemplates.starter(event, CommunicationChannel.WHATSAPP);
            MetaTemplateConverter.requireValidName(s.templateName());
            var converted = MetaTemplateConverter.convert(s.content(), TemplateSampleValues.all());
            assertThat(converted.bodyText()).as(event.name()).isNotBlank();
        }
    }

    @Test
    void everyVariableAGeneratedStarterUsesIsOneTheAppCanFill() {
        for (var event : CommunicationEventType.values()) {
            for (var channel : CommunicationChannel.values()) {
                if (!StarterTemplates.isAllowed(event, channel)) {
                    continue;
                }
                var s = StarterTemplates.starter(event, channel);
                var matcher = java.util.regex.Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}")
                        .matcher(s.content() + " " + (s.subject() == null ? "" : s.subject()));
                while (matcher.find()) {
                    assertThat(TemplateSampleValues.all()).as(event + "/" + channel + " {{" + matcher.group(1) + "}}")
                            .containsKey(matcher.group(1));
                }
            }
        }
    }

    @Test
    void theDailySummaryIsWhatsAppOnly() {
        assertThat(StarterTemplates.isAllowed(CommunicationEventType.DAILY_BUSINESS_SUMMARY, CommunicationChannel.WHATSAPP)).isTrue();
        assertThat(StarterTemplates.isAllowed(CommunicationEventType.DAILY_BUSINESS_SUMMARY, CommunicationChannel.SMS)).isFalse();
        assertThatThrownBy(() -> StarterTemplates.starter(CommunicationEventType.DAILY_BUSINESS_SUMMARY, CommunicationChannel.EMAIL))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("WhatsApp only");
        assertThat(StarterTemplates.starter(CommunicationEventType.DAILY_BUSINESS_SUMMARY, CommunicationChannel.WHATSAPP)
                .templateName()).isEqualTo("daily_business_summary");
    }

    @Test
    void namesForSmsAndEmailAreReadableTitles() {
        assertThat(StarterTemplates.starter(CommunicationEventType.SHIPMENT_CANCELLED, CommunicationChannel.SMS).templateName())
                .isEqualTo("Shipment Cancelled - SMS");
        assertThat(StarterTemplates.starter(CommunicationEventType.RTO_INITIATED, CommunicationChannel.EMAIL).templateName())
                .isEqualTo("Rto Initiated - Email");
    }
}
