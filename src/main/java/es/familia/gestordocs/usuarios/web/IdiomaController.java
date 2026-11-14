package es.familia.gestordocs.usuarios.web;

import java.net.URI;
import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import es.familia.gestordocs.usuarios.Idioma;
import es.familia.gestordocs.usuarios.UsuarioService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
class IdiomaController {
    private final UsuarioService usuarios;

    IdiomaController(UsuarioService usuarios) { this.usuarios = usuarios; }

    @PostMapping("/idioma")
    String cambiar(@RequestParam Idioma idioma, @RequestParam(defaultValue = "/") String volver,
            HttpServletRequest request, HttpServletResponse response) {
        if (usuarios.idUsuarioActual().isPresent()) usuarios.cambiarIdiomaPropio(idioma);
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("idioma", idioma.name())
                .path(request.getContextPath().isEmpty() ? "/" : request.getContextPath())
                .httpOnly(true).secure(request.isSecure()).sameSite("Lax")
                .maxAge(Duration.ofDays(365)).build().toString());
        return "redirect:" + destinoSeguro(volver);
    }

    static String destinoSeguro(String ruta) {
        try {
            var uri = URI.create(ruta);
            var path = uri.getPath();
            if (uri.isAbsolute() || uri.getRawAuthority() != null || uri.getFragment() != null
                    || path == null || !path.startsWith("/") || path.startsWith("//")
                    || ruta.contains("\\") || path.contains("\\")
                    || ruta.chars().anyMatch(Character::isISOControl)
                    || path.chars().anyMatch(Character::isISOControl)) return "/";
            return ruta;
        } catch (IllegalArgumentException e) { return "/"; }
    }
}
