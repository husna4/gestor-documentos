package es.familia.gestordocs.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import es.familia.gestordocs.Muestras;
import es.familia.gestordocs.SesionDePrueba;
import es.familia.gestordocs.TestIntegracion;
import es.familia.gestordocs.compartido.NoEncontradoException;
import es.familia.gestordocs.compartido.ReglaNegocioException;
import es.familia.gestordocs.documentos.DocumentoService;
import es.familia.gestordocs.documentos.SubidaForm;
import es.familia.gestordocs.personas.PersonaForm;
import es.familia.gestordocs.personas.PersonaService;
import es.familia.gestordocs.secciones.SeccionForm;
import es.familia.gestordocs.secciones.SeccionService;
import es.familia.gestordocs.usuarios.NuevoUsuarioForm;
import es.familia.gestordocs.usuarios.Rol;
import es.familia.gestordocs.usuarios.UsuarioService;

/** Reglas que cruzan módulos: qué pasa con los documentos al borrar personas y secciones. */
@TestIntegracion
class BorradosTest {

    @Autowired
    PersonaService personaService;

    @Autowired
    SeccionService seccionService;

    @Autowired
    DocumentoService documentoService;

    @Autowired
    UsuarioService usuarioService;

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
    void alBorrarUnaPersonaSusDocumentosQuedanSinPersonaYSuUsuarioDesvinculado() {
        var dni = seccionService.crear(new SeccionForm("DNI", null));
        var lucia = personaService.crear(new PersonaForm("Lucía", null, null));
        var documento = documentoService.subir(new SubidaForm(List.of(dni.id()), lucia.id(), null),
                List.of(Muestras.jpeg("dni-lucia.jpg"))).subidos().getFirst();
        var usuario = usuarioService.crear(new NuevoUsuarioForm("lucia", "temporal123", Rol.USER, lucia.id()));
        assertThat(documentoService.contarPorPersona(lucia.id())).isEqualTo(1);

        personaService.eliminar(lucia.id());

        assertThatThrownBy(() -> personaService.obtener(lucia.id())).isInstanceOf(NoEncontradoException.class);
        var conservado = documentoService.obtener(documento.id());
        assertThat(conservado.persona()).isNull();
        assertThat(conservado.secciones()).extracting("nombre").containsExactly("DNI");
        assertThat(usuarioService.obtener(usuario.id()).personaId()).isNull();
    }

    @Test
    void alBorrarUnaSeccionSusDocumentosSeConservanYLosQueSeQuedanSinNingunaPasanASinClasificar() {
        var dni = seccionService.crear(new SeccionForm("DNI", null));
        var alquiler = seccionService.crear(new SeccionForm("Ayuda alquiler", null));
        var enLasDos = documentoService.subir(new SubidaForm(List.of(dni.id(), alquiler.id()), null, null),
                List.of(Muestras.pdf("dni.pdf"))).subidos().getFirst();
        var soloEnAlquiler = documentoService.subir(new SubidaForm(List.of(alquiler.id()), null, null),
                List.of(Muestras.pdf("nomina.pdf"))).subidos().getFirst();
        var sinClasificarAntes = documentoService.contarSinClasificar();
        assertThat(documentoService.contarPorSeccion(alquiler.id())).isEqualTo(2);

        seccionService.eliminar(alquiler.id());

        assertThat(documentoService.obtener(enLasDos.id()).secciones()).extracting("nombre").containsExactly("DNI");
        assertThat(documentoService.obtener(soloEnAlquiler.id()).secciones()).isEmpty();
        assertThat(documentoService.contarSinClasificar()).isEqualTo(sinClasificarAntes + 1);
    }

    @Test
    void noPuedeHaberDosSeccionesConElMismoNombre() {
        seccionService.crear(new SeccionForm("Facturas", null));

        assertThatThrownBy(() -> seccionService.crear(new SeccionForm(" facturas ", null)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Ya existe una sección con ese nombre.");
    }

    @Test
    void lasPersonasSeListanPorNombre() {
        personaService.crear(new PersonaForm("Zoe", null, null));
        personaService.crear(new PersonaForm("Ana", "García", "Notas"));

        var nombres = personaService.listar().stream().map(p -> p.nombreCompleto()).toList();
        assertThat(nombres).containsSubsequence("Ana García", "Zoe");
    }
}
