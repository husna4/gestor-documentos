package es.familia.gestordocs.documentos;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import es.familia.gestordocs.*;
import es.familia.gestordocs.compartido.NoEncontradoException;
import es.familia.gestordocs.escaneo.*;
import es.familia.gestordocs.usuarios.Rol;
import es.familia.gestordocs.usuarios.internal.UsuarioAutenticado;

@TestIntegracion
class BorradoresWebTest {
    @Autowired BorradorEscaneoService borradores;
    @Autowired DocumentoService documentos;
    @Autowired SesionDePrueba sesion;
    @Autowired MockMvc mvc;
    @MockitoBean EscaneoService escaneo;
    UsuarioAutenticado admin;
    List<UUID> creados = new ArrayList<>();
    byte[] jpeg;

    @BeforeEach
    void entrar() throws Exception {
        admin = sesion.comoAdmin();
        jpeg = Muestras.jpeg("vista.jpg").contenido().getInputStream().readAllBytes();
        when(escaneo.escanear(any(), anyString(), any(), anyBoolean()))
                .thenReturn(new ResultadoEscaneo(jpeg, true));
    }

    @AfterEach
    void limpiar() {
        if (TestTransaction.isActive()) {
            TestTransaction.flagForRollback(); TestTransaction.end();
        }
        TestTransaction.start();
        autenticarAdmin();
        for (var id : creados) {
            try { borradores.descartar(id); } catch (NoEncontradoException ignored) { }
        }
        sesion.cerrar();
    }

    void autenticarAdmin() {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                        admin, null, admin.getAuthorities()));
    }

    UUID crear() {
        var id = borradores.crear(Muestras.png("foto.png")).id();
        creados.add(id);
        return id;
    }

    MockMultipartFile solicitud(UUID id, String variante) {
        var json = "{\"seccionIds\":[],\"descripcion\":\"Prueba\",\"fotos\":[{\"id\":\""
                + id + "\",\"variante\":\"" + variante + "\"}]}";
        return new MockMultipartFile("datos", "datos.json", "application/json", json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void crearExigeAdminYCsrfYValidaElContenidoReal() throws Exception {
        var file = new MockMultipartFile("file", "foto.png", "image/png",
                Muestras.png("foto.png").contenido().getInputStream().readAllBytes());
        mvc.perform(multipart("/documentos/escaneos").file(file).with(user(admin)))
                .andExpect(status().isForbidden());
        var consulta = sesion.comoUsuario();
        mvc.perform(multipart("/documentos/escaneos").file(file).with(user(consulta)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/documentos/escaneos/" + UUID.randomUUID() + "/imagen")
                .param("variante", "fuente").with(user(consulta))).andExpect(status().isForbidden());
        mvc.perform(multipart("/documentos/escaneos")
                .file(new MockMultipartFile("file", "foto.jpg", "image/jpeg", "texto".getBytes()))
                .with(user(admin)).with(csrf())).andExpect(status().isBadRequest());
    }

    @Test
    void filtroYVistaNoPermitenAccesoDeOtroAdministradorNiUsanCachePublica() throws Exception {
        var id = crear();
        mvc.perform(post("/documentos/escaneos/" + id + "/filtro").param("variante", "bw")
                .with(user(admin)).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.variante").value("bw"));
        mvc.perform(get("/documentos/escaneos/" + id + "/imagen").param("variante", "bw").with(user(admin)))
                .andExpect(status().isOk()).andExpect(content().bytes(jpeg))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        var otro = sesion.iniciar("otro-admin-borradores", Rol.ADMIN);
        mvc.perform(get("/documentos/escaneos/" + id + "/imagen").param("variante", "bw").with(user(otro)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/documentos/escaneos/" + id).with(user(otro)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(multipart("/documentos/escaneos/guardar").file(solicitud(id, "bw"))
                .with(user(otro)).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.subidos").isEmpty());
    }

    @Test
    void guardaExactamenteLaVistaSinVolverAEscanearYRollbackConservaElTemporal() throws Exception {
        var id = crear(); borradores.filtrar(id, "bw");
        mvc.perform(multipart("/documentos/escaneos/guardar").file(solicitud(id, "bw"))
                .with(user(admin)).with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$.errores").isEmpty()).andExpect(jsonPath("$.subidos[0].nombreOriginal").value("foto.jpg"));
        sesion.comoAdmin();
        var doc = documentos.buscar("foto").stream().filter(d -> d.descripcion().equals("Prueba")).findFirst().orElseThrow();
        assertThat(documentos.contenido(doc.id()).recurso().getContentAsByteArray()).isEqualTo(jpeg);
        assertThat(Path.of("target/borradores-test", id.toString())).exists();
        TestTransaction.flagForRollback(); TestTransaction.end(); TestTransaction.start();
        autenticarAdmin();
        assertThat(borradores.imagen(id, "bw").contenido()).isEqualTo(jpeg);
        verify(escaneo, times(1)).escanear(any(), anyString(), any(), anyBoolean());
    }

    @Test
    void trasCommitSeEliminanFuenteYTodasLasVariantes() {
        // Usar el administrador persistente creado por el perfil de tests, sin dejar usuarios nuevos.
        TestTransaction.flagForRollback(); TestTransaction.end(); TestTransaction.start();
        admin = sesion.iniciar("admin", Rol.ADMIN);
        var id = crear(); borradores.filtrar(id, "bw"); borradores.filtrar(id, "enhanced");
        Long docId = null;
        try {
            var resultado = documentos.subir(new SubidaForm(List.of(), null, "Borrador commit"),
                    List.of(), List.of(new SeleccionBorrador(id, "bw")));
            docId = resultado.subidos().getFirst().id();
            TestTransaction.flagForCommit(); TestTransaction.end();
            assertThat(Path.of("target/borradores-test", id.toString())).doesNotExist();
            assertThatThrownBy(() -> borradores.imagen(id, "bw")).isInstanceOf(NoEncontradoException.class);
        } finally {
            if (!TestTransaction.isActive()) TestTransaction.start();
            sesion.iniciar("admin", Rol.ADMIN);
            if (docId != null) documentos.eliminar(docId);
            TestTransaction.flagForCommit(); TestTransaction.end(); TestTransaction.start();
        }
    }

    @Test
    void descripcionInvalidaYFiltroNoVistoConservanElBorrador() throws Exception {
        var id = crear();
        mvc.perform(multipart("/documentos/escaneos/guardar").file(solicitud(id, "bw"))
                .with(user(admin)).with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$.subidos").isEmpty()).andExpect(jsonPath("$.errores[0]").exists());
        var json = "{\"descripcion\":\"" + "x".repeat(2001) + "\",\"fotos\":[]}";
        mvc.perform(multipart("/documentos/escaneos/guardar")
                .file(new MockMultipartFile("datos", "datos.json", "application/json", json.getBytes()))
                .with(user(admin)).with(csrf())).andExpect(status().isBadRequest());
        sesion.comoAdmin();
        assertThat(borradores.imagen(id, "fuente").tipo()).isEqualTo("image/png");
        verifyNoInteractions(escaneo);
    }

    @Test
    void permiteGuardarElOriginalYLosFicherosNoEscaneables() throws Exception {
        var id = crear();
        mvc.perform(multipart("/documentos/escaneos/guardar").file(solicitud(id, "fuente"))
                .file(new MockMultipartFile("ficheros", "contrato.pdf", "application/pdf",
                        Muestras.pdf("contrato.pdf").contenido().getInputStream().readAllBytes()))
                .with(user(admin)).with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$.subidos.length()").value(2)).andExpect(jsonPath("$.errores").isEmpty());
        verifyNoInteractions(escaneo);
    }
}
