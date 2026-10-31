package es.familia.gestordocs.documentos;

import org.springframework.core.io.InputStreamSource;

/**
 * Un fichero recibido, independiente de cómo llegó (formulario web, API REST...).
 * El {@code MultipartFile} de Spring implementa {@link InputStreamSource}, así que se adapta directamente.
 */
public record FicheroSubido(String nombreOriginal, long tamano, InputStreamSource contenido) {
}
