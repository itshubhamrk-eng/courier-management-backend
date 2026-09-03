package com.courier.modules.pod.application;

import com.courier.modules.pod.application.provider.PodAnalysisRequest;
import com.courier.modules.pod.application.provider.PodAnalysisResult;
import com.courier.modules.pod.application.provider.PodProviderUnavailableException;
import com.courier.modules.pod.application.provider.PodVerificationProvider;
import com.courier.modules.pod.domain.PodVerification;
import com.courier.modules.pod.domain.PodVerificationRepository;
import com.courier.modules.pod.domain.PodVerificationStatus;
import com.courier.modules.shipment.application.ShipmentService;
import com.courier.modules.shipment.domain.Shipment;
import com.courier.modules.shipment.domain.ShipmentAsset;
import com.courier.modules.shipment.domain.ShipmentStatus;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.AuthenticatedUser;
import com.courier.shared.security.Roles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PodVerificationServiceImplTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID CALLER = UUID.randomUUID();
    private static final UUID SHIPMENT_ID = UUID.randomUUID();

    @Mock private PodVerificationRepository podVerificationRepository;
    @Mock private ShipmentService shipmentService;
    @Mock private PodVerificationProvider provider;
    @Mock private AuditService auditService;

    private PodVerificationProperties properties;
    private PodVerificationServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new PodVerificationProperties();
        service = new PodVerificationServiceImpl(podVerificationRepository, shipmentService, provider,
                properties, auditService);
        CompanyContext.setCompanyId(COMPANY);
        signedIn(Roles.BRANCH_MANAGER);

        Shipment shipment = outForDeliveryShipment();
        when(shipmentService.getById(SHIPMENT_ID)).thenReturn(shipment);
        when(shipmentService.uploadPodFile(eq(SHIPMENT_ID), any())).thenReturn("https://store/pod/photo.jpg");
        when(shipmentService.attachPodAsset(eq(SHIPMENT_ID), anyString(), anyString()))
                .thenAnswer(i -> asset(i.getArgument(1)));
        when(podVerificationRepository.findDuplicatesWithinCompany(eq(COMPANY), anyString(), eq(SHIPMENT_ID)))
                .thenReturn(List.of());
        when(podVerificationRepository.save(any(PodVerification.class))).thenAnswer(i -> i.getArgument(0));
        when(provider.providerName()).thenReturn("heuristic-local");
        when(provider.modelName()).thenReturn("structural-v1");
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------ verify

    @Test
    @DisplayName("a high-confidence result resolves to PASS")
    void highScorePasses() {
        when(provider.analyze(any())).thenReturn(result(92, false, false));

        PodVerification saved = service.verify(SHIPMENT_ID, command("Ramesh"));

        assertThat(saved.getVerificationStatus()).isEqualTo(PodVerificationStatus.PASS);
        assertThat(saved.getVerificationScore()).isEqualTo(92);
        verify(shipmentService).uploadPodFile(eq(SHIPMENT_ID), any());
        verify(shipmentService).attachPodAsset(eq(SHIPMENT_ID), eq("PHOTO"), anyString());
    }

    @Test
    @DisplayName("a mid-confidence result resolves to REVIEW")
    void midScoreReview() {
        when(provider.analyze(any())).thenReturn(result(70, false, false));

        PodVerification saved = service.verify(SHIPMENT_ID, command("Ramesh"));

        assertThat(saved.getVerificationStatus()).isEqualTo(PodVerificationStatus.REVIEW);
    }

    @Test
    @DisplayName("a low-confidence result (e.g. blurred/dark image) resolves to FAIL")
    void lowScoreFails() {
        when(provider.analyze(any())).thenReturn(result(30, false, false));

        PodVerification saved = service.verify(SHIPMENT_ID, command("Ramesh"));

        assertThat(saved.getVerificationStatus()).isEqualTo(PodVerificationStatus.FAIL);
    }

    @Test
    @DisplayName("missing signature is reflected in the persisted result, not itself a refusal")
    void missingSignatureRecorded() {
        when(provider.analyze(any())).thenReturn(new PodAnalysisResult(75,
                List.of("No signature detected on this delivery capture."), false, "GOOD",
                "Ramesh", "AWB1", null, false, false));

        PodVerification saved = service.verify(SHIPMENT_ID, command("Ramesh"));

        assertThat(saved.isSignatureDetected()).isFalse();
        assertThat(saved.reasons()).contains("No signature detected on this delivery capture.");
    }

    @Test
    @DisplayName("a duplicate-hash match forces REVIEW even at a high score")
    void duplicateForcesReview() {
        when(podVerificationRepository.findDuplicatesWithinCompany(eq(COMPANY), anyString(), eq(SHIPMENT_ID)))
                .thenReturn(List.of(mockExisting()));
        when(provider.analyze(any())).thenReturn(new PodAnalysisResult(95,
                List.of("This POD photo matches one already used on a different shipment — "
                        + "possible duplicate submission."),
                true, "GOOD", "Ramesh", "AWB1", null, false, true));

        PodVerification saved = service.verify(SHIPMENT_ID, command("Ramesh"));

        assertThat(saved.getVerificationStatus()).isEqualTo(PodVerificationStatus.REVIEW);

        ArgumentCaptor<PodAnalysisRequest> captor = ArgumentCaptor.forClass(PodAnalysisRequest.class);
        verify(provider).analyze(captor.capture());
        assertThat(captor.getValue().duplicateSuspectedByHash()).isTrue();
    }

    @Test
    @DisplayName("wrong AWB is passed through to the provider for cross-checking")
    void wrongAwbPassedToProvider() {
        when(provider.analyze(any())).thenReturn(result(40, false, false));

        service.verify(SHIPMENT_ID, new PodVerificationService.VerifyPodCommand(
                photoBytes(), "photo.jpg", "image/jpeg", null, null, null,
                "Ramesh", "WRONG-AWB", null, null, null));

        ArgumentCaptor<PodAnalysisRequest> captor = ArgumentCaptor.forClass(PodAnalysisRequest.class);
        verify(provider).analyze(captor.capture());
        assertThat(captor.getValue().claimedAwb()).isEqualTo("WRONG-AWB");
        assertThat(captor.getValue().shipmentActualAwb()).isEqualTo("TRK-000001");
    }

    @Test
    @DisplayName("a live-scanned QR value is passed through to the provider for cross-checking")
    void qrScanValuePassedToProvider() {
        when(provider.analyze(any())).thenReturn(result(95, true, false));

        service.verify(SHIPMENT_ID, new PodVerificationService.VerifyPodCommand(
                photoBytes(), "photo.jpg", "image/jpeg", null, null, null,
                "Ramesh", "TRK-000001", "SHP-000001", null, "TRK-000001"));

        ArgumentCaptor<PodAnalysisRequest> captor = ArgumentCaptor.forClass(PodAnalysisRequest.class);
        verify(provider).analyze(captor.capture());
        assertThat(captor.getValue().qrScanValue()).isEqualTo("TRK-000001");
    }

    @Test
    @DisplayName("an unavailable AI provider routes to REVIEW, never a silent PASS")
    void providerUnavailableRoutesToReview() {
        when(provider.analyze(any())).thenThrow(new PodProviderUnavailableException("down"));

        PodVerification saved = service.verify(SHIPMENT_ID, command("Ramesh"));

        assertThat(saved.getVerificationStatus()).isEqualTo(PodVerificationStatus.REVIEW);
        assertThat(saved.getAiProvider()).isEqualTo("unavailable");
    }

    @Test
    @DisplayName("verification is refused off an OUT_FOR_DELIVERY status")
    void wrongStatusRefused() {
        Shipment booked = outForDeliveryShipment();
        booked.setStatus(ShipmentStatus.BOOKED);
        when(shipmentService.getById(SHIPMENT_ID)).thenReturn(booked);

        assertThatThrownBy(() -> service.verify(SHIPMENT_ID, command("Ramesh")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("OUT_FOR_DELIVERY");
        verify(provider, never()).analyze(any());
    }

    @Test
    @DisplayName("a missing photo is refused before any AI call")
    void missingPhotoRefused() {
        assertThatThrownBy(() -> service.verify(SHIPMENT_ID, new PodVerificationService.VerifyPodCommand(
                null, null, null, null, null, null, "Ramesh", null, null, null, null)))
                .isInstanceOf(BusinessRuleException.class);
        verify(provider, never()).analyze(any());
    }

    // ------------------------------------------------------------------ getLatest

    @Test
    @DisplayName("getLatest 404s when no verification has been run")
    void getLatestNotFound() {
        when(podVerificationRepository.findAllByShipmentIdWithinCompany(SHIPMENT_ID, COMPANY))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.getLatest(SHIPMENT_ID)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------------ review

    @Test
    @DisplayName("approving a REVIEW result moves it to PASS and stamps the reviewer")
    void approveMovesToPass() {
        PodVerification existing = reviewVerification();
        when(podVerificationRepository.findLatestByShipmentIdWithinCompany(SHIPMENT_ID, COMPANY))
                .thenReturn(Optional.of(existing));

        PodVerification saved = service.review(SHIPMENT_ID,
                new PodVerificationService.ReviewPodCommand(true, "Looks fine"));

        assertThat(saved.getVerificationStatus()).isEqualTo(PodVerificationStatus.PASS);
        assertThat(saved.getReviewedBy()).isEqualTo(CALLER);
        assertThat(saved.getReviewedAt()).isNotNull();
        assertThat(saved.getReviewRemarks()).isEqualTo("Looks fine");
    }

    @Test
    @DisplayName("rejecting a REVIEW result moves it to FAIL")
    void rejectMovesToFail() {
        PodVerification existing = reviewVerification();
        when(podVerificationRepository.findLatestByShipmentIdWithinCompany(SHIPMENT_ID, COMPANY))
                .thenReturn(Optional.of(existing));

        PodVerification saved = service.review(SHIPMENT_ID,
                new PodVerificationService.ReviewPodCommand(false, "Unclear photo"));

        assertThat(saved.getVerificationStatus()).isEqualTo(PodVerificationStatus.FAIL);
    }

    @Test
    @DisplayName("reviewing an already-decided verification is refused")
    void reviewIllegalWhenNotPending() {
        PodVerification existing = reviewVerification();
        existing.setVerificationStatus(PodVerificationStatus.PASS);
        when(podVerificationRepository.findLatestByShipmentIdWithinCompany(SHIPMENT_ID, COMPANY))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.review(SHIPMENT_ID,
                new PodVerificationService.ReviewPodCommand(true, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ------------------------------------------------------------------ helpers

    private void signedIn(String role) {
        AuthenticatedUser principal = new AuthenticatedUser(CALLER, COMPANY, "user@test.local", Set.of(role), "jti");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }

    private static Shipment outForDeliveryShipment() {
        Shipment shipment = new Shipment();
        shipment.setId(SHIPMENT_ID);
        shipment.setShipmentNumber("SHP-000001");
        shipment.setTrackingNumber("TRK-000001");
        shipment.setStatus(ShipmentStatus.OUT_FOR_DELIVERY);
        return shipment;
    }

    private static ShipmentAsset asset(String kind) {
        ShipmentAsset asset = ShipmentAsset.builder().shipmentId(SHIPMENT_ID)
                .assetType(com.courier.modules.shipment.domain.ShipmentAssetType.POD)
                .kind(kind).assetUrl("https://store/pod/x.jpg").build();
        asset.setId(UUID.randomUUID());
        return asset;
    }

    private static byte[] photoBytes() {
        return new byte[]{1, 2, 3, 4};
    }

    private static PodVerificationService.VerifyPodCommand command(String receiverName) {
        return new PodVerificationService.VerifyPodCommand(
                photoBytes(), "photo.jpg", "image/jpeg", null, null, null,
                receiverName, "TRK-000001", "SHP-000001", null, null);
    }

    private static PodAnalysisResult result(int score, boolean signature, boolean mustReview) {
        return new PodAnalysisResult(score, List.of("scored"), signature, "GOOD",
                "Ramesh", "TRK-000001", null, false, mustReview);
    }

    private static PodVerification mockExisting() {
        PodVerification v = PodVerification.builder().shipmentId(UUID.randomUUID())
                .verificationStatus(PodVerificationStatus.PASS).verificationScore(90)
                .podHash("abc").aiProvider("heuristic-local").aiModel("structural-v1").build();
        v.setId(UUID.randomUUID());
        return v;
    }

    private static PodVerification reviewVerification() {
        PodVerification v = PodVerification.builder().shipmentId(SHIPMENT_ID)
                .verificationStatus(PodVerificationStatus.REVIEW).verificationScore(70)
                .aiProvider("heuristic-local").aiModel("structural-v1").build();
        v.setId(UUID.randomUUID());
        return v;
    }
}
