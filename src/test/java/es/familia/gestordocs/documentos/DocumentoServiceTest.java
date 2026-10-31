package es.familia.gestordocs.documentos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.transaction.TestTransaction;

import es.familia.gestordocs.Muestras;
import es.familia.gestordocs.SesionDePrueba;
import es.familia.gestordocs.TestIntegracion;
import es.familia.gestordocs.compartido.ReglaNegocioException;
import es.familia.gestordocs.personas.PersonaForm;
import es.familia.gestordocs.personas.PersonaService;
import es.familia.gestordocs.secciones.SeccionForm;
import es.familia.gestordocs.secciones.SeccionService;

@TestIntegracion
class DocumentoServiceTest {

    /** Coincide con app.storage.path de application-test.yml. */
    private static final Path CARPETA_FICHEROS = Path.of("target/ficheros-test/ficheros");

    @Autowired
    DocumentoService documentoService;

    @Autowired
    SeccionService seccionService;

    @Autowired
    PersonaService personaService;

    @Autowired
    SesionDePrueba sesion;

    @BeforeEach
    void entrarComoAdmin() {
        sesion.comoAdmin();
    }

    @AfterEach
    void salir() {
        sesion.cerrar();
    }

    @Test
    void subirUnaImagenGuardaElFicheroSuTipoRealYUnaMiniatura() throws IOException {
        var dni = seccionService.crear(new SeccionForm("DNI", null));
        var maria = personaService.crear(new PersonaForm("María", "López", null));

        var resultado = documentoService.subir(new SubidaForm(List.of(dni.id()), maria.id(), "Por delante"),
                List.of(Muestras.jpeg("dni-maria.jpg")));

        assertThat(resultado.errores()).isEmpty();
        var documento = resultado.subidos().getFirst();
        assertThat(documento.nombreVisible()).isEqualTo("dni-maria");
        assertThat(documento.nombreOriginal()).isEqualTo("dni-maria.jpg");
        assertThat(documento.tipoMime()).isEqualTo("image/jpeg");
        assertThat(documento.persona().nombre()).isEqualTo("María");
        assertThat(documento.secciones()).extracting("nombre").containsExactly("DNI");
        assertThat(documento.subidoPor()).isEqualTo("admin-test");
        assertThat(documento.tieneMiniatura()).isTrue();
        assertThat(documentoService.miniatura(documento.id())).isPresent();

        var contenido = documentoService.contenido(documento.id());
        assertThat(contenido.nombreDescarga()).isEqualTo("dni-maria.jpg");
        assertThat(contenido.previsualizable()).isTrue();
        assertThat(contenido.recurso().contentLength()).isEqualTo(documento.tamanoBytes());
    }

    @Test
    void unaSubidaMultipleAceptaLosValidosYExplicaLosRechazados() {
        var resultado = documentoService.subir(new SubidaForm(List.of(), null, null), List.of(
                Muestras.pdf("contrato casa.pdf"),
                Muestras.texto("notas.txt"),
                Muestras.ejecutableDisfrazado("foto.jpg"),
                Muestras.vacio("vacio.pdf"),
                Muestras.deTamano("enorme.pdf", 200 * 1024)));

        assertThat(resultado.subidos()).extracting(DocumentoDto::tipoMime)
                .containsExactly("application/pdf", "text/plain");
        assertThat(resultado.errores()).hasSize(3);
        assertThat(resultado.errores().get(0)).startsWith("foto.jpg: tipo de fichero no permitido");
        assertThat(resultado.errores().get(1)).isEqualTo("vacio.pdf: el fichero está vacío.");
        assertThat(resultado.errores().get(2)).isEqualTo("enorme.pdf: supera el tamaño máximo de 100 KB.");
    }

    @Test
    void sinSeccionesElDocumentoQuedaSinClasificar() {
        var antes = documentoService.contarSinClasificar();

        documentoService.subir(new SubidaForm(List.of(), null, null), List.of(Muestras.pdf("suelto.pdf")));

        assertThat(documentoService.contarSinClasificar()).isEqualTo(antes + 1);
    }

    @Test
    void noSePuedeSubirAUnaSeccionQueNoExiste() {
        assertThatThrownBy(() -> documentoService.subir(new SubidaForm(List.of(999_999L), null, null),
                List.of(Muestras.pdf("x.pdf"))))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void editarCambiaNombreSeccionesYPersona() {
        var casa = seccionService.crear(new SeccionForm("Casa", null));
        var alquiler = seccionService.crear(new SeccionForm("Ayuda alquiler", null));
        var pepe = personaService.crear(new PersonaForm("Pepe", null, null));
        var subido = documentoService.subir(new SubidaForm(List.of(casa.id()), null, null),
                List.of(Muestras.pdf("contrato.pdf"))).subidos().getFirst();

        var editado = documentoService.editar(subido.id(), new EdicionDocumentoForm(
                "Contrato de alquiler", "Firmado en 2024", List.of(casa.id(), alquiler.id()), pepe.id()));

        assertThat(editado.nombreVisible()).isEqualTo("Contrato de alquiler");
        assertThat(editado.descripcion()).isEqualTo("Firmado en 2024");
        assertThat(editado.secciones()).extracting("nombre").containsExactly("Ayuda alquiler", "Casa");
        assertThat(editado.persona().id()).isEqualTo(pepe.id());
        assertThat(documentoService.contenido(subido.id()).nombreDescarga()).isEqualTo("Contrato de alquiler.pdf");
        assertThat(documentoService.porSeccion(alquiler.id(), null)).extracting(DocumentoDto::id)
                .containsExactly(subido.id());
        assertThat(documentoService.porSeccion(casa.id(), pepe.id())).hasSize(1);
    }

    @Test
    void siLaTransaccionSeDeshaceNoQuedanFicherosHuerfanosEnDisco() throws IOException {
        var antes = contarFicheros();

        documentoService.subir(new SubidaForm(List.of(), null, null), List.of(Muestras.pdf("temporal.pdf")));
        assertThat(contarFicheros()).isEqualTo(antes + 1);

        TestTransaction.end(); // rollback

        assertThat(contarFicheros()).isEqualTo(antes);
    }

    @Test
    void borrarUnDocumentoBorraSuFicheroAlConfirmar() throws IOException {
        var antes = contarFicheros();
        var subido = documentoService.subir(new SubidaForm(List.of(), null, null),
                List.of(Muestras.jpeg("para-borrar.jpg"))).subidos().getFirst();
        TestTransaction.flagForCommit();
        TestTransaction.end();

        TestTransaction.start();
        documentoService.eliminar(subido.id());
        assertThat(contarFicheros()).as("aún no se ha confirmado").isEqualTo(antes + 1);
        TestTransaction.flagForCommit();
        TestTransaction.end();

        assertThat(contarFicheros()).isEqualTo(antes);
        TestTransaction.start();
        assertThat(documentoService.buscar("para-borrar")).isEmpty();
    }

    private static long contarFicheros() throws IOException {
        if (!Files.exists(CARPETA_FICHEROS)) {
            return 0;
        }
        try (var ficheros = Files.walk(CARPETA_FICHEROS)) {
            return ficheros.filter(Files::isRegularFile).count();
        }
    }
}
