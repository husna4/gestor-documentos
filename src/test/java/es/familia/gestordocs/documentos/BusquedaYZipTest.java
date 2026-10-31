package es.familia.gestordocs.documentos;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import es.familia.gestordocs.Muestras;
import es.familia.gestordocs.SesionDePrueba;
import es.familia.gestordocs.TestIntegracion;
import es.familia.gestordocs.personas.PersonaForm;
import es.familia.gestordocs.personas.PersonaService;
import es.familia.gestordocs.secciones.SeccionForm;
import es.familia.gestordocs.secciones.SeccionService;

@TestIntegracion
class BusquedaYZipTest {

    @Autowired
    DocumentoService documentoService;

    @Autowired
    ZipService zipService;

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
    void laBusquedaNoDistingueMayusculasNiTildesYAceptaTrozos() {
        subir(List.of(), null, Muestras.jpeg("DNI María Pérez.jpg"), Muestras.pdf("Factura luz enero.pdf"),
                Muestras.pdf("Contrato.pdf"));

        assertThat(nombres(documentoService.buscar("maria perez"))).containsExactly("DNI María Pérez");
        assertThat(nombres(documentoService.buscar("FACT"))).containsExactly("Factura luz enero");
        assertThat(nombres(documentoService.buscar("  contrató "))).containsExactly("Contrato");
        assertThat(documentoService.buscar("")).isEmpty();
    }

    @Test
    void losComodinesDeSqlSeBuscanLiteralmente() {
        subir(List.of(), null, Muestras.pdf("Descuento 50% luz.pdf"), Muestras.pdf("Otro documento.pdf"));

        assertThat(nombres(documentoService.buscar("50%"))).containsExactly("Descuento 50% luz");
        assertThat(documentoService.buscar("%")).extracting(DocumentoDto::nombreVisible)
                .containsExactly("Descuento 50% luz");
        assertThat(documentoService.buscar("_")).isEmpty();
    }

    @Test
    void elZipDeUnaPersonaTieneUnaCarpetaPorSeccionYNombresSinRepetir() throws IOException {
        var dni = seccionService.crear(new SeccionForm("DNI", null));
        var alquiler = seccionService.crear(new SeccionForm("Ayuda alquiler", null));
        var maria = personaService.crear(new PersonaForm("María", "López", null));
        subir(List.of(dni.id(), alquiler.id()), maria.id(), Muestras.jpeg("dni.jpg"));
        subir(List.of(dni.id()), maria.id(), Muestras.jpeg("dni.jpg"));
        subir(List.of(), maria.id(), Muestras.pdf("suelto.pdf"));

        assertThat(zipService.nombreZipPersona(maria.id())).isEqualTo("María López.zip");
        var salida = new ByteArrayOutputStream();
        zipService.escribirZipPersona(maria.id(), salida);

        assertThat(entradas(salida.toByteArray())).containsExactlyInAnyOrder(
                "DNI/dni.jpg", "DNI/dni (2).jpg", "Ayuda alquiler/dni.jpg", "Sin clasificar/suelto.pdf");
    }

    @Test
    void elZipDeUnaSeccionPuedeFiltrarsePorPersona() throws IOException {
        var casa = seccionService.crear(new SeccionForm("Casa", null));
        var pepe = personaService.crear(new PersonaForm("Pepe", null, null));
        subir(List.of(casa.id()), null, Muestras.pdf("escritura.pdf"));
        subir(List.of(casa.id()), pepe.id(), Muestras.pdf("seguro.pdf"));

        var todo = new ByteArrayOutputStream();
        zipService.escribirZipSeccion(casa.id(), null, todo);
        var dePepe = new ByteArrayOutputStream();
        zipService.escribirZipSeccion(casa.id(), pepe.id(), dePepe);

        assertThat(entradas(todo.toByteArray())).containsExactlyInAnyOrder("escritura.pdf", "seguro.pdf");
        assertThat(entradas(dePepe.toByteArray())).containsExactly("seguro.pdf");
        assertThat(zipService.nombreZipSeccion(casa.id(), pepe.id())).isEqualTo("Casa - Pepe.zip");
    }

    private void subir(List<Long> secciones, Long personaId, FicheroSubido... ficheros) {
        var resultado = documentoService.subir(new SubidaForm(secciones, personaId, null), List.of(ficheros));
        assertThat(resultado.errores()).isEmpty();
    }

    private static List<String> nombres(List<DocumentoDto> documentos) {
        return documentos.stream().map(DocumentoDto::nombreVisible).toList();
    }

    private static List<String> entradas(byte[] zip) throws IOException {
        var nombres = new ArrayList<String>();
        try (var entrada = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (var e = entrada.getNextEntry(); e != null; e = entrada.getNextEntry()) {
                nombres.add(e.getName());
            }
        }
        return nombres;
    }
}
