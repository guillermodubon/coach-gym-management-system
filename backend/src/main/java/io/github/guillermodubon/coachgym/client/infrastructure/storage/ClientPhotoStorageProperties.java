package io.github.guillermodubon.coachgym.client.infrastructure.storage;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "gym.storage.client-photos")
class ClientPhotoStorageProperties {

    private Path directory = Path.of("data", "client-photos");

    public Path getDirectory() {
        return directory;
    }

    public void setDirectory(Path directory) {
        if (directory == null) {
            throw new IllegalArgumentException("Client photo directory is required.");
        }
        this.directory = directory;
    }
}
