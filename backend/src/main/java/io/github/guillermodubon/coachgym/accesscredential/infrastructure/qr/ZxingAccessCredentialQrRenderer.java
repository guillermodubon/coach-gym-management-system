package io.github.guillermodubon.coachgym.accesscredential.infrastructure.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialDocument;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialQrPayload;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialQrRenderer;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialRenderException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import org.springframework.stereotype.Component;

/** ZXing adapter kept inside infrastructure so the library never leaks to contracts. */
@Component
class ZxingAccessCredentialQrRenderer implements AccessCredentialQrRenderer {

    static final String RENDERER_VERSION = "qr-zxing-v1";
    static final int IMAGE_SIZE = 256;
    static final int MARGIN = 4;
    private static final MatrixToImageConfig IMAGE_CONFIG =
            new MatrixToImageConfig(0xFF000000, 0xFFFFFFFF);

    @Override
    public AccessCredentialDocument render(AccessCredentialQrPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Access credential QR payload is required.");
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            QRCodeWriter writer = new QRCodeWriter();
            var hints = Map.<EncodeHintType, Object>of(
                    EncodeHintType.CHARACTER_SET, "UTF-8",
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, MARGIN);
            var matrix = writer.encode(
                    payload.value(), BarcodeFormat.QR_CODE, IMAGE_SIZE, IMAGE_SIZE, hints);
            MatrixToImageWriter.writeToStream(matrix, "PNG", output, IMAGE_CONFIG);
            byte[] bytes = output.toByteArray();
            requirePng(bytes);
            return new AccessCredentialDocument("image/png", bytes);
        } catch (WriterException | IOException | IllegalArgumentException exception) {
            throw new AccessCredentialRenderException(
                    "Access credential QR could not be rendered.", exception);
        }
    }

    @Override
    public String rendererVersion() {
        return RENDERER_VERSION;
    }

    private static void requirePng(byte[] bytes) {
        byte[] signature = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        };
        if (bytes.length < signature.length) {
            throw new AccessCredentialRenderException(
                    "Access credential QR renderer produced an invalid PNG.", null);
        }
        for (int index = 0; index < signature.length; index++) {
            if (bytes[index] != signature[index]) {
                throw new AccessCredentialRenderException(
                        "Access credential QR renderer produced an invalid PNG.", null);
            }
        }
    }
}
