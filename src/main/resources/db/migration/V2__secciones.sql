-- Módulo secciones
CREATE TABLE seccion (
    id          BIGSERIAL    PRIMARY KEY,
    nombre      VARCHAR(100) NOT NULL,
    descripcion TEXT,
    creado_en   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- "DNI" y "dni" cuentan como la misma sección.
CREATE UNIQUE INDEX seccion_nombre_unico ON seccion (lower(nombre));
