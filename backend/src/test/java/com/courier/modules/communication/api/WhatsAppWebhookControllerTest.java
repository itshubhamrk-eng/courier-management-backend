package com.courier.modules.communication.api;

import com.courier.modules.communication.application.WhatsAppReceiptService;
import com.courier.modules.communication.application.WhatsAppReceiptService.Receipt;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppWebhookControllerTest {

    private static final String SECRET = "app-secret-123";
    private static final String TOKEN = "verify-me";

    @Mock WhatsAppReceiptService receiptService;

    private WhatsAppWebhookController controller;

    @BeforeEach
    void setUp() {
        controller = new WhatsAppWebhookController(receiptService, new ObjectMapper());
        ReflectionTestUtils.setField(controller, "verifyToken", TOKEN);
        ReflectionTestUtils.setField(controller, "appSecret", SECRET);
    }

    private static String sign(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private static final String PAYLOAD = """
            {"object":"whatsapp_business_account","entry":[{"id":"WABA","changes":[{"field":"messages","value":{
              "messaging_product":"whatsapp",
              "statuses":[
                {"id":"wamid.A","status":"delivered","timestamp":"1760436000","recipient_id":"919123456780"},
                {"id":"wamid.B","status":"failed","timestamp":"1760436060","errors":[
                    {"code":131026,"title":"Message undeliverable","message":"Receiver is incapable of receiving this message"}]},
                {"id":"wamid.C","status":"read","timestamp":"not-a-number"}
              ]}}]}]}
            """;

    // ---------------------------------------------------------------- GET handshake

    @Test
    void handshakeEchoesTheChallengeOnlyForTheRightToken() {
        var ok = controller.verify("subscribe", TOKEN, "1158201444");
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody()).isEqualTo("1158201444");

        assertThat(controller.verify("subscribe", "wrong", "x").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(controller.verify("unsubscribe", TOKEN, "x").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(controller.verify("subscribe", null, "x").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(controller.verify("subscribe", TOKEN, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void handshakeIsUnavailableUntilAVerifyTokenIsConfigured() {
        ReflectionTestUtils.setField(controller, "verifyToken", "");
        assertThat(controller.verify("subscribe", "", "x").getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    // ---------------------------------------------------------------- POST receipts

    @Test
    void aValidlySignedPayloadIsParsedAndApplied() throws Exception {
        var response = controller.receive(PAYLOAD.getBytes(StandardCharsets.UTF_8), sign(PAYLOAD, SECRET));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Receipt>> captor = ArgumentCaptor.forClass(List.class);
        verify(receiptService).apply(captor.capture());
        List<Receipt> receipts = captor.getValue();
        assertThat(receipts).hasSize(3);
        assertThat(receipts.get(0)).isEqualTo(new Receipt("wamid.A", "delivered", Instant.ofEpochSecond(1760436000L), null));
        assertThat(receipts.get(1).status()).isEqualTo("failed");
        assertThat(receipts.get(1).error()).contains("Message undeliverable").contains("Receiver is incapable").contains("131026");
        assertThat(receipts.get(2).at()).isNull(); // an unparseable timestamp becomes "now" downstream
    }

    @Test
    void theSignatureHeaderMayOmitThePrefixButNotBeWrong() throws Exception {
        String hex = sign(PAYLOAD, SECRET).substring("sha256=".length());
        assertThat(controller.receive(PAYLOAD.getBytes(StandardCharsets.UTF_8), hex).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.receive(PAYLOAD.getBytes(StandardCharsets.UTF_8), sign(PAYLOAD, "other-secret")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aTamperedBodyIsRefusedAndNothingIsRecorded() throws Exception {
        String signature = sign(PAYLOAD, SECRET);
        byte[] tampered = PAYLOAD.replace("delivered", "read").getBytes(StandardCharsets.UTF_8);

        assertThat(controller.receive(tampered, signature).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(receiptService, never()).apply(any());
    }

    @Test
    void missingSignatureIsABadRequestAndNoConfiguredSecretIsUnavailable() throws Exception {
        assertThat(controller.receive(PAYLOAD.getBytes(StandardCharsets.UTF_8), null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.receive(PAYLOAD.getBytes(StandardCharsets.UTF_8), "  ").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        ReflectionTestUtils.setField(controller, "appSecret", "");
        assertThat(controller.receive(PAYLOAD.getBytes(StandardCharsets.UTF_8), sign(PAYLOAD, SECRET)).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verify(receiptService, never()).apply(any());
    }

    @Test
    void inboundCustomerMessagesAndOtherFieldsAreAcknowledgedButNotProcessed() throws Exception {
        String inbound = "{\"entry\":[{\"changes\":[{\"field\":\"messages\",\"value\":{\"messages\":[{\"from\":\"9197\",\"text\":{\"body\":\"hi\"}}]}}]}]}";
        assertThat(controller.receive(inbound.getBytes(StandardCharsets.UTF_8), sign(inbound, SECRET)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        verify(receiptService, never()).apply(any());
    }

    @Test
    void notJsonDespiteAValidSignatureIsABadRequest() throws Exception {
        String junk = "not json at all";
        assertThat(controller.receive(junk.getBytes(StandardCharsets.UTF_8), sign(junk, SECRET)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aTransientFailureAnswers5xxSoMetaRetriesInsteadOfDroppingTheReceipt() throws Exception {
        when(receiptService.apply(any())).thenThrow(new IllegalStateException("db down"));

        assertThat(controller.receive(PAYLOAD.getBytes(StandardCharsets.UTF_8), sign(PAYLOAD, SECRET)).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
