package io.github.guillermodubon.coachgym.payment.infrastructure.storage;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "gym.storage.payment-receipts")
class PaymentReceiptStorageProperties {

    private Path directory = Path.of("data", "payment-receipts");

    public Path getDirectory() {
        return directory;
    }

    public void setDirectory(Path directory) {
        if (directory == null) {
            throw new IllegalArgumentException("Payment receipt directory is required.");
        }
        this.directory = directory;
    }
}
