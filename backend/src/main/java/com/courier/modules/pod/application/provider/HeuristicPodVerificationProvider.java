package com.courier.modules.pod.application.provider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The only {@link PodVerificationProvider} shipped in this codebase — a deterministic local
 * scorer, not a real trained model. There is no AI/vision vendor credential configured in
 * this dev environment (the same class of gap {@code NotificationPort}/SMTP already
 * documents), so this stands in behind the same interface a real OCR/vision call would
 * implement: it decodes the image with the JDK's own {@link ImageIO} (no new dependency) and
 * scores plain structural signals — darkness, blur (a gradient-magnitude proxy, not a true
 * Laplacian-variance detector), resolution, signature presence, receiver-name completeness,
 * and an AWB cross-check against this platform's own database record (real ground truth,
 * genuinely useful, and needs no OCR since the shipment's actual AWB is already known
 * server-side). It performs no OCR — {@code detectedReceiverName}/{@code detectedAwb}/
 * {@code detectedDate} are best-effort passthroughs of the caller-supplied/system values,
 * not text read out of the pixels. This is documented honestly in
 * {@code MEMORY/modules/pod-verification.md}; swapping in a real vision provider later is a
 * second {@link PodVerificationProvider} implementation and a config change, nothing here.
 *
 * <p>Active whenever {@code pod.ai.enabled} is {@code true} (the default) —
 * {@link UnavailablePodVerificationProvider} takes over when it is set to {@code false},
 * simulating an unconfigured/unreachable AI vendor so the "provider unavailable -> manual
 * review" rule has something real to exercise.
 */
@Service
@ConditionalOnProperty(prefix = "pod.ai", name = "enabled", havingValue = "true", matchIfMissing = true)
public class HeuristicPodVerificationProvider implements PodVerificationProvider {

    private static final int MIN_DIMENSION_PIXELS = 300;
    private static final int DARKNESS_LUMINANCE_THRESHOLD = 60;
    private static final double BLUR_GRADIENT_THRESHOLD = 8.0;
    private static final int SAMPLE_GRID = 40;

    @Override
    public String providerName() {
        return "heuristic-local";
    }

    @Override
    public String modelName() {
        return "structural-v1";
    }

    @Override
    public PodAnalysisResult analyze(PodAnalysisRequest request) {
        List<String> reasons = new ArrayList<>();
        int score = 100;
        String imageQuality;

        ImageMetrics metrics = decode(request.photoBytes());
        if (metrics == null) {
            score -= 100;
            reasons.add("Image could not be read — file may be corrupt or an unsupported format.");
            imageQuality = "POOR";
        } else {
            int issues = 0;
            if (metrics.avgLuminance() < DARKNESS_LUMINANCE_THRESHOLD) {
                score -= 25;
                issues++;
                reasons.add("Image appears too dark to verify clearly.");
            }
            if (metrics.avgGradient() < BLUR_GRADIENT_THRESHOLD) {
                score -= 25;
                issues++;
                reasons.add("Image appears blurry.");
            }
            if (metrics.width() < MIN_DIMENSION_PIXELS || metrics.height() < MIN_DIMENSION_PIXELS) {
                score -= 15;
                issues++;
                reasons.add("Image resolution is too low for reliable verification.");
            }
            imageQuality = issues == 0 ? "GOOD" : issues == 1 ? "FAIR" : "POOR";
        }

        boolean signatureDetected = request.signatureBytes() != null && request.signatureBytes().length > 0;
        if (!signatureDetected) {
            score -= 15;
            reasons.add("No signature detected on this delivery capture.");
        }

        String receiverName = request.receiverName() == null ? "" : request.receiverName().trim();
        if (receiverName.isBlank()) {
            score -= 20;
            reasons.add("Receiver name was not provided.");
        } else if (receiverName.length() < 2) {
            score -= 10;
            reasons.add("Receiver name looks incomplete.");
        }

        boolean awbMismatch = isMismatch(request.claimedAwb(), request.shipmentActualAwb())
                || isMismatch(request.claimedShipmentNumber(), request.shipmentActualNumber());
        if (awbMismatch) {
            score -= 40;
            reasons.add("Provided AWB/shipment number does not match this shipment's own record.");
        }

        boolean mustReview = false;
        if (request.duplicateSuspectedByHash()) {
            score -= 50;
            reasons.add("This POD photo matches one already used on a different shipment — "
                    + "possible duplicate submission.");
            mustReview = true;
        }

        boolean tamperingSuspected = metrics != null && looksTampered(metrics, request.photoBytes().length);
        if (tamperingSuspected) {
            score -= 10;
            reasons.add("Image metadata looks stripped or heavily re-compressed — possible edited "
                    + "file (unverified signal).");
            mustReview = true;
        }

        score = Math.max(0, Math.min(100, score));

        String detectedAwb = firstNonBlank(request.claimedAwb(), request.shipmentActualAwb());
        String detectedDate = request.deliveryDateTime() == null ? null : request.deliveryDateTime().toString();

        return new PodAnalysisResult(score, List.copyOf(reasons), signatureDetected, imageQuality,
                receiverName.isBlank() ? null : receiverName, detectedAwb, detectedDate,
                tamperingSuspected, mustReview);
    }

    private static boolean isMismatch(String claimed, String actual) {
        if (claimed == null || claimed.isBlank() || actual == null || actual.isBlank()) {
            return false;
        }
        return !claimed.trim().equalsIgnoreCase(actual.trim());
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        return b == null || b.isBlank() ? null : b.trim();
    }

    /** A very small file for a lot of declared pixels is a weak, honestly-labelled signal of
     *  re-saving/metadata stripping — not proof of tampering. */
    private static boolean looksTampered(ImageMetrics metrics, int byteLength) {
        long pixels = (long) metrics.width() * metrics.height();
        if (pixels < MIN_DIMENSION_PIXELS * MIN_DIMENSION_PIXELS) {
            return false;
        }
        double bytesPerPixel = byteLength / (double) pixels;
        return bytesPerPixel < 0.05;
    }

    private static ImageMetrics decode(byte[] photoBytes) {
        if (photoBytes == null || photoBytes.length == 0) {
            return null;
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(photoBytes));
            if (image == null) {
                return null;
            }
            int width = image.getWidth();
            int height = image.getHeight();
            int stepX = Math.max(1, width / SAMPLE_GRID);
            int stepY = Math.max(1, height / SAMPLE_GRID);

            long luminanceSum = 0;
            long gradientSum = 0;
            int samples = 0;
            int previousLuminance = -1;
            for (int y = 0; y < height; y += stepY) {
                for (int x = 0; x < width; x += stepX) {
                    int rgb = image.getRGB(x, y);
                    int luminance = luminanceOf(rgb);
                    luminanceSum += luminance;
                    if (previousLuminance >= 0) {
                        gradientSum += Math.abs(luminance - previousLuminance);
                    }
                    previousLuminance = luminance;
                    samples++;
                }
                previousLuminance = -1;
            }
            if (samples == 0) {
                return null;
            }
            double avgLuminance = luminanceSum / (double) samples;
            double avgGradient = gradientSum / (double) Math.max(1, samples - (height / stepY));
            return new ImageMetrics(width, height, avgLuminance, avgGradient);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static int luminanceOf(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (int) (0.299 * r + 0.587 * g + 0.114 * b);
    }

    private record ImageMetrics(int width, int height, double avgLuminance, double avgGradient) {
    }
}
