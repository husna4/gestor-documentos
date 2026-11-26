package es.familia.gestordocs.compartido.web;

import java.util.List;
import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.util.WebUtils;

import es.familia.gestordocs.compartido.Idioma;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Preferencia del navegador, independiente de la autenticación y de las cuentas. */
@Configuration
class IdiomaLocaleResolver {
    private static final List<Locale> DISPONIBLES = List.of(Idioma.es.locale(), Idioma.en.locale());

    @Bean
    LocaleResolver localeResolver() {
        return new LocaleResolver() {
            @Override
            public Locale resolveLocale(HttpServletRequest request) {
                var cookie = WebUtils.getCookie(request, "idioma");
                if (cookie != null) {
                    if ("es".equals(cookie.getValue())) return Idioma.es.locale();
                    if ("en".equals(cookie.getValue())) return Idioma.en.locale();
                }
                var preferencia = request.getHeader(HttpHeaders.ACCEPT_LANGUAGE);
                if (preferencia != null && !preferencia.isBlank()) {
                    try {
                        var locale = Locale.lookup(Locale.LanguageRange.parse(preferencia), DISPONIBLES);
                        if (locale != null) return locale;
                    } catch (IllegalArgumentException ignored) {
                        // Una cabecera malformada nunca altera el idioma por defecto.
                    }
                }
                return Idioma.es.locale();
            }

            @Override
            public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
                throw new UnsupportedOperationException("Usar POST /idioma");
            }
        };
    }
}
