package es.familia.gestordocs.almacenamiento.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import es.familia.gestordocs.almacenamiento.AlmacenamientoException;

@ControllerAdvice
class ErroresAlmacenamiento {
    private static final Logger log = LoggerFactory.getLogger(ErroresAlmacenamiento.class);

    @ExceptionHandler(AlmacenamientoException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    String almacenamiento(AlmacenamientoException error, Model model) {
        log.error("Fallo de almacenamiento", error);
        model.addAttribute("status", 500);
        return "error";
    }
}
