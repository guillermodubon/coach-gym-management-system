package io.github.guillermodubon.coachgym.client.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class ClientPhotoWebContractTest {

    @Test
    void exposesMultipartUploadPrivateDownloadAndAdministrativeDelete() {
        RequestMapping root = ClientPhotoController.class
                .getAnnotation(RequestMapping.class);
        assertThat(root.value())
                .containsExactly("/api/v1/clients/{clientId}/photo");

        Method upload = method("upload");
        assertThat(upload.getAnnotation(PutMapping.class).consumes())
                .contains("multipart/form-data");

        Method load = method("load");
        assertThat(load.getAnnotation(GetMapping.class)).isNotNull();
        assertThat(load.getReturnType()).isEqualTo(ResponseEntity.class);

        Method delete = method("delete");
        assertThat(delete.getAnnotation(DeleteMapping.class)).isNotNull();
    }

    private static Method method(String name) {
        return Arrays.stream(ClientPhotoController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
