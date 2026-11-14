package es.familia.gestordocs.usuarios.internal;

import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.util.WebUtils;

import es.familia.gestordocs.usuarios.Idioma;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
class IdiomaLocaleResolver {
    static final String ATRIBUTO = IdiomaLocaleResolver.class.getName() + ".idioma";

    @Bean
    LocaleResolver localeResolver() {
        return new LocaleResolver() {
            @Override
            public Locale resolveLocale(HttpServletRequest request) {
                if (request.getAttribute(ATRIBUTO) instanceof Idioma idioma) return idioma.locale();
                var cookie = WebUtils.getCookie(request, "idioma");
                if (cookie != null && "en".equals(cookie.getValue())) return Idioma.en.locale();
                return Idioma.es.locale();
            }

            @Override
            public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
                throw new UnsupportedOperationException("Usar POST /idioma");
            }
        };
    }
}
