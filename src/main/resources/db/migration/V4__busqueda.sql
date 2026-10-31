-- Búsqueda por nombre sin distinguir mayúsculas ni tildes, con coincidencias parciales.
-- unaccent quita las tildes ("María" -> "Maria"); pg_trgm permite indexar LIKE '%texto%'.
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- unaccent() no está marcada como IMMUTABLE y Postgres no deja usarla en un índice.
-- Este envoltorio fija el diccionario y sí es inmutable.
CREATE FUNCTION f_unaccent(texto TEXT) RETURNS TEXT
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
AS $$
    SELECT public.unaccent('public.unaccent'::regdictionary, texto)
$$;

CREATE INDEX documento_nombre_busqueda_idx
    ON documento USING gin (f_unaccent(lower(nombre_visible)) gin_trgm_ops);
