package es.familia.gestordocs.documentos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import es.familia.gestordocs.SesionDePrueba;
import es.familia.gestordocs.TestIntegracion;

/** Opt-in: requiere un DocScanner real. Solo utiliza fotografías sintéticas y la BD de tests. */
@TestIntegracion
@TestPropertySource(properties = "app.documentos.tamano-maximo=2MB")
@EnabledIfEnvironmentVariable(named = "DOCSCANNER_LIVE_TEST", matches = "true")
class DocScannerRealTest {
    @Autowired DocumentoService documentos;
    @Autowired SesionDePrueba sesion;
    @Autowired MockMvc mvc;

    @AfterEach
    void salir() { sesion.cerrar(); }

    @Test
    void fotoInclinadaPasaPorFormularioApiRealYAlmacenamiento() throws Exception {
        var admin = sesion.comoAdmin();
        var muestra = new ClassPathResource("escaneo/documento-inclinado.jpg").getContentAsByteArray();
        mvc.perform(multipart("/documentos/subir")
                        .file(new MockMultipartFile("ficheros", "prueba-escaneo-real.jpg", "image/jpeg", muestra))
                        .param("escanearFotos", "true").with(user(admin)).with(csrf()))
                .andExpect(redirectedUrl("/documentos/subir"))
                .andExpect(flash().attribute("rechazados", List.of()))
                .andExpect(flash().attribute("avisos", List.of()))
                .andExpect(flash().attribute("subidos", List.of("prueba-escaneo-real")));
        sesion.comoAdmin();
        var doc = documentos.buscar("prueba-escaneo-real").getFirst();
        assertThat(doc.tipoMime()).isEqualTo("image/jpeg");
        assertThat(doc.tieneMiniatura()).isTrue();
        try (var input = documentos.contenido(doc.id()).recurso().getInputStream()) {
            var imagen = ImageIO.read(input);
            assertThat(imagen.getWidth()).isBetween(750, 810);
            assertThat(imagen.getHeight()).isBetween(730, 800);
        }
    }

    @Autowired BorradorEscaneoService borradores;

    @Test
    void previsualizaCuatroFiltrosYGuardaExactamenteElElegido() throws Exception {
        sesion.comoAdmin();
        var muestra = new ClassPathResource("escaneo/documento-inclinado.jpg").getContentAsByteArray();
        var id = borradores.crear(new FicheroSubido("vista-real.jpg", muestra.length, new ByteArrayResource(muestra))).id();
        try {
            for (var filtro : List.of("enhanced", "grayscale", "bw", "original")) {
                assertThat(borradores.filtrar(id, filtro).sinBordes()).isFalse();
                var vista = borradores.imagen(id, filtro).contenido();
                try (var input = new java.io.ByteArrayInputStream(vista)) {
                    var imagen = ImageIO.read(input);
                    assertThat(imagen.getWidth()).isBetween(750, 810);
                    assertThat(imagen.getHeight()).isBetween(730, 800);
                    imagen.flush();
                }
            }
            assertThat(borradores.imagen(id, "fuente").contenido()).isEqualTo(muestra);
            var elegida = borradores.imagen(id, "bw").contenido();
            var guardado = documentos.subir(new SubidaForm(List.of(), null, "Vista real"), List.of(),
                    List.of(new SeleccionBorrador(id, "bw")));
            assertThat(guardado.errores()).isEmpty();
            assertThat(documentos.contenido(guardado.subidos().getFirst().id()).recurso().getContentAsByteArray())
                    .isEqualTo(elegida);
        } finally {
            org.springframework.test.context.transaction.TestTransaction.flagForRollback();
            org.springframework.test.context.transaction.TestTransaction.end();
            borradores.descartar(id);
            org.springframework.test.context.transaction.TestTransaction.start();
        }
    }

    @Test
    void sinDocumentoSeConservaElEncuadreConAviso() throws Exception {
        sesion.comoAdmin();
        var imagen = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
        var graphics = imagen.createGraphics();
        graphics.setColor(new Color(100, 120, 110));
        graphics.fillRect(0, 0, 320, 240);
        graphics.dispose();
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(imagen, "PNG", bytes);
        var resultado = documentos.subir(new SubidaForm(List.of(), null, "Fallback real", true),
                List.of(new FicheroSubido("sin-bordes-real.png", bytes.size(), new ByteArrayResource(bytes.toByteArray()))));
        assertThat(resultado.errores()).isEmpty();
        assertThat(resultado.avisos()).hasSize(1);
        try (var input = documentos.contenido(resultado.subidos().getFirst().id()).recurso().getInputStream()) {
            var guardada = ImageIO.read(input);
            assertThat(guardada.getWidth()).isEqualTo(320);
            assertThat(guardada.getHeight()).isEqualTo(240);
        }
    }
}
