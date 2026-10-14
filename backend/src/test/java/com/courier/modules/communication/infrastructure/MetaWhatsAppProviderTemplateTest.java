package com.courier.modules.communication.infrastructure;

import com.courier.modules.communication.application.provider.ProviderSendException;
import com.courier.modules.communication.application.provider.WhatsAppProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MetaWhatsAppProviderTemplateTest {

    private static final WhatsAppProvider.WhatsAppCredentials CREDS =
            new WhatsAppProvider.WhatsAppCredentials("111", "WABA9", "tok");

    private MockRestServiceServer server;
    private MetaWhatsAppProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new MetaWhatsAppProvider(builder, "91", "en_US");
    }

    @Test
    void submitPostsNameLanguageCategoryAndBodyWithExamples() {
        server.expect(requestTo("https://graph.facebook.com/v19.0/WABA9/message_templates"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer tok"))
                .andExpect(jsonPath("$.name").value("shipment_booked"))
                .andExpect(jsonPath("$.language").value("en_US"))
                .andExpect(jsonPath("$.category").value("UTILITY"))
                .andExpect(jsonPath("$.components[0].type").value("BODY"))
                .andExpect(jsonPath("$.components[0].text").value("Hi {{1}} bye"))
                .andExpect(jsonPath("$.components[0].example.body_text[0][0]").value("Rahul"))
                .andRespond(withSuccess("{\"id\":\"555\",\"status\":\"PENDING\",\"category\":\"UTILITY\"}",
                        MediaType.APPLICATION_JSON));

        var result = provider.submitTemplate(new WhatsAppProvider.TemplateSubmission(
                "shipment_booked", "en_US", "UTILITY", "Hi {{1}} bye", List.of("Rahul")), CREDS);

        assertThat(result.metaTemplateId()).isEqualTo("555");
        assertThat(result.status()).isEqualTo("PENDING");
        server.verify();
    }

    @Test
    void submitOmitsTheExampleBlockWhenThereAreNoVariables() {
        server.expect(requestTo("https://graph.facebook.com/v19.0/WABA9/message_templates"))
                .andExpect(jsonPath("$.components[0].example").doesNotExist())
                .andRespond(withSuccess("{\"id\":\"1\",\"status\":\"PENDING\"}", MediaType.APPLICATION_JSON));

        var result = provider.submitTemplate(new WhatsAppProvider.TemplateSubmission(
                "plain", "en_US", "UTILITY", "Just text", List.of()), CREDS);

        assertThat(result.category()).isEqualTo("UTILITY"); // falls back to what we asked for
    }

    @Test
    void submitSurfacesMetasOwnReasonOnRejection() {
        server.expect(requestTo("https://graph.facebook.com/v19.0/WABA9/message_templates"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"Invalid parameter\",\"code\":100,"
                                + "\"error_user_msg\":\"Template name already exists in this language.\"}}"));

        assertThatThrownBy(() -> provider.submitTemplate(new WhatsAppProvider.TemplateSubmission(
                "x", "en_US", "UTILITY", "t", List.of()), CREDS))
                .isInstanceOf(ProviderSendException.class)
                .hasMessageContaining("Template name already exists in this language.")
                .hasMessageContaining("code 100");
    }

    @Test
    void submitNeedsABusinessAccountIdAndToken() {
        var noWaba = new WhatsAppProvider.WhatsAppCredentials("111", null, "tok");
        assertThatThrownBy(() -> provider.submitTemplate(new WhatsAppProvider.TemplateSubmission(
                "x", "en_US", "UTILITY", "t", List.of()), noWaba))
                .isInstanceOf(ProviderSendException.class).hasMessageContaining("Business Account ID");
    }

    @Test
    void statusPicksTheRowMatchingNameAndLanguage() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://graph.facebook.com/v19.0/WABA9/message_templates?")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"data\":["
                        + "{\"id\":\"1\",\"name\":\"shipment_booked\",\"language\":\"hi\",\"status\":\"REJECTED\"},"
                        + "{\"id\":\"2\",\"name\":\"shipment_booked\",\"language\":\"en_US\",\"status\":\"APPROVED\","
                        + "\"category\":\"UTILITY\"}]}", MediaType.APPLICATION_JSON));

        var status = provider.fetchTemplateStatus("shipment_booked", "en_US", CREDS);

        assertThat(status.metaTemplateId()).isEqualTo("2");
        assertThat(status.status()).isEqualTo("APPROVED");
        assertThat(status.rejectedReason()).isNull();
    }

    @Test
    void statusReportsARejectionReasonButNotMetasNONE() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://graph.facebook.com/v19.0/WABA9/message_templates?")))
                .andRespond(withSuccess("{\"data\":[{\"id\":\"2\",\"name\":\"t\",\"language\":\"en_US\","
                        + "\"status\":\"REJECTED\",\"rejected_reason\":\"INVALID_FORMAT\"}]}", MediaType.APPLICATION_JSON));
        assertThat(provider.fetchTemplateStatus("t", "en_US", CREDS).rejectedReason()).isEqualTo("INVALID_FORMAT");

        server.reset();
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://graph.facebook.com/v19.0/WABA9/message_templates?")))
                .andRespond(withSuccess("{\"data\":[{\"id\":\"2\",\"name\":\"t\",\"language\":\"en_US\","
                        + "\"status\":\"APPROVED\",\"rejected_reason\":\"NONE\"}]}", MediaType.APPLICATION_JSON));
        assertThat(provider.fetchTemplateStatus("t", "en_US", CREDS).rejectedReason()).isNull();
    }

    @Test
    void statusIsNotFoundWhenMetaHasNoSuchTemplate() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://graph.facebook.com/v19.0/WABA9/message_templates?")))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        assertThat(provider.fetchTemplateStatus("ghost", "en_US", CREDS).status()).isEqualTo("NOT_FOUND");
    }

    @Test
    void readsAJsonBodyEvenWhenMetaLabelsItTextJavascript() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://graph.facebook.com/v19.0/WABA9/message_templates?")))
                .andRespond(withSuccess("{\"data\":[{\"id\":\"2\",\"name\":\"t\",\"language\":\"en_US\","
                        + "\"status\":\"APPROVED\"}]}", MediaType.valueOf("text/javascript;charset=UTF-8")));

        assertThat(provider.fetchTemplateStatus("t", "en_US", CREDS).status()).isEqualTo("APPROVED");
    }

    @Test
    void aNonJsonAnswerBecomesAClearError() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://graph.facebook.com/v19.0/WABA9/message_templates?")))
                .andRespond(withSuccess("<html>gateway</html>", MediaType.TEXT_HTML));

        assertThatThrownBy(() -> provider.fetchTemplateStatus("t", "en_US", CREDS))
                .isInstanceOf(ProviderSendException.class).hasMessageContaining("not JSON");
    }

    @Test
    void submitCarriesHeaderFooterAndButtonsInMetasShape() {
        server.expect(requestTo("https://graph.facebook.com/v19.0/WABA9/message_templates"))
                .andExpect(jsonPath("$.components[0].type").value("HEADER"))
                .andExpect(jsonPath("$.components[0].format").value("TEXT"))
                .andExpect(jsonPath("$.components[0].text").value("Shipment {{1}}"))
                .andExpect(jsonPath("$.components[0].example.header_text[0]").value("SHP-1"))
                .andExpect(jsonPath("$.components[1].type").value("BODY"))
                .andExpect(jsonPath("$.components[2].type").value("FOOTER"))
                .andExpect(jsonPath("$.components[2].text").value("Thank you!"))
                .andExpect(jsonPath("$.components[3].type").value("BUTTONS"))
                .andExpect(jsonPath("$.components[3].buttons[0].type").value("URL"))
                .andExpect(jsonPath("$.components[3].buttons[0].url").value("https://x.in/t/{{1}}"))
                .andExpect(jsonPath("$.components[3].buttons[0].example[0]").value("https://x.in/t/AWB1"))
                .andExpect(jsonPath("$.components[3].buttons[1].type").value("PHONE_NUMBER"))
                .andExpect(jsonPath("$.components[3].buttons[1].phone_number").value("+919123456780"))
                .andExpect(jsonPath("$.components[3].buttons[1].example").doesNotExist())
                .andRespond(withSuccess("{\"id\":\"9\",\"status\":\"PENDING\"}", MediaType.APPLICATION_JSON));

        provider.submitTemplate(new WhatsAppProvider.TemplateSubmission("t", "hi", "UTILITY", "Body {{1}}",
                List.of("x"), "Shipment {{1}}", "SHP-1", "Thank you!", List.of(
                new WhatsAppProvider.SubmissionButton("URL", "Track", "https://x.in/t/{{1}}", "https://x.in/t/AWB1", null),
                new WhatsAppProvider.SubmissionButton("PHONE_NUMBER", "Call us", null, null, "+919123456780"))), CREDS);

        server.verify();
    }

    @Test
    void submitWithoutExtrasSendsOnlyTheBodyComponent() {
        server.expect(requestTo("https://graph.facebook.com/v19.0/WABA9/message_templates"))
                .andExpect(jsonPath("$.components.length()").value(1))
                .andExpect(jsonPath("$.components[0].type").value("BODY"))
                .andRespond(withSuccess("{\"id\":\"9\",\"status\":\"PENDING\"}", MediaType.APPLICATION_JSON));

        provider.submitTemplate(new WhatsAppProvider.TemplateSubmission("t", "en_US", "UTILITY", "Just text", List.of()), CREDS);

        server.verify();
    }

    @Test
    void sendUsesTheTemplatesLanguageAndAddsHeaderAndButtonParameters() {
        server.expect(requestTo("https://graph.facebook.com/v19.0/111/messages"))
                .andExpect(jsonPath("$.to").value("919123456780"))
                .andExpect(jsonPath("$.template.name").value("shipment_booked"))
                .andExpect(jsonPath("$.template.language.code").value("mr"))
                .andExpect(jsonPath("$.template.components[0].type").value("header"))
                .andExpect(jsonPath("$.template.components[0].parameters[0].text").value("SHP-1"))
                .andExpect(jsonPath("$.template.components[1].type").value("body"))
                .andExpect(jsonPath("$.template.components[1].parameters[0].text").value("Asha"))
                .andExpect(jsonPath("$.template.components[2].type").value("button"))
                .andExpect(jsonPath("$.template.components[2].sub_type").value("url"))
                .andExpect(jsonPath("$.template.components[2].index").value("1"))
                .andExpect(jsonPath("$.template.components[2].parameters[0].text").value("AWB1"))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.1\"}]}", MediaType.APPLICATION_JSON));

        var result = provider.send(new WhatsAppProvider.WhatsAppMessage("9123456780", "shipment_booked",
                List.of("Asha"), "mr", "SHP-1", List.of(new WhatsAppProvider.ButtonParameter(1, "AWB1"))), CREDS);

        assertThat(result.providerMessageId()).isEqualTo("wamid.1");
        server.verify();
    }

    @Test
    void sendFallsBackToTheDeploymentLanguageWhenTheMessageHasNone() {
        server.expect(requestTo("https://graph.facebook.com/v19.0/111/messages"))
                .andExpect(jsonPath("$.template.language.code").value("en_US"))
                .andExpect(jsonPath("$.template.components.length()").value(1))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.2\"}]}", MediaType.APPLICATION_JSON));

        provider.send(new WhatsAppProvider.WhatsAppMessage("9123456780", "hello_world", List.of()), CREDS);

        server.verify();
    }

    // ------------------------------------------------------------------ pricing analytics

    @Test
    void pricingRequestAsksMetaForCostAndVolumeByCategoryAndType() {
        server.expect(requestTo(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.startsWith("https://graph.facebook.com/v19.0/WABA9?"),
                        org.hamcrest.Matchers.containsString("currency"),
                        org.hamcrest.Matchers.containsString("pricing_analytics.start(1000).end(2000)"),
                        org.hamcrest.Matchers.containsString("granularity(DAILY)"),
                        org.hamcrest.Matchers.containsString("metric_types(COST,VOLUME)"),
                        org.hamcrest.Matchers.containsString("dimensions(PRICING_CATEGORY,PRICING_TYPE)"))))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer tok"))
                .andRespond(withSuccess("{\"currency\":\"INR\",\"pricing_analytics\":{\"data\":[{\"data_points\":["
                        + "{\"start\":1,\"end\":2,\"pricing_category\":\"UTILITY\",\"pricing_type\":\"REGULAR\",\"volume\":10,\"cost\":1.15},"
                        + "{\"start\":1,\"end\":2,\"pricing_category\":\"UTILITY\",\"pricing_type\":\"FREE_CUSTOMER_SERVICE\",\"volume\":3,\"cost\":0}"
                        + "]}]}}", MediaType.valueOf("text/javascript;charset=UTF-8")));

        var report = provider.fetchPricing(CREDS, Instant.ofEpochSecond(1000), Instant.ofEpochSecond(2000));

        assertThat(report.currency()).isEqualTo("INR");
        assertThat(report.points()).hasSize(2);
        assertThat(report.points().get(0).category()).isEqualTo("UTILITY");
        assertThat(report.points().get(0).pricingType()).isEqualTo("REGULAR");
        assertThat(report.points().get(0).volume()).isEqualTo(10);
        assertThat(report.points().get(0).cost()).isEqualByComparingTo("1.15");
        assertThat(report.points().get(1).cost()).isEqualByComparingTo("0");
        server.verify();
    }

    @Test
    void anAccountWithNoBillingYetReturnsAnEmptyReportNotAnError() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://graph.facebook.com/v19.0/WABA9?")))
                .andRespond(withSuccess("{\"currency\":\"INR\",\"id\":\"WABA9\"}", MediaType.APPLICATION_JSON));

        var report = provider.fetchPricing(CREDS, Instant.ofEpochSecond(1), Instant.ofEpochSecond(2));

        assertThat(report.points()).isEmpty();
        assertThat(report.currency()).isEqualTo("INR");
    }

    @Test
    void aMissingPermissionSurfacesMetasOwnWords() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://graph.facebook.com/v19.0/WABA9?")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"(#200) Requires whatsapp_business_management permission\",\"code\":200}}"));

        assertThatThrownBy(() -> provider.fetchPricing(CREDS, Instant.ofEpochSecond(1), Instant.ofEpochSecond(2)))
                .isInstanceOf(ProviderSendException.class)
                .hasMessageContaining("whatsapp_business_management").hasMessageContaining("code 200")
                .hasMessageContaining("Channel Settings");
    }

    @Test
    void pricingNeedsABusinessAccountIdAndToken() {
        assertThatThrownBy(() -> provider.fetchPricing(new WhatsAppProvider.WhatsAppCredentials("111", null, "tok"),
                Instant.ofEpochSecond(1), Instant.ofEpochSecond(2)))
                .isInstanceOf(ProviderSendException.class).hasMessageContaining("Business Account ID");
    }

    @Test
    void anExpiredTokenIsExplainedInPlainWordsWithTheFix() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://graph.facebook.com/v19.0/WABA9?")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"Error validating access token: Session has expired\","
                                + "\"type\":\"OAuthException\",\"code\":190,\"error_subcode\":463}}"));

        assertThatThrownBy(() -> provider.fetchPricing(CREDS, Instant.ofEpochSecond(1), Instant.ofEpochSecond(2)))
                .isInstanceOf(ProviderSendException.class)
                .hasMessageContaining("expired").hasMessageContaining("System User").hasMessageContaining("code 190");
    }

    @Test
    void otherMetaErrorsKeepMetasOwnWording() {
        assertThat(MetaWhatsAppProvider.hintFor("100", "")).isEmpty();
        assertThat(MetaWhatsAppProvider.hintFor("190", "")).contains("expired");
        assertThat(MetaWhatsAppProvider.hintFor("10", "")).contains("permission");
    }
}
