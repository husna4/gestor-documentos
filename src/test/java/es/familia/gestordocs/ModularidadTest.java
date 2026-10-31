package es.familia.gestordocs;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Comprueba la arquitectura: ningún módulo usa clases internas de otro
 * y no hay dependencias circulares entre módulos. No necesita BD.
 */
class ModularidadTest {

    @Test
    void losModulosRespetanSusLimites() {
        ApplicationModules.of(GestorDocumentosApplication.class).verify();
    }
}
