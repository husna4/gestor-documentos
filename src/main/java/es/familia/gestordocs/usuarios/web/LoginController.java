package es.familia.gestordocs.usuarios.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Solo muestra el formulario: el POST /login lo procesa Spring Security. */
@Controller
class LoginController {

    @GetMapping("/login")
    String login() {
        return "login";
    }
}
