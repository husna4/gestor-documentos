package es.familia.gestordocs.usuarios.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import es.familia.gestordocs.TestIntegracion;
import es.familia.gestordocs.usuarios.Rol;

@TestIntegracion
class AdminInicialTest {

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void alArrancarSeCreaElAdminConLasCredencialesDeLaConfiguracion() {
        var admin = usuarios.findByUsername("admin").orElseThrow();

        assertThat(admin.getRol()).isEqualTo(Rol.ADMIN);
        assertThat(admin.isActivo()).isTrue();
        assertThat(admin.isDebeCambiarPassword()).isTrue();
        assertThat(passwordEncoder.matches("admin-de-pruebas", admin.getPasswordHash())).isTrue();
    }

    @Test
    void sinUsuariosYSinCredencialesConfiguradasNoArranca() {
        var repositorioVacio = mock(UsuarioRepository.class);
        when(repositorioVacio.count()).thenReturn(0L);
        var runner = new AdminInicialRunner(repositorioVacio, passwordEncoder, new AdminInicialProperties("", ""));

        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_USERNAME");
    }
}
