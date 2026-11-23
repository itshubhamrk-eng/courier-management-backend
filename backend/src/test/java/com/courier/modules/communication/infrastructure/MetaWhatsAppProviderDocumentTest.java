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

import java.nio.charset.StandardCharsets;
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

class MetaWhatsAppProviderDocumentTest {

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
    void uploadPostsMultipartWithTheThreeFieldsAndReturnsTheMediaId() {
        server.expect(requestTo("https://graph.facebook.com/v19.0/111/media"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer tok"))
                .andExpect(header("Content-Type", org.hamcrest.Matchers.startsWith("multipart/form-data")))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("name=\"messaging_product\""),
                        org.hamcrest.Matchers.containsString("whatsapp"),
                        org.hamcrest.Matchers.containsString("name=\"type\""),
                        org.hamcrest.Matchers.containsString("name=\"file\"; filename=\"statement.pdf\""),
                        org.hamcrest.Matchers.containsString("Content-Type: application/pdf"),
                        org.hamcrest.Matchers.containsString("%PDF-fake"))))
                .andRespond(withSuccess("{\"id\":\"123\"}", MediaType.valueOf("text/javascript")));

        String id = provider.uploadDocument("%PDF-fake".getBytes(StandardCharsets.UTF_8), "statement.pdf", CREDS);

        assertThat(id).isEqualTo("123");
        server.verify();
    }

    @Test
    void uploadMapsAMetaErrorToProviderSendExceptionWithMetasMessage() {
        server.expect(requestTo("https://graph.facebook.com/v19.0/111/media"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"Unsupported file type\",\"code\":100}}"));

        assertThatThrownBy(() -> provider.uploadDocument(new byte[]{1}, "a.pdf", CREDS))
                .isInstanceOf(ProviderSendException.class)
                .hasMessageContaining("Unsupported file type");
    }

    @Test
    void uploadWithoutCredentialsFails() {
        var none = new WhatsAppProvider.WhatsAppCredentials(null, null, null);
        assertThatThrownBy(() -> provider.uploadDocument(new byte[]{1}, "a.pdf", none))
                .isInstanceOf(ProviderSendException.class).hasMessageContaining("not configured");
    }

    @Test
    void aMessageWithADocumentGetsAFirstHeaderComponentOfTypeDocument() {
        server.expect(requestTo("https://graph.facebook.com/v19.0/111/messages"))
                .andExpect(jsonPath("$.template.components[0].type").value("header"))
                .andExpect(jsonPath("$.template.components[0].parameters[0].type").value("document"))
                .andExpect(jsonPath("$.template.components[0].parameters[0].document.id").value("123"))
                .andExpect(jsonPath("$.template.components[0].parameters[0].document.filename").value("s.pdf"))
                .andExpect(jsonPath("$.template.components[1].type").value("body"))
                .andExpect(jsonPath("$.template.components[1].parameters[0].text").value("Pune"))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.9\"}]}", MediaType.APPLICATION_JSON));

        // a text header parameter is set too: the document wins
        var message = new WhatsAppProvider.WhatsAppMessage("9123456780", "branch_monthly_report",
                List.of("Pune"), "en_US", "ignored", List.of()).withDocument("123", "s.pdf");
        var result = provider.send(message, CREDS);

        assertThat(result.providerMessageId()).isEqualTo("wamid.9");
        server.verify();
    }

    @Test
    void withDocumentReturnsACopyAndTheShortConstructorsLeaveItNull() {
        var plain = new WhatsAppProvider.WhatsAppMessage("1", "t", List.of());
        assertThat(plain.documentMediaId()).isNull();
        assertThat(plain.documentFileName()).isNull();
        var copy = plain.withDocument("m", "f.pdf");
        assertThat(copy.documentMediaId()).isEqualTo("m");
        assertThat(copy.documentFileName()).isEqualTo("f.pdf");
        assertThat(plain.documentMediaId()).isNull();
    }

    @Test
    void logOnlyUploadReturnsASyntheticMediaId() {
        assertThat(new LogOnlyWhatsAppProvider().uploadDocument(new byte[]{1}, "a.pdf", CREDS))
                .startsWith("log-media-");
    }
}
