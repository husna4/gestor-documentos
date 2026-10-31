package es.familia.gestordocs.usuarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import es.familia.gestordocs.SesionDePrueba;
import es.familia.gestordocs.TestIntegracion;
import es.familia.gestordocs.compartido.ReglaNegocioException;
import es.familia.gestordocs.usuarios.internal.UsuarioAutenticado;

@TestIntegracion
class UsuarioServiceTest {

    @Autowired
    UsuarioService usuarioService;

    @Autowired
    SesionDePrueba sesion;

    UsuarioAutenticado admin;

    @BeforeEach
    void entrarComoAdmin() {
        admin = sesion.comoAdmin();
    }

    @AfterEach
    void salir() {
        sesion.cerrar();
    }

    @Test
    void unUsuarioNuevoQuedaEnMinusculasYDebeCambiarLaPassword() {
        var creado = usuarioService.crear(new NuevoUsuarioForm("  Abuela ", "temporal123", Rol.USER, null));

        assertThat(creado.username()).isEqualTo("abuela");
        assertThat(creado.rol()).isEqualTo(Rol.USER);
        assertThat(creado.activo()).isTrue();
        assertThat(creado.debeCambiarPassword()).isTrue();
    }

    @Test
    void noSePuedenRepetirUsernames() {
        usuarioService.crear(new NuevoUsuarioForm("pepe", "temporal123", Rol.USER, null));

        assertThatThrownBy(() -> usuarioService.crear(new NuevoUsuarioForm("PEPE", "temporal123", Rol.USER, null)))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void elAdminNoPuedeQuitarseElRolNiDesactivarseNiBorrarse() {
        var id = admin.getId();

        assertThatThrownBy(() -> usuarioService.actualizar(id, new EdicionUsuarioForm(Rol.USER, true, null)))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> usuarioService.actualizar(id, new EdicionUsuarioForm(Rol.ADMIN, false, null)))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> usuarioService.eliminar(id))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void desactivarOtroUsuario() {
        var pepe = usuarioService.crear(new NuevoUsuarioForm("pepe", "temporal123", Rol.USER, null));

        var editado = usuarioService.actualizar(pepe.id(), new EdicionUsuarioForm(Rol.USER, false, null));

        assertThat(editado.activo()).isFalse();
    }

    @Test
    void resetearLaPasswordObligaACambiarla() {
        var pepe = usuarioService.crear(new NuevoUsuarioForm("pepe", "temporal123", Rol.USER, null));
        usuarioService.actualizar(pepe.id(), new EdicionUsuarioForm(Rol.USER, true, null));

        usuarioService.resetearPassword(pepe.id(), "otra-temporal");

        assertThat(usuarioService.obtener(pepe.id()).debeCambiarPassword()).isTrue();
        assertThatThrownBy(() -> usuarioService.resetearPassword(pepe.id(), "corta"))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void noSePuedeVincularAUnaPersonaQueNoExiste() {
        assertThatThrownBy(() -> usuarioService.crear(new NuevoUsuarioForm("pepe", "temporal123", Rol.USER, 999_999L)))
                .isInstanceOf(ReglaNegocioException.class);
    }
}
