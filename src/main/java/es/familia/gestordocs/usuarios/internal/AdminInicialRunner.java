package es.familia.gestordocs.usuarios.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import es.familia.gestordocs.usuarios.Rol;

/**
 * Al arrancar, si la tabla de usuarios está vacía, crea el administrador inicial con las
 * credenciales de {@code app.admin.*} (variables ADMIN_USERNAME y ADMIN_PASSWORD).
 * Se le obliga a cambiar la contraseña en su primer login.
 */
@Component
class AdminInicialRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInicialRunner.class);

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final AdminInicialProperties propiedades;

    AdminInicialRunner(UsuarioRepository usuarios, PasswordEncoder passwordEncoder,
            AdminInicialProperties propiedades) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.propiedades = propiedades;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (usuarios.count() > 0) {
            return;
        }
        if (!StringUtils.hasText(propiedades.username()) || !StringUtils.hasText(propiedades.password())) {
            throw new IllegalStateException(
                    "No hay ningún usuario en la base de datos y no se ha configurado el administrador inicial. "
                            + "Define las variables de entorno ADMIN_USERNAME y ADMIN_PASSWORD y vuelve a arrancar.");
        }
        var admin = new Usuario(propiedades.username(), passwordEncoder.encode(propiedades.password()),
                Rol.ADMIN, true);
        usuarios.save(admin);
        log.info("Creado el administrador inicial '{}'. Deberá cambiar la contraseña al entrar.", admin.getUsername());
    }
}
