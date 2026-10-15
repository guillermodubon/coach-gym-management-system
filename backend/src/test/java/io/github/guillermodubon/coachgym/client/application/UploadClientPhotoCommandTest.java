package io.github.guillermodubon.coachgym.client.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UploadClientPhotoCommandTest {

    @Test
    void acceptsSupportedSignaturesAndDefensivelyCopiesBytes() {
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1};
        UploadClientPhotoCommand command = new UploadClientPhotoCommand(" IMAGE/PNG ", png);
        png[8] = 9;
        assertThat(command.contentType()).isEqualTo("image/png");
        assertThat(command.bytes()[8]).isEqualTo((byte) 1);
    }

    @Test
    void rejectsUnsupportedMismatchedEmptyAndOversizedContent() {
        assertThatThrownBy(() -> new UploadClientPhotoCommand("text/plain", new byte[]{1}))
                .isInstanceOf(ClientPhotoValidationException.class);
        assertThatThrownBy(() -> new UploadClientPhotoCommand("image/png", new byte[]{1, 2, 3}))
                .isInstanceOf(ClientPhotoValidationException.class);
        assertThatThrownBy(() -> new UploadClientPhotoCommand("image/png", new byte[0]))
                .isInstanceOf(ClientPhotoValidationException.class);
        byte[] oversized = new byte[(int) UploadClientPhotoCommand.MAX_SIZE_BYTES + 1];
        assertThatThrownBy(() -> new UploadClientPhotoCommand("image/png", oversized))
                .isInstanceOf(ClientPhotoTooLargeException.class);
    }
}
