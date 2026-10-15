package io.github.guillermodubon.coachgym.accesscredential.infrastructure.qr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialDocument;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialQrPayload;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ZxingAccessCredentialQrRendererTest {

    private static final String TOKEN =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Test
    void rendersDeterministicHighContrastPngThatDecodesOffline() throws Exception {
        ZxingAccessCredentialQrRenderer renderer = new ZxingAccessCredentialQrRenderer();
        AccessCredentialQrPayload payload = AccessCredentialQrPayload.fromToken(TOKEN);

        AccessCredentialDocument first = renderer.render(payload);
        AccessCredentialDocument second = renderer.render(payload);

        assertThat(renderer.rendererVersion()).isEqualTo("qr-zxing-v1");
        assertThat(first.contentType()).isEqualTo("image/png");
        assertThat(first.bytes()).containsExactly(second.bytes());
        assertThat(first.bytes()).startsWith(
                (byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47,
                (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A);
        assertThat(first.sizeBytes())
                .isGreaterThan(0)
                .isLessThanOrEqualTo(AccessCredentialDocument.MAX_SIZE_BYTES);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(first.bytes()));
        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isEqualTo(256);
        assertThat(image.getHeight()).isEqualTo(256);

        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(
                new BufferedImageLuminanceSource(image)));
        Result decoded = new MultiFormatReader().decode(bitmap);
        assertThat(decoded.getText()).isEqualTo(payload.value());
    }

    @Test
    void rejectsMissingPayloadAsAContainedRenderFailure() {
        ZxingAccessCredentialQrRenderer renderer = new ZxingAccessCredentialQrRenderer();

        assertThatThrownBy(() -> renderer.render(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Access credential QR payload is required.");
    }

    @Test
    void exposesNoProviderTypeThroughTheRendererPort() {
        assertThat(io.github.guillermodubon.coachgym.accesscredential.application
                .AccessCredentialQrRenderer.class.getDeclaredMethods())
                .allSatisfy(method -> assertThat(method.getReturnType().getName())
                        .doesNotStartWith("com.google.zxing."));
    }
}
