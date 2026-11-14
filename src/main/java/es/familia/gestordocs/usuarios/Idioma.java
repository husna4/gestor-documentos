package es.familia.gestordocs.usuarios;

import java.util.Locale;

/** Códigos estables almacenados como texto, nunca como ordinales. */
public enum Idioma {
    es, en;

    public Locale locale() { return Locale.forLanguageTag(name()); }
}
