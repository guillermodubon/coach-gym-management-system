package io.github.guillermodubon.coachgym.user.infrastructure.storage;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Local-only root for private staff profile photo objects. */
@Component
@ConfigurationProperties(prefix = "gym.storage.staff-photos")
class StaffProfilePhotoStorageProperties {

    private Path directory = Path.of("data", "staff-profile-photos");

    public Path getDirectory() {
        return directory;
    }

    public void setDirectory(Path directory) {
        if (directory == null) {
            throw new IllegalArgumentException("Staff profile photo directory is required.");
        }
        this.directory = directory;
    }
}
