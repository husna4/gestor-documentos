package es.familia.gestordocs.usuarios.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import es.familia.gestordocs.TestIntegracion;
import es.familia.gestordocs.usuarios.Rol;

@TestIntegracion
class LoginTest {

    private static final String PASSWORD = "secreta123";

    @Autowired
    MockMvc mvc;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void sinSesionRedirigeAlLogin() throws Exception {
        mvc.perform(get("/"))
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void loginCorrectoSinDistinguirMayusculasEnElUsuario() throws Exception {
        crearUsuario("pepe", true, false);

        mvc.perform(formLogin().user("Pepe").password(PASSWORD))
                .andExpect(authenticated().withUsername("pepe").withRoles("USER"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void passwordIncorrectaNoEntra() throws Exception {
        crearUsuario("pepe", true, false);

        mvc.perform(formLogin().user("pepe").password("otra-cosa"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void usuarioInactivoNoEntra() throws Exception {
        crearUsuario("pepe", false, false);

        mvc.perform(formLogin().user("pepe").password(PASSWORD))
                .andExpect(unauthenticated());
    }

    @Test
    void usuarioDesactivadoConSesionAbiertaEsExpulsado() throws Exception {
        var pepe = crearUsuario("pepe", true, false);
        var sesion = new UsuarioAutenticado(pepe);
        pepe.setActivo(false);
        usuarios.flush();

        mvc.perform(get("/").with(user(sesion)))
                .andExpect(redirectedUrl("/login?desactivado"));
    }

    @Test
    void siDebeCambiarLaPasswordSoloPuedeIrALaPantallaDeCambio() throws Exception {
        var pepe = crearUsuario("pepe", true, true);

        mvc.perform(get("/").with(user(new UsuarioAutenticado(pepe))))
                .andExpect(redirectedUrl("/cuenta/password"));
        mvc.perform(get("/cuenta/password").with(user(new UsuarioAutenticado(pepe))))
                .andExpect(status().isOk());
    }

    @Test
    void cambiarLaPasswordQuitaLaObligacion() throws Exception {
        var pepe = crearUsuario("pepe", true, true);

        mvc.perform(post("/cuenta/password").with(user(new UsuarioAutenticado(pepe))).with(csrf())
                        .param("actual", PASSWORD)
                        .param("nueva", "nuevaClave99")
                        .param("repetida", "nuevaClave99"))
                .andExpect(redirectedUrl("/"));

        var guardado = usuarios.findById(pepe.getId()).orElseThrow();
        assertThat(guardado.isDebeCambiarPassword()).isFalse();
        assertThat(passwordEncoder.matches("nuevaClave99", guardado.getPasswordHash())).isTrue();
    }

    @Test
    void cambiarLaPasswordConLaActualIncorrectaMuestraError() throws Exception {
        var pepe = crearUsuario("pepe", true, false);

        mvc.perform(post("/cuenta/password").with(user(new UsuarioAutenticado(pepe))).with(csrf())
                        .param("actual", "no-es-esta")
                        .param("nueva", "nuevaClave99")
                        .param("repetida", "nuevaClave99"))
                .andExpect(status().isOk())
                .andExpect(model().hasErrors());
    }

    @Test
    void lasPasswordsNuevasDebenCoincidir() throws Exception {
        var pepe = crearUsuario("pepe", true, false);

        mvc.perform(post("/cuenta/password").with(user(new UsuarioAutenticado(pepe))).with(csrf())
                        .param("actual", PASSWORD)
                        .param("nueva", "nuevaClave99")
                        .param("repetida", "nuevaClave98"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "repetida"));
    }

    @Test
    void unPostSinTokenCsrfSeRechaza() throws Exception {
        var pepe = crearUsuario("pepe", true, false);

        mvc.perform(post("/cuenta/password").with(user(new UsuarioAutenticado(pepe)))
                        .param("actual", PASSWORD)
                        .param("nueva", "nuevaClave99")
                        .param("repetida", "nuevaClave99"))
                .andExpect(status().isForbidden());
    }

    private Usuario crearUsuario(String username, boolean activo, boolean debeCambiarPassword) {
        var usuario = new Usuario(username, passwordEncoder.encode(PASSWORD), Rol.USER, debeCambiarPassword);
        usuario.setActivo(activo);
        return usuarios.saveAndFlush(usuario);
    }
}
