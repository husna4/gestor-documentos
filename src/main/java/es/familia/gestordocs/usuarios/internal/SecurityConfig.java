package es.familia.gestordocs.usuarios.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices.RememberMeTokenAlgorithm;

/**
 * Seguridad web: login por formulario, "Recordarme", CSRF (activo por defecto) y reglas por URL.
 * {@code @EnableMethodSecurity} activa además los {@code @PreAuthorize} de los services,
 * que protegen la lógica aunque mañana se llegue a ella por una API REST.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({ RecordarmeProperties.class, AdminInicialProperties.class })
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, TokenBasedRememberMeServices rememberMeServices,
            RecordarmeProperties recordarme, UsuarioRepository usuarios) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/webjars/**", "/css/**", "/favicon.ico", "/error").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/documentos/subir", "/documentos/*/editar", "/documentos/*/borrar")
                        .hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .permitAll())
                .rememberMe(rm -> rm
                        .rememberMeServices(rememberMeServices)
                        .key(recordarme.clave()))
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?salir")
                        .permitAll())
                // El visor de PDF es un <iframe> de nuestra propia web; desde otras webs sigue prohibido.
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .addFilterBefore(new EstadoUsuarioFilter(usuarios, rememberMeServices), AuthorizationFilter.class);
        return http.build();
    }

    @Bean
    TokenBasedRememberMeServices rememberMeServices(UserDetailsService userDetailsService,
            RecordarmeProperties recordarme) {
        var servicio = new TokenBasedRememberMeServices(recordarme.clave(), userDetailsService,
                RememberMeTokenAlgorithm.SHA256);
        servicio.setMatchingAlgorithm(RememberMeTokenAlgorithm.SHA256);
        servicio.setParameter("recordarme");
        servicio.setTokenValiditySeconds((int) recordarme.validez().toSeconds());
        return servicio;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
