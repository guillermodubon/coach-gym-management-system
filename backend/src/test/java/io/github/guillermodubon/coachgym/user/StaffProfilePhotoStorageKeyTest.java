package io.github.guillermodubon.coachgym.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.user.application.StaffProfilePhotoStorageException;
import io.github.guillermodubon.coachgym.user.application.StaffProfilePhotoStorageKey;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StaffProfilePhotoStorageKeyTest {

    private static final UUID USER_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001");

    @Test
    void generatesCanonicalServerOwnedKeysForEachAllowedType() {
        String key = StaffProfilePhotoStorageKey.forUser(USER_ID, "IMAGE/PNG");

        assertThat(key)
                .matches("staff-profiles/10000000-0000-0000-0000-000000000001/"
                        + "[0-9a-f-]{36}\\.png");
        assertThat(StaffProfilePhotoStorageKey.isCanonical(key)).isTrue();
        assertThat(StaffProfilePhotoStorageKey
                .requireCanonicalForUser(key, USER_ID)).isEqualTo(key);
    }

    @Test
    void rejectsTraversalAbsoluteAndCrossUserKeys() {
        assertThatThrownBy(() -> StaffProfilePhotoStorageKey
                .requireCanonical("../staff-profiles/photo.png"))
                .isInstanceOf(StaffProfilePhotoStorageException.class);
        assertThatThrownBy(() -> StaffProfilePhotoStorageKey
                .requireCanonical("/absolute/photo.png"))
                .isInstanceOf(StaffProfilePhotoStorageException.class);

        String key = StaffProfilePhotoStorageKey.forUser(USER_ID, "image/png");
        assertThatThrownBy(() -> StaffProfilePhotoStorageKey
                .requireCanonicalForUser(key, UUID.randomUUID()))
                .isInstanceOf(StaffProfilePhotoStorageException.class)
                .hasMessage("Staff profile photo storage key ownership is invalid.");
        assertThatThrownBy(() -> StaffProfilePhotoStorageKey
                .requireCanonicalForContentType(key, "image/jpeg"))
                .isInstanceOf(StaffProfilePhotoStorageException.class)
                .hasMessage("Staff profile photo storage key type is invalid.");
    }
}
