package es.familia.gestordocs.documentos;

import java.util.UUID;

/** Variante que el usuario ha previsualizado y desea guardar. */
public record SeleccionBorrador(UUID id, String variante) { }
