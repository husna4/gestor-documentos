package es.familia.gestordocs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import es.familia.gestordocs.documentos.DocumentoDto;
import es.familia.gestordocs.documentos.DocumentoService;
import es.familia.gestordocs.documentos.SubidaForm;
import es.familia.gestordocs.personas.PersonaDto;
import es.familia.gestordocs.personas.PersonaForm;
import es.familia.gestordocs.personas.PersonaService;
import es.familia.gestordocs.secciones.SeccionDto;
import es.familia.gestordocs.secciones.SeccionForm;
import es.familia.gestordocs.secciones.SeccionService;
import es.familia.gestordocs.usuarios.internal.UsuarioAutenticado;

/** Renderiza cada pantalla con datos reales: detecta errores en las plantillas Thymeleaf. */
@TestIntegracion
class PantallasTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    SesionDePrueba sesion;

    @Autowired
    SeccionService seccionService;

    @Autowired
    PersonaService personaService;

    @Autowired
    DocumentoService documentoService;

    UsuarioAutenticado admin;
    SeccionDto dni;
    PersonaDto maria;
    DocumentoDto foto;
    DocumentoDto pdf;
    DocumentoDto texto;

    @BeforeEach
    void preparar() {
        admin = sesion.comoAdmin();
        dni = seccionService.crear(new SeccionForm("DNI", "Documentos de identidad"));
        maria = personaService.crear(new PersonaForm("María", "López", "La mayor"));
        var subidos = documentoService.subir(new SubidaForm(List.of(dni.id()), maria.id(), "Escaneado"),
                List.of(Muestras.jpeg("dni-maria.jpg"), Muestras.pdf("contrato.pdf"), Muestras.texto("notas.txt")))
                .subidos();
        foto = subidos.get(0);
        pdf = subidos.get(1);
        texto = subidos.get(2);
        sesion.cerrar();
    }

    @AfterEach
    void salir() {
        sesion.cerrar();
    }

    @Test
    void pantallasDeConsulta() throws Exception {
        pagina("/", "DNI", "María López", "dni-maria");
        pagina("/secciones/" + dni.id(), "Documentos de identidad", "dni-maria", "Descargar todo (ZIP)");
        pagina("/secciones/" + dni.id() + "?persona=" + maria.id(), "dni-maria");
        pagina("/personas/" + maria.id(), "María López", "La mayor", "dni-maria");
        pagina("/sin-clasificar", "Sin clasificar");
        pagina("/buscar?q=maria", "dni-maria", "1 resultado");
        pagina("/buscar", "Escribe para buscar");
        pagina("/documentos/" + foto.id(), "dni-maria", "Escaneado", "visor-imagen");
        pagina("/documentos/" + pdf.id(), "visor-pdf");
        pagina("/documentos/" + texto.id(), "no se puede ver en el navegador");
        pagina("/cuenta/password", "Cambiar contraseña");
    }

    @Test
    void conHtmxSoloSeDevuelveElTrozoDePagina() throws Exception {
        var html = mvc.perform(get("/buscar?q=contrato").header("HX-Request", "true").with(user(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("id=\"resultados\"", "contrato").doesNotContain("<nav");

        var seccion = mvc.perform(get("/secciones/" + dni.id()).header("HX-Request", "true").with(user(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(seccion).contains("id=\"listado\"").doesNotContain("<nav");
    }

    @Test
    void pantallasDeAdministracion() throws Exception {
        pagina("/documentos/subir", "Hacer foto", "capture=\"environment\"", "DNI", "María López");
        pagina("/documentos/" + foto.id() + "/editar", "dni-maria");
        pagina("/admin/personas", "María López");
        pagina("/admin/personas/nueva", "Nueva persona");
        pagina("/admin/personas/" + maria.id() + "/editar", "La mayor");
        pagina("/admin/personas/" + maria.id() + "/borrar", "Tiene 3 documentos", "quedarán sin persona");
        pagina("/admin/secciones", "DNI");
        pagina("/admin/secciones/nueva", "Nueva sección");
        pagina("/admin/secciones/" + dni.id() + "/editar", "Documentos de identidad");
        pagina("/admin/secciones/" + dni.id() + "/borrar", "Sin clasificar");
        pagina("/admin/usuarios", "admin-test");
        pagina("/admin/usuarios/nuevo", "Contraseña temporal");
        pagina("/admin/usuarios/" + admin.getId() + "/editar", "admin-test");
    }

    @Test
    void ficheros() throws Exception {
        mvc.perform(get("/documentos/" + foto.id() + "/ver").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Content-Disposition", containsString("inline")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mvc.perform(get("/documentos/" + texto.id() + "/ver").with(user(admin)))
                .andExpect(header().string("Content-Disposition", containsString("attachment")));
        mvc.perform(get("/documentos/" + pdf.id() + "/descargar").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("contrato.pdf")));
        mvc.perform(get("/documentos/" + foto.id() + "/miniatura").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"));
        mvc.perform(get("/documentos/" + pdf.id() + "/miniatura").with(user(admin)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/zip/persona/" + maria.id()).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"));
        mvc.perform(get("/documentos/999999").with(user(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    void subirDesdeElFormulario() throws Exception {
        var foto = new MockMultipartFile("ficheros", "factura.png", "image/png",
                Muestras.png("factura.png").contenido().getInputStream().readAllBytes());
        var camaraVacia = new MockMultipartFile("ficheros", "", "application/octet-stream", new byte[0]);

        mvc.perform(multipart("/documentos/subir").file(foto).file(camaraVacia)
                        .param("seccionIds", dni.id().toString())
                        .param("personaId", "")
                        .with(user(admin)).with(csrf()))
                .andExpect(redirectedUrl("/documentos/subir"))
                .andExpect(flash().attribute("subidos", List.of("factura")))
                .andExpect(flash().attribute("rechazados", List.of()));
    }

    private void pagina(String url, String... textos) throws Exception {
        var html = mvc.perform(get(url).with(user(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).as(url).contains(textos);
    }
}
