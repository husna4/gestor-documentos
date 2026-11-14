package es.familia.gestordocs.compartido.web;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
class ModeloIdioma {
    @ModelAttribute("volverIdioma")
    String volver(HttpServletRequest request) {
        var ruta = request.getRequestURI().substring(request.getContextPath().length());
        if (!"GET".equals(request.getMethod())) return "/";
        return ruta + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
    }
}
