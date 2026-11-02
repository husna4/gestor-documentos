package es.familia.gestordocs.documentos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import es.familia.gestordocs.Muestras;
import es.familia.gestordocs.SesionDePrueba;
import es.familia.gestordocs.TestIntegracion;
import es.familia.gestordocs.escaneo.EscaneoException;
import es.familia.gestordocs.escaneo.EscaneoService;
import es.familia.gestordocs.escaneo.ResultadoEscaneo;
import es.familia.gestordocs.personas.PersonaForm;
import es.familia.gestordocs.personas.PersonaService;
import es.familia.gestordocs.secciones.SeccionForm;
import es.familia.gestordocs.secciones.SeccionService;

@TestIntegracion
class DocumentoEscaneoTest {
    @Autowired DocumentoService documentos;
    @Autowired SesionDePrueba sesion;
    @Autowired PersonaService personas;
    @Autowired SeccionService secciones;
    @Autowired MockMvc mvc;
    @MockitoBean EscaneoService escaneo;

    @BeforeEach
    void entrar() { sesion.comoAdmin(); }

    @AfterEach
    void salir() { sesion.cerrar(); }

    private static SubidaForm conEscaneo() {
        return new SubidaForm(List.of(), null, null, true);
    }

    private static byte[] jpeg() throws IOException {
        return Muestras.jpeg("procesado.jpg").contenido().getInputStream().readAllBytes();
    }

    @Test
    void guardaElJpegProcesadoYSuMiniaturaSinPerderClasificacion() throws IOException {
        var persona = personas.crear(new PersonaForm("Persona de prueba", null, null));
        var seccion = secciones.crear(new SeccionForm("Facturas de prueba", null));
        var procesado = jpeg();
        when(escaneo.escanear(any(), eq("image/png"))).thenReturn(new ResultadoEscaneo(procesado, true));
        var resultado = documentos.subir(new SubidaForm(List.of(seccion.id()), persona.id(), "Mi factura", true),
                List.of(Muestras.png("factura.png")));

        assertThat(resultado.errores()).isEmpty();
        assertThat(resultado.avisos()).isEmpty();
        var doc = resultado.subidos().getFirst();
        assertThat(doc.nombreOriginal()).isEqualTo("factura.jpg");
        assertThat(doc.nombreVisible()).isEqualTo("factura");
        assertThat(doc.tipoMime()).isEqualTo("image/jpeg");
        assertThat(doc.tamanoBytes()).isEqualTo(procesado.length);
        assertThat(doc.persona().id()).isEqualTo(persona.id());
        assertThat(doc.secciones()).extracting("id").containsExactly(seccion.id());
        assertThat(doc.descripcion()).isEqualTo("Mi factura");
        assertThat(doc.tieneMiniatura()).isTrue();
        assertThat(documentos.contenido(doc.id()).recurso().getContentAsByteArray()).isEqualTo(procesado);
        assertThat(documentos.contenido(doc.id()).nombreDescarga()).isEqualTo("factura.jpg");
        verify(escaneo).escanear(any(), eq("image/png"));
    }

    @Test
    void desmarcarEscaneoConservaElOriginalSinLlamarAlServicio() throws IOException {
        var original = Muestras.png("original.png");
        var doc = documentos.subir(new SubidaForm(List.of(), null, null, false), List.of(original))
                .subidos().getFirst();
        verifyNoInteractions(escaneo);
        assertThat(doc.tipoMime()).isEqualTo("image/png");
        assertThat(doc.nombreOriginal()).isEqualTo("original.png");
        assertThat(documentos.contenido(doc.id()).recurso().getContentAsByteArray())
                .isEqualTo(original.contenido().getInputStream().readAllBytes());
    }

    @Test
    void pdfYTextoNoSeEnvianAunqueEsteActivadoElEscaneo() {
        var resultado = documentos.subir(conEscaneo(), List.of(Muestras.pdf("contrato.pdf"), Muestras.texto("nota.txt")));
        assertThat(resultado.errores()).isEmpty();
        assertThat(resultado.subidos()).extracting(DocumentoDto::tipoMime)
                .containsExactly("application/pdf", "text/plain");
        verifyNoInteractions(escaneo);
    }

    @Test
    void sinBordesGuardaLaImagenCompletaEInforma() throws IOException {
        var completo = jpeg();
        when(escaneo.escanear(any(), any())).thenReturn(new ResultadoEscaneo(completo, false));
        var resultado = documentos.subir(conEscaneo(), List.of(Muestras.jpeg("sin-bordes.jpg")));
        assertThat(resultado.errores()).isEmpty();
        assertThat(resultado.subidos()).hasSize(1);
        assertThat(resultado.avisos()).containsExactly(
                "sin-bordes.jpg: no se detectaron los bordes; se ha guardado la imagen completa.");
        assertThat(documentos.contenido(resultado.subidos().getFirst().id()).recurso().getContentAsByteArray())
                .isEqualTo(completo);
    }

    @Test
    void falloDelEscanerNoGuardaOriginalNiDejaHuerfanos() throws IOException {
        when(escaneo.escanear(any(), any())).thenThrow(new EscaneoException("El escáner está ocupado."));
        var antes = contarFicheros();
        var resultado = documentos.subir(conEscaneo(), List.of(Muestras.jpeg("no-guardado.jpg")));
        assertThat(resultado.subidos()).isEmpty();
        assertThat(resultado.errores()).containsExactly("no-guardado.jpg: El escáner está ocupado.");
        assertThat(contarFicheros()).isEqualTo(antes);
        assertThat(documentos.buscar("no-guardado")).isEmpty();
    }

    @Test
    void unFalloDeFotoNoImpideGuardarLosDemasDocumentos() {
        when(escaneo.escanear(any(), any())).thenThrow(new EscaneoException("Servicio no disponible."));
        var resultado = documentos.subir(conEscaneo(), List.of(Muestras.jpeg("foto.jpg"), Muestras.pdf("contrato.pdf")));
        assertThat(resultado.subidos()).extracting(DocumentoDto::nombreVisible).containsExactly("contrato");
        assertThat(resultado.errores()).containsExactly("foto.jpg: Servicio no disponible.");
    }

    @Test
    void validaTipoYTamanosAntesDeInvocarElEscaner() {
        var resultado = documentos.subir(conEscaneo(), List.of(Muestras.ejecutableDisfrazado("foto.jpg"),
                Muestras.deTamano("enorme.jpg", 200 * 1024), Muestras.vacio("vacio.png")));
        assertThat(resultado.subidos()).isEmpty();
        assertThat(resultado.errores()).hasSize(3);
        verifyNoInteractions(escaneo);
    }

    @Test
    void rechazaResultadoMayorQueElLimiteDeAlmacenamiento() throws IOException {
        when(escaneo.escanear(any(), any())).thenReturn(new ResultadoEscaneo(new byte[101 * 1024], true));
        var antes = contarFicheros();
        var resultado = documentos.subir(conEscaneo(), List.of(Muestras.jpeg("crecida.jpg")));
        assertThat(resultado.subidos()).isEmpty();
        assertThat(resultado.errores().getFirst()).contains("tamaño permitido");
        assertThat(contarFicheros()).isEqualTo(antes);
    }

    @Test
    void unUsuarioSinRolAdminNoPuedeActivarEscaneo() {
        sesion.comoUsuario();
        assertThatThrownBy(() -> documentos.subir(conEscaneo(), List.of(Muestras.jpeg("foto.jpg"))))
                .isInstanceOf(AuthorizationDeniedException.class);
        verifyNoInteractions(escaneo);
    }

    @Test
    void formularioOfreceEscaneoActivadoPorDefecto() throws Exception {
        var admin = sesion.comoAdmin();
        var html = mvc.perform(get("/documentos/subir").with(user(admin))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Escanear fotos antes de guardar");
        assertThat(html).containsPattern("(?s)id=\"escanearFotos\"[^>]*checked");
    }

    @Test
    void formularioEnvíaEscaneoYPresentaLosAvisos() throws Exception {
        var admin = sesion.comoAdmin();
        when(escaneo.escanear(any(), any())).thenReturn(new ResultadoEscaneo(jpeg(), false));
        var foto = new MockMultipartFile("ficheros", "camara.png", "image/png",
                Muestras.png("camara.png").contenido().getInputStream().readAllBytes());
        mvc.perform(multipart("/documentos/subir").file(foto).param("escanearFotos", "true")
                        .with(user(admin)).with(csrf()))
                .andExpect(redirectedUrl("/documentos/subir"))
                .andExpect(flash().attribute("subidos", List.of("camara")))
                .andExpect(flash().attribute("rechazados", List.of()))
                .andExpect(flash().attribute("avisos", List.of(
                        "camara.png: no se detectaron los bordes; se ha guardado la imagen completa.")));
        mvc.perform(get("/documentos/subir").flashAttr("avisos", List.of("Aviso de prueba"))
                        .with(user(admin)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Aviso de prueba")));
    }

    @Test
    void formularioDesmarcadoNoInvocaElEscaner() throws Exception {
        var admin = sesion.comoAdmin();
        var foto = new MockMultipartFile("ficheros", "original.png", "image/png",
                Muestras.png("original.png").contenido().getInputStream().readAllBytes());
        mvc.perform(multipart("/documentos/subir").file(foto).param("_escanearFotos", "on")
                        .with(user(admin)).with(csrf()))
                .andExpect(redirectedUrl("/documentos/subir"))
                .andExpect(flash().attribute("subidos", List.of("original")));
        verifyNoInteractions(escaneo);
    }

    private static long contarFicheros() throws IOException {
        var carpeta = Path.of("target/ficheros-test/ficheros");
        if (!Files.exists(carpeta)) { return 0; }
        try (var paths = Files.walk(carpeta)) { return paths.filter(Files::isRegularFile).count(); }
    }
}
