package com.courier.modules.communication.application;

import com.courier.modules.communication.domain.TemplateButton;
import com.courier.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateComponentRulesTest {

    private static TemplateButton link(String text, String url) {
        return new TemplateButton(TemplateButton.Type.URL, text, url, null);
    }

    private static TemplateButton call(String text, String phone) {
        return new TemplateButton(TemplateButton.Type.PHONE_NUMBER, text, null, phone);
    }

    @Test
    void categoryAndLanguageMustBeOnesWeOffer() {
        assertThat(TemplateComponentRules.category(" utility ")).isEqualTo("UTILITY");
        assertThat(TemplateComponentRules.language("hi")).isEqualTo("hi");
        assertThatThrownBy(() -> TemplateComponentRules.category("PROMO")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> TemplateComponentRules.category(null)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> TemplateComponentRules.language("fr")).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("en_US");
        assertThatThrownBy(() -> TemplateComponentRules.language("English")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void headerAllowsOneVariableOneLineAndSixtyCharacters() {
        assertThat(TemplateComponentRules.header("  Shipment {{shipmentNumber}}  ")).isEqualTo("Shipment {{shipmentNumber}}");
        assertThat(TemplateComponentRules.header("   ")).isNull();
        assertThat(TemplateComponentRules.header(null)).isNull();
        assertThatThrownBy(() -> TemplateComponentRules.header("x".repeat(61))).hasMessageContaining("60");
        assertThatThrownBy(() -> TemplateComponentRules.header("a\nb")).hasMessageContaining("single line");
        assertThatThrownBy(() -> TemplateComponentRules.header("{{customerName}} {{trackingNumber}}"))
                .hasMessageContaining("at most one");
    }

    @Test
    void footerForbidsVariables() {
        assertThat(TemplateComponentRules.footer("Thank you!")).isEqualTo("Thank you!");
        assertThatThrownBy(() -> TemplateComponentRules.footer("Hi {{customerName}}")).hasMessageContaining("footer");
        assertThatThrownBy(() -> TemplateComponentRules.footer("x".repeat(61))).hasMessageContaining("60");
    }

    @Test
    void buttonsAreValidatedAndCleaned() {
        var clean = TemplateComponentRules.buttons(List.of(
                link(" Track ", "https://courier.skra.in/track/{{trackingNumber}}"),
                call("Call us", "+91 91234-56780")));

        assertThat(clean).hasSize(2);
        assertThat(clean.get(0).text()).isEqualTo("Track");
        assertThat(clean.get(1).phoneNumber()).isEqualTo("+919123456780");
        assertThat(TemplateComponentRules.buttons(null)).isEmpty();
        assertThat(TemplateComponentRules.buttons(List.of())).isEmpty();
    }

    @Test
    void rejectsBadLabelsLinksAndNumbers() {
        assertThatThrownBy(() -> TemplateComponentRules.buttons(List.of(link("", "https://a.b"))))
                .hasMessageContaining("Button 1").hasMessageContaining("label");
        assertThatThrownBy(() -> TemplateComponentRules.buttons(List.of(link("x".repeat(26), "https://a.b"))))
                .hasMessageContaining("25");
        assertThatThrownBy(() -> TemplateComponentRules.buttons(List.of(link("Go", "http://a.b"))))
                .hasMessageContaining("https://");
        assertThatThrownBy(() -> TemplateComponentRules.buttons(List.of(call("Call", "9123456780"))))
                .hasMessageContaining("country code");
        assertThatThrownBy(() -> TemplateComponentRules.buttons(List.of(new TemplateButton(null, "x", null, null))))
                .hasMessageContaining("Link or Call");
    }

    @Test
    void aLinkVariableMustBeSingleAndLast() {
        TemplateComponentRules.buttons(List.of(link("Track", "https://x.in/t/{{trackingNumber}}")));
        assertThatThrownBy(() -> TemplateComponentRules.buttons(List.of(link("T", "https://x.in/{{trackingNumber}}/more"))))
                .hasMessageContaining("very end");
        assertThatThrownBy(() -> TemplateComponentRules.buttons(List.of(link("T", "https://x.in/{{a}}/{{b}}"))))
                .hasMessageContaining("at most one");
    }

    @Test
    void capsTwoLinksAndOneCall() {
        assertThatThrownBy(() -> TemplateComponentRules.buttons(List.of(
                link("A", "https://a.b"), link("B", "https://a.b"), link("C", "https://a.b"))))
                .hasMessageContaining("2 link");
        assertThatThrownBy(() -> TemplateComponentRules.buttons(List.of(
                call("A", "+919123456780"), call("B", "+919123456781"))))
                .hasMessageContaining("1 call");
    }

    @Test
    void samplesOnlyForKnownVariablesSingleLineAndBlanksDropped() {
        var clean = TemplateComponentRules.samples(Map.of("customerName", " Asha ", "amount", "  "));
        assertThat(clean).containsOnlyKeys("customerName").containsEntry("customerName", "Asha");
        assertThatThrownBy(() -> TemplateComponentRules.samples(Map.of("nope", "x")))
                .hasMessageContaining("not a variable");
        assertThatThrownBy(() -> TemplateComponentRules.samples(Map.of("customerName", "a\nb")))
                .hasMessageContaining("single line");
        assertThatThrownBy(() -> TemplateComponentRules.samples(Map.of("customerName", "x".repeat(201))))
                .hasMessageContaining("too long");
        assertThat(TemplateComponentRules.samples(null)).isEmpty();
    }
}
