package es.familia.gestordocs.documentos.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import es.familia.gestordocs.documentos.ZipService;
import jakarta.servlet.http.HttpServletResponse;

/** Descarga en ZIP de todos los documentos de una persona o de una sección. */
@Controller
@RequestMapping("/zip")
class ZipController {

    private final ZipService zipService;

    ZipController(ZipService zipService) {
        this.zipService = zipService;
    }

    @GetMapping("/persona/{id}")
    void persona(@PathVariable Long id, HttpServletResponse respuesta) throws IOException {
        prepararRespuesta(respuesta, zipService.nombreZipPersona(id));
        zipService.escribirZipPersona(id, respuesta.getOutputStream());
    }

    @GetMapping("/seccion/{id}")
    void seccion(@PathVariable Long id, @RequestParam(required = false) Long persona,
            HttpServletResponse respuesta) throws IOException {
        prepararRespuesta(respuesta, zipService.nombreZipSeccion(id, persona));
        zipService.escribirZipSeccion(id, persona, respuesta.getOutputStream());
    }

    private static void prepararRespuesta(HttpServletResponse respuesta, String nombre) {
        respuesta.setContentType("application/zip");
        respuesta.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(nombre, StandardCharsets.UTF_8).build().toString());
        respuesta.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }
}
