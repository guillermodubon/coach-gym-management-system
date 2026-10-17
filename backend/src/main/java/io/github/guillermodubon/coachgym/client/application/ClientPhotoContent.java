package io.github.guillermodubon.coachgym.client.application;

/** Binary client photo returned by the storage boundary. */
public record ClientPhotoContent(
        String contentType,
        byte[] bytes,
        String checksumSha256) {

    public ClientPhotoContent {
        if (contentType == null || contentType.isBlank()) {
            throw new ClientPhotoValidationException("Client photo content type is required.");
        }
        if (bytes == null || bytes.length == 0) {
            throw new ClientPhotoValidationException("Client photo content is required.");
        }
        bytes = bytes.clone();
        if (checksumSha256 == null || !checksumSha256.matches("[0-9a-f]{64}")) {
            throw new ClientPhotoValidationException("Client photo checksum is invalid.");
        }
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
