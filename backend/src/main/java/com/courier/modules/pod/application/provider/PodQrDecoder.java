package com.courier.modules.pod.application.provider;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Server-side fallback for the QR cross-check: if the delivery app didn't scan the label's QR
 * live (or the operator's device has no camera access), the same QR is very often visible
 * inside the POD photo itself (the label is usually photographed alongside the parcel/
 * signature) — this decodes it from the already-uploaded photo bytes, no extra capture step
 * required. Best-effort only: a POD photo with no visible/decodable QR is not an error, just a
 * {@code null} result, since {@link HeuristicPodVerificationProvider} already has two other
 * independent identifiers (typed AWB/shipment number) to cross-check against.
 */
public final class PodQrDecoder {

    private PodQrDecoder() {
    }

    public static String decode(byte[] photoBytes) {
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
            int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
            RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixels);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            return new MultiFormatReader().decode(bitmap).getText();
        } catch (NotFoundException e) {
            return null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
