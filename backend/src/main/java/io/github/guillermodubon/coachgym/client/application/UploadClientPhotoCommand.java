package io.github.guillermodubon.coachgym.client.application;

/** Validated photo upload input. */
public record UploadClientPhotoCommand(
        String contentType,
        byte[] bytes) {

    public static final long MAX_SIZE_BYTES = 5L * 1024L * 1024L;

    public UploadClientPhotoCommand {
        contentType = ClientPhotoPolicy.normalizeContentType(contentType);
        if (bytes == null || bytes.length == 0) {
            throw new ClientPhotoValidationException("Client photo content is required.");
        }
        if (bytes.length > MAX_SIZE_BYTES) {
            throw new ClientPhotoTooLargeException(bytes.length, MAX_SIZE_BYTES);
        }
        ClientPhotoPolicy.requireMatchingSignature(contentType, bytes);
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
