package es.familia.gestordocs;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;

class ConfiguracionSecretosTest {

    @Test
    void importaSecretosDesdeArchivosSobreLaConfiguracionDelClasspath(@TempDir Path secretos) throws Exception {
        Files.writeString(secretos.resolve("spring.datasource.password"), "password-desde-archivo");
        Files.writeString(secretos.resolve("app.admin.username"), "admin-desde-archivo");
        Files.writeString(secretos.resolve("app.admin.password"), "admin-password-desde-archivo");
        Files.writeString(secretos.resolve("app.recordarme.clave"), "clave-desde-archivo");

        try (var contexto = new SpringApplicationBuilder(ConfiguracionDePrueba.class)
                .web(WebApplicationType.NONE)
                .run("--spring.config.import=configtree:" + secretos.toAbsolutePath() + "/",
                        "--spring.main.banner-mode=off", "--logging.level.root=ERROR")) {
            var entorno = contexto.getEnvironment();
            assertThat(entorno.getProperty("spring.datasource.password")).isEqualTo("password-desde-archivo");
            assertThat(entorno.getProperty("app.admin.username")).isEqualTo("admin-desde-archivo");
            assertThat(entorno.getProperty("app.admin.password")).isEqualTo("admin-password-desde-archivo");
            assertThat(entorno.getProperty("app.recordarme.clave")).isEqualTo("clave-desde-archivo");
            // Las opciones no secretas siguen saliendo del application.yml habitual.
            assertThat(entorno.getProperty("spring.application.name")).isEqualTo("gestor-documentos");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracionDePrueba {
    }
}
