package es.familia.gestordocs.usuarios.internal;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Propiedades {@code app.recordarme.*} de application.yml.
 *
 * @param clave   clave secreta con la que se firma la cookie; si cambia, todos los "Recordarme" caducan
 * @param validez cuánto dura la cookie
 */
@Validated
@ConfigurationProperties("app.recordarme")
public record RecordarmeProperties(@NotBlank String clave, @NotNull Duration validez) {
}
