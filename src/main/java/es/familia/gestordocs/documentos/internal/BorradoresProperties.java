package es.familia.gestordocs.documentos.internal;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("app.borradores")
public record BorradoresProperties(Path ruta, Duration caducidad, DataSize espacioMaximo,
                                   int maximoPorUsuario, int maximoTotal) {
    public BorradoresProperties {
        if (ruta == null || caducidad == null || caducidad.isNegative() || caducidad.isZero()
                || espacioMaximo == null || espacioMaximo.toBytes() < 1
                || maximoPorUsuario < 1 || maximoTotal < maximoPorUsuario) {
            throw new IllegalArgumentException("Configuración de borradores no válida");
        }
        ruta = ruta.toAbsolutePath().normalize();
        if (ruta.getParent() == null) {
            throw new IllegalArgumentException("La carpeta temporal no puede ser la raíz del disco");
        }
    }
}
