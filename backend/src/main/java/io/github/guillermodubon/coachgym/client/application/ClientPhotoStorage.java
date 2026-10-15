package io.github.guillermodubon.coachgym.client.application;

public interface ClientPhotoStorage {

    void store(String storageKey, byte[] content);

    ClientPhotoContent load(
            String storageKey,
            String contentType,
            String checksumSha256);

    void delete(String storageKey);
}
