package es.familia.gestordocs.documentos.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

/** Propiedades {@code app.documentos.*}. */
@Validated
@ConfigurationProperties("app.documentos")
public record DocumentosProperties(@NotNull DataSize tamanoMaximo) {
}
