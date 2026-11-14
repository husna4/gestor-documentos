package es.familia.gestordocs.documentos;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import es.familia.gestordocs.Muestras;
import es.familia.gestordocs.compartido.NoEncontradoException;
import es.familia.gestordocs.compartido.ReglaNegocioException;
import es.familia.gestordocs.documentos.internal.*;
import es.familia.gestordocs.escaneo.*;
import es.familia.gestordocs.usuarios.UsuarioService;

class BorradorEscaneoServiceTest {
    @TempDir Path carpeta;
    EscaneoService escaneo = mock(EscaneoService.class);
    UsuarioService usuarios = mock(UsuarioService.class);
    AtomicReference<Instant> ahora = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
    Clock reloj = new Clock() {
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return ahora.get(); }
    };
    DiscoAlmacenBorradores almacen;
    BorradorEscaneoService servicio;
    byte[] procesada;

    BorradorEscaneoService configurar(long cuota, int porUsuario) throws IOException {
        var props = new BorradoresProperties(carpeta, Duration.ofMinutes(30), DataSize.ofBytes(cuota), porUsuario, 10);
        almacen = spy(new DiscoAlmacenBorradores(props));
        return new BorradorEscaneoService(almacen, props, new DocumentosProperties(DataSize.ofKilobytes(100)),
                escaneo, new DetectorTipo(), usuarios, reloj);
    }

    @BeforeEach
    void preparar() throws Exception {
        when(usuarios.idUsuarioActual()).thenReturn(Optional.of(1L));
        procesada = Muestras.jpeg("resultado.jpg").contenido().getInputStream().readAllBytes();
        when(escaneo.escanear(any(), anyString(), any(), anyBoolean()))
                .thenReturn(new ResultadoEscaneo(procesada, true));
        servicio = configurar(10_000_000, 5);
    }

    @Test
    void generaSoloLaVariantePedidaYLaReutilizaDesdeDisco() throws Exception {
        var foto = Muestras.png("foto.png");
        var id = servicio.crear(foto).id();
        servicio.filtrar(id, "enhanced");
        servicio.filtrar(id, "bw");
        servicio.filtrar(id, "enhanced");
        assertThat(servicio.imagen(id, "bw").contenido()).isEqualTo(procesada);
        verify(escaneo, times(1)).escanear(any(), eq("image/png"), eq(FiltroEscaneo.enhanced), eq(true));
        verify(escaneo, times(1)).escanear(any(), eq("image/png"), eq(FiltroEscaneo.bw), eq(true));
        verifyNoMoreInteractions(escaneo);
        assertThat(carpeta.resolve(id.toString()).resolve("bw.jpg")).exists();
        assertThat(carpeta.resolve(id.toString()).resolve("grayscale.jpg")).doesNotExist();
    }

    @Test
    void fuenteConservaLosBytesYNoInvocaElEscaner() throws Exception {
        var foto = Muestras.png("foto.png");
        var bytes = foto.contenido().getInputStream().readAllBytes();
        var id = servicio.crear(foto).id();
        servicio.filtrar(id, "fuente");
        var reserva = servicio.reservar(new SeleccionBorrador(id, "fuente"));
        assertThat(reserva.fichero().nombreOriginal()).isEqualTo("foto.png");
        assertThat(reserva.fichero().contenido().getInputStream().readAllBytes()).isEqualTo(bytes);
        verifyNoInteractions(escaneo);
    }

    @Test
    void todosLosFiltrosPartenDeLaFuenteYNoDeOtroResultado() throws Exception {
        var foto = Muestras.png("foto.png");
        var bytes = foto.contenido().getInputStream().readAllBytes();
        var id = servicio.crear(foto).id();
        servicio.filtrar(id, "bw"); servicio.filtrar(id, "original");
        verify(escaneo).escanear(eq(bytes), eq("image/png"), eq(FiltroEscaneo.bw), eq(true));
        verify(escaneo).escanear(eq(bytes), eq("image/png"), eq(FiltroEscaneo.original), eq(true));
    }

    @Test
    void siNoHayBordesAplicaElFiltroSobreLaFotoCompleta() {
        when(escaneo.escanear(any(), anyString(), eq(FiltroEscaneo.bw), eq(true)))
                .thenReturn(new ResultadoEscaneo(procesada, false));
        var id = servicio.crear(Muestras.png("foto.png")).id();
        assertThat(servicio.filtrar(id, "bw").sinBordes()).isTrue();
        verify(escaneo).escanear(any(), eq("image/png"), eq(FiltroEscaneo.bw), eq(false));
        servicio.filtrar(id, "bw");
        verify(escaneo, times(1)).escanear(any(), eq("image/png"), eq(FiltroEscaneo.bw), eq(false));
    }

    @Test
    void otroPropietarioNoPuedeConsultarFiltrarGuardarNiDescartar() {
        var id = servicio.crear(Muestras.png("foto.png")).id();
        when(usuarios.idUsuarioActual()).thenReturn(Optional.of(2L));
        assertThatThrownBy(() -> servicio.imagen(id, "fuente")).isInstanceOf(NoEncontradoException.class);
        assertThatThrownBy(() -> servicio.filtrar(id, "bw")).isInstanceOf(NoEncontradoException.class);
        assertThatThrownBy(() -> servicio.reservar(new SeleccionBorrador(id, "fuente")))
                .isInstanceOf(NoEncontradoException.class);
        assertThatThrownBy(() -> servicio.descartar(id)).isInstanceOf(NoEncontradoException.class);
        verifyNoInteractions(escaneo);
    }

    @Test
    void laActividadRenuevaLaCaducidadYLaLimpiezaBorraElBorradorCompleto() {
        var id = servicio.crear(Muestras.png("foto.png")).id();
        ahora.set(ahora.get().plus(Duration.ofMinutes(29)));
        servicio.imagen(id, "fuente");
        ahora.set(ahora.get().plus(Duration.ofMinutes(29)));
        servicio.limpiarCaducados();
        assertThat(carpeta.resolve(id.toString())).exists();
        ahora.set(ahora.get().plus(Duration.ofMinutes(2)));
        assertThatThrownBy(() -> servicio.imagen(id, "fuente")).isInstanceOf(NoEncontradoException.class);
        assertThat(carpeta.resolve(id.toString())).doesNotExist();
    }

    @Test
    void guardarReservaLaVarianteSinReprocesarYRollbackLaConserva() {
        var id = servicio.crear(Muestras.png("foto.png")).id();
        servicio.filtrar(id, "bw");
        var seleccion = new SeleccionBorrador(id, "bw");
        var reserva = servicio.reservar(seleccion);
        assertThatThrownBy(() -> servicio.reservar(seleccion)).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> servicio.descartar(id)).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> servicio.filtrar(id, "enhanced")).isInstanceOf(ReglaNegocioException.class);
        ahora.set(ahora.get().plus(Duration.ofHours(1)));
        servicio.limpiarCaducados();
        assertThat(carpeta.resolve(id.toString())).exists();
        servicio.terminarReserva(reserva, false);
        assertThat(servicio.imagen(id, "bw").contenido()).isEqualTo(procesada);
        var reintento = servicio.reservar(seleccion);
        servicio.terminarReserva(reintento, true);
        assertThat(carpeta.resolve(id.toString())).doesNotExist();
        verify(escaneo, times(1)).escanear(any(), anyString(), any(), anyBoolean());
    }

    @Test
    void noGuardaUnaVarianteQueNoSeHaGenerado() {
        var id = servicio.crear(Muestras.png("foto.png")).id();
        assertThatThrownBy(() -> servicio.reservar(new SeleccionBorrador(id, "bw")))
                .isInstanceOf(ReglaNegocioException.class).hasMessage("escaneo.previsualizar");
        assertThatThrownBy(() -> servicio.filtrar(id, "../../secreto"))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("filtro");
    }

    @Test
    void unFalloDeApiNoSeCacheaYPermiteReintentarOUsarLaFuente() {
        when(escaneo.escanear(any(), anyString(), eq(FiltroEscaneo.bw), eq(true)))
                .thenThrow(new EscaneoException("escaneo.ocupado"))
                .thenReturn(new ResultadoEscaneo(procesada, true));
        var id = servicio.crear(Muestras.png("foto.png")).id();
        assertThatThrownBy(() -> servicio.filtrar(id, "bw")).isInstanceOf(EscaneoException.class);
        assertThat(carpeta.resolve(id.toString()).resolve("bw.jpg")).doesNotExist();
        assertThat(servicio.imagen(id, "fuente").tipo()).isEqualTo("image/png");
        servicio.filtrar(id, "bw");
        assertThat(servicio.imagen(id, "bw").contenido()).isEqualTo(procesada);
    }

    @Test
    void limitaCantidadEspacioTipoYTamanoAntesDeProcesar() throws Exception {
        servicio = configurar(10_000_000, 1);
        servicio.crear(Muestras.png("foto.png"));
        assertThatThrownBy(() -> servicio.crear(Muestras.png("otra.png")))
                .isInstanceOf(ReglaNegocioException.class).hasMessage("escaneo.demasiadas");
        when(usuarios.idUsuarioActual()).thenReturn(Optional.of(2L));
        assertThatThrownBy(() -> servicio.crear(Muestras.ejecutableDisfrazado("foto.jpg")))
                .isInstanceOf(ReglaNegocioException.class).hasMessage("escaneo.incompatible");
        assertThatThrownBy(() -> servicio.crear(Muestras.deTamano("enorme.jpg", 200 * 1024)))
                .isInstanceOf(ReglaNegocioException.class).hasMessage("escaneo.fotoTamano");
        servicio = configurar(1, 5);
        assertThatThrownBy(() -> servicio.crear(Muestras.png("otra.png")))
                .isInstanceOf(ReglaNegocioException.class).hasMessage("escaneo.sinEspacio");
        verifyNoInteractions(escaneo);
    }

    @Test
    void siFallaLaLimpiezaTrasGuardarSeReintentaSinExponerElBorrador() throws Exception {
        var id = servicio.crear(Muestras.png("foto.png")).id();
        var reserva = servicio.reservar(new SeleccionBorrador(id, "fuente"));
        doThrow(new IOException("Disco ocupado")).doCallRealMethod().when(almacen).borrar(id);
        servicio.terminarReserva(reserva, true);
        assertThatThrownBy(() -> servicio.imagen(id, "fuente")).isInstanceOf(NoEncontradoException.class);
        servicio.limpiarCaducados();
        assertThat(carpeta.resolve(id.toString())).doesNotExist();
    }

    @Test
    void limpiaRestosCaducadosTrasReinicioSinTocarOtrosArchivos() throws Exception {
        var id = servicio.crear(Muestras.png("foto.png")).id();
        Files.writeString(carpeta.resolve("conservar.txt"), "otro archivo");
        ahora.set(ahora.get().plus(Duration.ofMinutes(31)));
        var reiniciado = configurar(10_000_000, 5);
        reiniciado.limpiarCaducados();
        assertThat(carpeta.resolve(id.toString())).doesNotExist();
        assertThat(carpeta.resolve("conservar.txt")).exists();
    }
}
