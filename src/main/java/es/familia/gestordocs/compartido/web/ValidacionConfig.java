package es.familia.gestordocs.compartido.web;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class ValidacionConfig implements WebMvcConfigurer {
    private final MessageSource mensajes;

    ValidacionConfig(MessageSource mensajes) { this.mensajes = mensajes; }

    @Bean
    public LocalValidatorFactoryBean validator() {
        var validator = new LocalValidatorFactoryBean();
        validator.setValidationMessageSource(mensajes);
        return validator;
    }

    @Override
    public org.springframework.validation.Validator getValidator() { return validator(); }
}
