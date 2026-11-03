package es.familia.gestordocs.documentos.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DiscoAlmacenBorradores implements AlmacenBorradores {
    private final Path raiz;
    public DiscoAlmacenBorradores(BorradoresProperties propiedades) throws IOException {
        raiz = propiedades.ruta();
        Files.createDirectories(raiz);
        if (Files.isSymbolicLink(raiz)) { throw new IOException("Carpeta temporal no válida"); }
    }

    private Path carpeta(UUID id) throws IOException {
        var path = raiz.resolve(id.toString());
        if (Files.isSymbolicLink(path)) { throw new IOException("Borrador no válido"); }
        return path;
    }

    private Path archivo(UUID id, String variante) throws IOException {
        if (!Set.of("fuente", "original", "grayscale", "bw", "enhanced").contains(variante)) {
            throw new IllegalArgumentException("Variante no válida");
        }
        var path = carpeta(id).resolve(variante.equals("fuente") ? "fuente" : variante + ".jpg");
        if (Files.isSymbolicLink(path)) { throw new IOException("Variante no válida"); }
        return path;
    }

    @Override
    public void escribir(UUID id, String variante, byte[] contenido) throws IOException {
        var destino = archivo(id, variante);
        Files.createDirectories(destino.getParent());
        var temporal = Files.createTempFile(destino.getParent(), ".variante-", ".part");
        try {
            Files.write(temporal, contenido);
            Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporal); }
    }

    @Override
    public byte[] leer(UUID id, String variante) throws IOException {
        return Files.readAllBytes(archivo(id, variante));
    }

    @Override
    public long espacioUtilizado() throws IOException {
        try (var paths = Files.walk(raiz)) {
            long bytes = 0;
            for (var path : paths.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).toList()) {
                bytes = Math.addExact(bytes, Files.size(path));
            }
            return bytes;
        }
    }

    @Override
    public void tocar(UUID id, Instant instante) throws IOException {
        Files.setLastModifiedTime(carpeta(id), FileTime.from(instante));
    }

    @Override
    public void borrar(UUID id) throws IOException {
        var carpeta = carpeta(id);
        if (!Files.exists(carpeta, LinkOption.NOFOLLOW_LINKS)) { return; }
        // walk no sigue enlaces: nunca se borran destinos fuera del borrador.
        try (var paths = Files.walk(carpeta)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) { Files.deleteIfExists(path); }
        }
    }

    @Override
    public void limpiarHuerfanos(Instant anterioresA, Set<UUID> activos) throws IOException {
        try (var paths = Files.list(raiz)) {
            for (var path : paths.toList()) {
                UUID id;
                try { id = UUID.fromString(path.getFileName().toString()); }
                catch (IllegalArgumentException e) { continue; }
                if (!activos.contains(id) && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                        && Files.getLastModifiedTime(path).toInstant().isBefore(anterioresA)) { borrar(id); }
            }
        }
    }
}
