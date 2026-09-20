package io.github.guillermodubon.coachgym.user;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.user.application.StaffProfilePhotoContent;
import io.github.guillermodubon.coachgym.user.application.StaffProfilePhotoInspector;
import io.github.guillermodubon.coachgym.user.application.StaffProfileValidationException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class StaffProfilePhotoInspectorTest {

    @Test
    void acceptsRealPngAndJpegSignaturesAndBoundedDimensions() throws Exception {
        StaffProfilePhotoInspector.requireSafe(content("image/png", "PNG"));
        StaffProfilePhotoInspector.requireSafe(content("image/jpeg", "JPEG"));
    }

    @Test
    void rejectsMismatchedOrMalformedImageContent() throws Exception {
        StaffProfilePhotoContent content = new StaffProfilePhotoContent(
                "image/png", new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47},
                checksum(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47}));

        assertThatThrownBy(() -> StaffProfilePhotoInspector.requireSafe(content))
                .isInstanceOf(StaffProfileValidationException.class)
                .hasMessage("Staff profile photo signature is invalid.");
    }

    @Test
    void rejectsImagesThatExceedDimensionBoundsBeforeRasterDecoding() throws Exception {
        BufferedImage image = new BufferedImage(
                StaffProfilePhotoInspector.MAX_DIMENSION + 1, 1,
                BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", output);
        StaffProfilePhotoContent content = new StaffProfilePhotoContent(
                "image/png", output.toByteArray(), checksum(output.toByteArray()));

        assertThatThrownBy(() -> StaffProfilePhotoInspector.requireSafe(content))
                .isInstanceOf(StaffProfileValidationException.class)
                .hasMessage("Staff profile photo dimensions exceed the allowed bounds.");
    }

    @Test
    void acceptsSignatureValidatedWebpWithoutRequiringAnOptionalJdkReader() throws Exception {
        byte[] bytes = {
            'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'
        };
        StaffProfilePhotoInspector.requireSafe(new StaffProfilePhotoContent(
                "image/webp", bytes, checksum(bytes)));
    }

    private static StaffProfilePhotoContent content(String type, String format)
            throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        byte[] bytes = output.toByteArray();
        return new StaffProfilePhotoContent(type, bytes, checksum(bytes));
    }

    private static String checksum(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
