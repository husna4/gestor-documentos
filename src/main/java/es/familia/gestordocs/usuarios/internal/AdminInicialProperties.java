package es.familia.gestordocs.usuarios.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Propiedades {@code app.admin.*}: credenciales del primer administrador. */
@ConfigurationProperties("app.admin")
public record AdminInicialProperties(String username, String password) {
}
