package es.familia.gestordocs;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import es.familia.gestordocs.documentos.DocumentoService;
import es.familia.gestordocs.documentos.EdicionDocumentoForm;
import es.familia.gestordocs.documentos.SubidaForm;
import es.familia.gestordocs.personas.PersonaForm;
import es.familia.gestordocs.personas.PersonaService;
import es.familia.gestordocs.secciones.SeccionForm;
import es.familia.gestordocs.secciones.SeccionService;
import es.familia.gestordocs.usuarios.NuevoUsuarioForm;
import es.familia.gestordocs.usuarios.Rol;
import es.familia.gestordocs.usuarios.UsuarioService;

/**
 * Un USER solo puede ver y descargar. Se comprueba en las dos capas: en los services
 * ({@code @PreAuthorize}, que protegerá también una futura API REST) y en las URLs web.
 */
@TestIntegracion
class PermisosTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    DocumentoService documentoService;

    @Autowired
    PersonaService personaService;

    @Autowired
    SeccionService seccionService;

    @Autowired
    UsuarioService usuarioService;

    // ---- Capa de services ----

    @Test
    @WithMockUser(roles = "USER")
    void unUsuarioNoPuedeSubirEditarNiBorrarDocumentos() {
        assertThatThrownBy(() -> documentoService.subir(new SubidaForm(List.of(), null, null),
                List.of(Muestras.pdf("x.pdf")))).isInstanceOf(AuthorizationDeniedException.class);
        assertThatThrownBy(() -> documentoService.editar(1L, new EdicionDocumentoForm("x", null, List.of(), null)))
                .isInstanceOf(AuthorizationDeniedException.class);
        assertThatThrownBy(() -> documentoService.eliminar(1L)).isInstanceOf(AuthorizationDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "USER")
    void unUsuarioNoPuedeGestionarPersonasSeccionesNiUsuarios() {
        assertThatThrownBy(() -> personaService.crear(new PersonaForm("X", null, null)))
                .isInstanceOf(AuthorizationDeniedException.class);
        assertThatThrownBy(() -> personaService.eliminar(1L)).isInstanceOf(AuthorizationDeniedException.class);
        assertThatThrownBy(() -> seccionService.crear(new SeccionForm("X", null)))
                .isInstanceOf(AuthorizationDeniedException.class);
        assertThatThrownBy(() -> seccionService.eliminar(1L)).isInstanceOf(AuthorizationDeniedException.class);
        assertThatThrownBy(() -> usuarioService.listar()).isInstanceOf(AuthorizationDeniedException.class);
        assertThatThrownBy(() -> usuarioService.crear(new NuevoUsuarioForm("x", "12345678", Rol.ADMIN, null)))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    @Test
    void sinSesionNiSiquieraSePuedeConsultar() {
        assertThatThrownBy(() -> documentoService.recientes(5))
                .isInstanceOf(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class);
    }

    // ---- Capa web ----

    @Test
    @WithMockUser(roles = "USER")
    void unUsuarioNoEntraEnLasPantallasDeAdministracion() throws Exception {
        mvc.perform(get("/documentos/subir")).andExpect(status().isForbidden());
        mvc.perform(get("/documentos/1/editar")).andExpect(status().isForbidden());
        mvc.perform(get("/admin/personas")).andExpect(status().isForbidden());
        mvc.perform(get("/admin/secciones/nueva")).andExpect(status().isForbidden());
        mvc.perform(get("/admin/usuarios")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void unUsuarioNoPuedeEnviarSubidasNiBorrados() throws Exception {
        var fichero = new MockMultipartFile("ficheros", "x.pdf", "application/pdf", "%PDF-1.4".getBytes());
        mvc.perform(multipart("/documentos/subir").file(fichero).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/documentos/1/borrar").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/admin/personas/1/borrar").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void unUsuarioSiPuedeVerElInicio() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test
    void lasDescargasExigenSesion() throws Exception {
        mvc.perform(get("/documentos/1/descargar")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/documentos/1/miniatura")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/zip/persona/1")).andExpect(redirectedUrl("/login"));
    }
}
