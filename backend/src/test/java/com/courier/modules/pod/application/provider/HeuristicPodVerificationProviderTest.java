package com.courier.modules.pod.application.provider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicPodVerificationProviderTest {

    private final HeuristicPodVerificationProvider provider = new HeuristicPodVerificationProvider();

    @Test
    @DisplayName("a bright, sharp, adequately-sized, checkered photo with a signature scores well")
    void goodPhotoScoresWell() {
        PodAnalysisResult result = provider.analyze(request(checkeredImage(600, 600, 200), signature(), "Ramesh",
                "TRK-1", "TRK-1"));

        assertThat(result.score()).isGreaterThanOrEqualTo(70);
        assertThat(result.signatureDetected()).isTrue();
        assertThat(result.imageQuality()).isIn("GOOD", "FAIR");
    }

    @Test
    @DisplayName("a solid dark image is flagged as too dark")
    void darkImageFlagged() {
        PodAnalysisResult result = provider.analyze(request(solidImage(600, 600, Color.BLACK), signature(),
                "Ramesh", "TRK-1", "TRK-1"));

        assertThat(result.reasons()).anyMatch(r -> r.contains("too dark"));
        assertThat(result.score()).isLessThan(100);
    }

    @Test
    @DisplayName("a flat solid-color image is flagged as blurry (no gradient)")
    void flatImageFlaggedBlurry() {
        PodAnalysisResult result = provider.analyze(request(solidImage(600, 600, Color.GRAY), signature(),
                "Ramesh", "TRK-1", "TRK-1"));

        assertThat(result.reasons()).anyMatch(r -> r.contains("blurry"));
    }

    @Test
    @DisplayName("a tiny image is flagged as low resolution")
    void tinyImageFlaggedLowRes() {
        PodAnalysisResult result = provider.analyze(request(checkeredImage(50, 50, 10), signature(),
                "Ramesh", "TRK-1", "TRK-1"));

        assertThat(result.reasons()).anyMatch(r -> r.contains("resolution"));
    }

    @Test
    @DisplayName("unreadable bytes are scored zero and flagged unreadable")
    void unreadableBytesScoreZero() {
        PodAnalysisResult result = provider.analyze(new PodAnalysisRequest(
                new byte[]{1, 2, 3}, "image/jpeg", signature(), "Ramesh", "TRK-1", null,
                "TRK-1", "SHP-1", Instant.now(), false, null));

        assertThat(result.score()).isEqualTo(0);
        assertThat(result.reasons()).anyMatch(r -> r.contains("could not be read"));
    }

    @Test
    @DisplayName("no signature bytes -> signatureDetected is false and scored down")
    void missingSignatureDetected() {
        PodAnalysisResult result = provider.analyze(request(checkeredImage(600, 600, 200), null,
                "Ramesh", "TRK-1", "TRK-1"));

        assertThat(result.signatureDetected()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("No signature"));
    }

    @Test
    @DisplayName("blank receiver name is scored down")
    void blankReceiverNameScoredDown() {
        PodAnalysisResult result = provider.analyze(request(checkeredImage(600, 600, 200), signature(),
                "  ", "TRK-1", "TRK-1"));

        assertThat(result.reasons()).anyMatch(r -> r.contains("Receiver name was not provided"));
    }

    @Test
    @DisplayName("a claimed AWB that disagrees with the shipment's real AWB is a hard fail — score zeroed")
    void awbMismatchFlagged() {
        PodAnalysisResult result = provider.analyze(request(checkeredImage(600, 600, 200), signature(),
                "Ramesh", "WRONG-AWB", "TRK-1"));

        assertThat(result.reasons()).anyMatch(r -> r.contains("does not match"));
        assertThat(result.score()).isZero();
    }

    @Test
    @DisplayName("a duplicate-hash signal forces mustReviewRegardlessOfScore even at a good score")
    void duplicateForcesReviewFlag() {
        PodAnalysisResult result = provider.analyze(new PodAnalysisRequest(
                checkeredImage(600, 600, 200), "image/jpeg", signature(), "Ramesh", "TRK-1", "TRK-1",
                "TRK-1", "SHP-1", Instant.now(), true, null));

        assertThat(result.mustReviewRegardlessOfScore()).isTrue();
        assertThat(result.reasons()).anyMatch(r -> r.contains("duplicate"));
    }

    @Test
    @DisplayName("a scanned QR that agrees with the shipment's own AWB is not flagged")
    void qrMatchNotFlagged() {
        PodAnalysisResult result = provider.analyze(new PodAnalysisRequest(
                checkeredImage(600, 600, 200), "image/jpeg", signature(), "Ramesh", null, null,
                "TRK-1", "SHP-1", Instant.now(), false, "TRK-1"));

        assertThat(result.reasons()).noneMatch(r -> r.contains("QR"));
        assertThat(result.score()).isNotZero();
    }

    @Test
    @DisplayName("a scanned QR for a different parcel is a hard fail — score zeroed")
    void qrMismatchFlagged() {
        PodAnalysisResult result = provider.analyze(new PodAnalysisRequest(
                checkeredImage(600, 600, 200), "image/jpeg", signature(), "Ramesh", null, null,
                "TRK-1", "SHP-1", Instant.now(), false, "TRK-OTHER-PARCEL"));

        assertThat(result.reasons()).anyMatch(r -> r.contains("QR") && r.contains("does not match"));
        assertThat(result.score()).isZero();
    }

    // ------------------------------------------------------------------ helpers

    private static PodAnalysisRequest request(byte[] photo, byte[] signature, String receiverName,
                                               String claimedAwb, String actualAwb) {
        return new PodAnalysisRequest(photo, "image/jpeg", signature, receiverName, claimedAwb, null,
                actualAwb, "SHP-1", Instant.now(), false, null);
    }

    private static byte[] signature() {
        return new byte[]{9, 9, 9};
    }

    private static byte[] solidImage(int w, int h, Color color) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return toBytes(image);
    }

    /** A checkerboard is bright, high-contrast and non-uniform — scores well on darkness and
     *  blur without needing a real photograph. */
    private static byte[] checkeredImage(int w, int h, int cell) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        for (int y = 0; y < h; y += cell) {
            for (int x = 0; x < w; x += cell) {
                boolean light = ((x / cell) + (y / cell)) % 2 == 0;
                g.setColor(light ? Color.WHITE : new Color(60, 60, 60));
                g.fillRect(x, y, cell, cell);
            }
        }
        g.dispose();
        return toBytes(image);
    }

    private static byte[] toBytes(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
