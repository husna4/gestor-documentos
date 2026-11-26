package es.familia.gestordocs.compartido;

import java.util.Locale;

/** Idiomas de la interfaz disponibles para cada navegador. */
public enum Idioma {
    es, en;

    public Locale locale() { return Locale.forLanguageTag(name()); }
}
