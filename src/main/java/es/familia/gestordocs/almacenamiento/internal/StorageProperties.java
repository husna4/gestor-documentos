package es.familia.gestordocs.almacenamiento.internal;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

/** Propiedad {@code app.storage.path}: carpeta raíz donde se guardan los ficheros. */
@Validated
@ConfigurationProperties("app.storage")
public record StorageProperties(@NotNull Path path) {
}
