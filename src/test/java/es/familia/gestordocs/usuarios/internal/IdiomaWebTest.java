package es.familia.gestordocs.usuarios.internal;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import es.familia.gestordocs.*;
import es.familia.gestordocs.compartido.Mensaje;
import es.familia.gestordocs.documentos.*;
import es.familia.gestordocs.escaneo.*;
import es.familia.gestordocs.personas.*;
import es.familia.gestordocs.secciones.*;
import es.familia.gestordocs.usuarios.*;

import jakarta.servlet.http.Cookie;

@TestIntegracion
class IdiomaWebTest {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository cuentas;
    @Autowired UsuarioService usuarios;
    @Autowired SesionDePrueba sesion;
    @Autowired PersonaService personas;
    @Autowired SeccionService secciones;
    @Autowired DocumentoService documentos;
    @Autowired BorradorEscaneoService borradores;
    @MockitoBean EscaneoService escaneo;
    UsuarioAutenticado ingles;
    UsuarioAutenticado espanol;

    @BeforeEach
    void preparar() {
        ingles = sesion.iniciar("english-test", Rol.ADMIN);
        espanol = sesion.iniciar("espanol-test", Rol.USER);
        sesion.cerrar();
    }

    @AfterEach
    void salir() { sesion.cerrar(); }

    @ParameterizedTest
    @CsvSource(value = {"en-GB|en", "es-MX|es", "fr-FR, en-GB;q=0.8, es;q=0.5|en",
            "en;q=0,es;q=1|es", "de-DE|es", "en;q=incorrecto|es"}, delimiter = '|')
    void primeraVisitaRespetaIdiomaDelNavegadorConAlternativaEspanola(String cabecera, String idioma) throws Exception {
        mvc.perform(get("/login").header("Accept-Language", cabecera))
                .andExpect(content().string(containsString("lang=\"" + idioma + "\"")));
    }

    @Test
    void sinPreferenciasSeUsaEspanolYUnaCookieInvalidaNoOcultaElNavegador() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(content().string(containsString("lang=\"es\"")));
        mvc.perform(get("/login").cookie(new Cookie("idioma", "xx")).header("Accept-Language", "en-GB"))
                .andExpect(content().string(containsString("lang=\"en\"")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"es", "en"})
    void seleccionManualPrevaleceSobreElIdiomaDelNavegador(String idioma) throws Exception {
        mvc.perform(get("/login").cookie(new Cookie("idioma", idioma))
                        .header("Accept-Language", "es".equals(idioma) ? "en-GB" : "es-ES"))
                .andExpect(content().string(containsString("lang=\"" + idioma + "\"")));
    }

    @Test
    void visitantePuedeCargarElScriptDelSelectorSinAccederAlDeSubidas() throws Exception {
        mvc.perform(get("/js/interfaz.js"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("javascript")));
        mvc.perform(get("/js/subida-documentos.js"))
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void seleccionAnonimaRequierePostCsrfYCodigoValido() throws Exception {
        mvc.perform(post("/idioma").param("idioma", "en")).andExpect(status().isForbidden());
        mvc.perform(post("/idioma").with(csrf()).param("idioma", "xx")).andExpect(status().isBadRequest());
        mvc.perform(post("/idioma").with(csrf()).param("idioma", "en").param("volver", "/buscar?q=Mar%C3%ADa"))
                .andExpect(redirectedUrl("/buscar?q=Mar%C3%ADa"))
                .andExpect(cookie().value("idioma", "en"))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")));
        mvc.perform(get("/idioma").with(user(ingles)).param("idioma", "es"))
                .andExpect(status().isMethodNotAllowed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.org", "//example.org", "/%2fexample.org", "/%5cexample.org",
            "/%0d%0aLocation:evil", "relative", "javascript:alert(1)", "/#fragment"})
    void rechazaRetornosExternosOMalformados(String retorno) throws Exception {
        mvc.perform(post("/idioma").with(csrf()).param("idioma", "en").param("volver", retorno))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void unaMismaCuentaTieneIdiomasIndependientesEnDosNavegadores() throws Exception {
        mvc.perform(get("/").with(user(ingles)).cookie(new Cookie("idioma", "en")))
                .andExpect(content().string(containsString("lang=\"en\"")))
                .andExpect(content().string(containsString("Recently uploaded")));
        mvc.perform(get("/").with(user(ingles)).cookie(new Cookie("idioma", "es")))
                .andExpect(content().string(containsString("lang=\"es\"")))
                .andExpect(content().string(containsString("Subidos recientemente")));
        mvc.perform(get("/").with(user(espanol)).cookie(new Cookie("idioma", "en")))
                .andExpect(content().string(containsString("lang=\"en\"")));
        mvc.perform(get("/").with(user(ingles)).header("Accept-Language", "en-GB"))
                .andExpect(content().string(containsString("lang=\"en\"")));
    }

    @Test
    void selectorSoloEscribeCookieSinModificarNingunaCuenta() throws Exception {
        var cuenta = cuentas.findById(espanol.getId()).orElseThrow();
        var antes = cuenta.toDto();
        var hash = cuenta.getPasswordHash();
        var otra = cuentas.findById(ingles.getId()).orElseThrow().toDto();
        mvc.perform(post("/idioma").with(user(espanol)).with(csrf())
                        .param("idioma", "en").param("id", ingles.getId().toString()).param("rol", "ADMIN"))
                .andExpect(redirectedUrl("/"))
                .andExpect(cookie().value("idioma", "en"));
        cuentas.flush();
        assertThat(cuenta.toDto()).isEqualTo(antes);
        assertThat(cuenta.getPasswordHash()).isEqualTo(hash);
        assertThat(cuentas.findById(ingles.getId()).orElseThrow().toDto()).isEqualTo(otra);
    }

    @Test
    void formulariosDeUsuariosNoOfrecenIdiomaPorCuenta() throws Exception {
        for (var ruta : List.of("/admin/usuarios/nuevo", "/admin/usuarios/" + espanol.getId() + "/editar")) {
            var html = mvc.perform(get(ruta).with(user(ingles)).cookie(new Cookie("idioma", "en")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(html).contains("data-selector-idioma").doesNotContain("id=\"idioma\"");
        }
    }

    @Test
    void fragmentosHtmxUsanLaPreferenciaDelNavegador() throws Exception {
        for (var entrada : java.util.Map.of("es", "0 resultados", "en", "0 results").entrySet()) {
            mvc.perform(get("/buscar?q=inexistente").header("HX-Request", "true").with(user(ingles))
                            .cookie(new Cookie("idioma", entrada.getKey())))
                    .andExpect(content().string(containsString(entrada.getValue())))
                    .andExpect(content().string(org.hamcrest.Matchers.not(containsString("<nav"))));
        }
    }

    @Test
    void idiomaDisponibleDuranteCambioObligatorioSinAbrirOtrasRutas() throws Exception {
        var cuenta = cuentas.findById(ingles.getId()).orElseThrow();
        cuenta.resetearPassword(cuenta.getPasswordHash());
        mvc.perform(get("/cuenta/password").with(user(ingles)).cookie(new Cookie("idioma", "en")))
                .andExpect(content().string(containsString("Choose a new password before continuing.")));
        mvc.perform(post("/idioma").with(user(ingles)).cookie(new Cookie("idioma", "en")).with(csrf()).param("idioma", "es"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/").with(user(ingles)).cookie(new Cookie("idioma", "en"))).andExpect(redirectedUrl("/cuenta/password"));
        mvc.perform(get("/js/interfaz.js").with(user(ingles)).cookie(new Cookie("idioma", "en")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("data")));
        mvc.perform(post("/idioma/otra").with(user(ingles)).cookie(new Cookie("idioma", "en")).with(csrf()))
                .andExpect(redirectedUrl("/cuenta/password"));
    }

    @Test
    void loginRecordarmeYLogoutMantienenLaPreferenciaDelNavegador() throws Exception {
        var login = mvc.perform(post("/login").with(csrf()).param("username", "english-test")
                        .param("password", "password-de-prueba").param("recordarme", "on"))
                .andExpect(redirectedUrl("/")).andReturn();
        var session = (MockHttpSession) login.getRequest().getSession(false);
        mvc.perform(get("/").session(session).cookie(new Cookie("idioma", "en")))
                .andExpect(content().string(containsString("lang=\"en\"")));
        var recordar = login.getResponse().getCookie("remember-me");
        assertThat(recordar).isNotNull();
        mvc.perform(get("/").cookie(recordar, new Cookie("idioma", "es")).header("Accept-Language", "en-GB"))
                .andExpect(content().string(containsString("lang=\"es\"")));
        mvc.perform(post("/logout").session(session).with(csrf())).andExpect(redirectedUrl("/login?salir"));
        mvc.perform(get("/login?salir").cookie(new Cookie("idioma", "en")))
                .andExpect(content().string(containsString("You have signed out.")));
    }

    @Test
    void beanValidationYReglasNegocioSeTraducen() throws Exception {
        mvc.perform(post("/cuenta/password").with(user(ingles)).cookie(new Cookie("idioma", "en")).with(csrf())
                        .param("actual", "").param("nueva", "a").param("repetida", "b"))
                .andExpect(content().string(containsString("Enter your current password")))
                .andExpect(content().string(containsString("Must contain at least 8 characters")))
                .andExpect(content().string(containsString("The passwords do not match")));
        mvc.perform(post("/cuenta/password").with(user(ingles)).cookie(new Cookie("idioma", "en")).with(csrf())
                        .param("actual", "incorrecta").param("nueva", "nueva-clave-123").param("repetida", "nueva-clave-123"))
                .andExpect(content().string(containsString("The current password is incorrect.")));
        mvc.perform(post("/admin/personas/nueva").with(user(ingles)).cookie(new Cookie("idioma", "en")).with(csrf()).param("nombre", ""))
                .andExpect(content().string(containsString("Name is required")));
    }

    @Test
    void flashSeResuelveConIdiomaAlMostrarYLosDatosSeEscapan() throws Exception {
        mvc.perform(get("/").with(user(ingles)).cookie(new Cookie("idioma", "en")).flashAttr("mensaje", Mensaje.de("documentos.borrado", "María <script>\"'")))
                .andExpect(content().string(containsString("Document “María &lt;script&gt;")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("<script>\"'"))));
    }

    @Test
    void escaneoConservaEstadosYCamposJsonConTextoTraducido() throws Exception {
        mvc.perform(post("/documentos/escaneos/" + UUID.randomUUID() + "/filtro")
                        .with(user(ingles)).cookie(new Cookie("idioma", "en")).with(csrf()).param("variante", "bw"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("The pending photo does not exist or has expired. Select it again."));
        var datos = "{\"fotos\":[],\"seccionIds\":[]}";
        mvc.perform(multipart("/documentos/escaneos/guardar").file(json(datos))
                        .file(new MockMultipartFile("ficheros", "vacío.pdf", "application/pdf", new byte[0]))
                        .with(user(ingles)).cookie(new Cookie("idioma", "en")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subidos").isEmpty())
                .andExpect(jsonPath("$.errores[0]").value("vacío.pdf: the file is empty."))
                .andExpect(jsonPath("$.avisos").isArray());
        mvc.perform(multipart("/documentos/escaneos/guardar").file(json("{\"descripcion\":\"" + "x".repeat(2001) + "\",\"fotos\":[]}"))
                        .with(user(ingles)).cookie(new Cookie("idioma", "en")).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").value(containsString("Check the details")));
    }

    @Test
    void erroresHtmlSeTraducenSinDetallesTecnicos() throws Exception {
        for (var entrada : java.util.Map.of(400, "Check the details", 403, "Permission denied", 404, "Not found",
                413, "File too large", 500, "Something went wrong").entrySet()) {
            mvc.perform(get("/error").accept(org.springframework.http.MediaType.TEXT_HTML).with(user(ingles)).cookie(new Cookie("idioma", "en"))
                            .requestAttr(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE, entrada.getKey()))
                    .andExpect(content().string(containsString(entrada.getValue())))
                    .andExpect(content().string(org.hamcrest.Matchers.not(containsString("??"))));
        }
    }

    @Test
    void respuestaParcialMantieneSenalDeCaducidadIndependienteDelTexto() throws Exception {
        var valor = "{\"fotos\":[{\"id\":\"" + UUID.randomUUID() + "\",\"variante\":\"fuente\"}]}";
        mvc.perform(multipart("/documentos/escaneos/guardar").file(json(valor)).with(user(ingles)).cookie(new Cookie("idioma", "en")).with(csrf()))
                .andExpect(status().isOk()).andExpect(header().string("X-Borrador-Caducado", "true"))
                .andExpect(jsonPath("$.errores[0]").value(containsString("Pending photo:")));
    }

    @Test
    void erroresEscanerNoRevelanCausaYSeTraducen() throws Exception {
        sesion.iniciar("english-test", Rol.ADMIN);
        var id = borradores.crear(Muestras.png("foto.png")).id();
        try {
            when(escaneo.escanear(any(), anyString(), any(), anyBoolean()))
                    .thenThrow(new EscaneoException("escaneo.conexion", new java.io.IOException("ruta-secreta")));
            mvc.perform(post("/documentos/escaneos/" + id + "/filtro").param("variante", "bw")
                            .with(user(ingles)).cookie(new Cookie("idioma", "en")).with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value(containsString("Could not contact the scanner")))
                    .andExpect(content().string(org.hamcrest.Matchers.not(containsString("ruta-secreta"))));
        } finally {
            sesion.iniciar("english-test", Rol.ADMIN);
            borradores.descartar(id);
        }
    }

    private MockMultipartFile json(String valor) {
        return new MockMultipartFile("datos", "datos.json", "application/json", valor.getBytes(StandardCharsets.UTF_8));
    }
}
