package io.github.guillermodubon.coachgym.accesscredential.infrastructure.storage;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "gym.storage.access-credentials")
class AccessCredentialStorageProperties {

    private Path directory = Path.of("data", "access-credentials");

    public Path getDirectory() {
        return directory;
    }

    public void setDirectory(Path directory) {
        if (directory == null) {
            throw new IllegalArgumentException("Access credential directory is required.");
        }
        this.directory = directory;
    }
}
