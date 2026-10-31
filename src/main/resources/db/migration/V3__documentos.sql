-- Módulo documentos
CREATE TABLE documento (
    id              BIGSERIAL    PRIMARY KEY,
    nombre_visible  VARCHAR(255) NOT NULL,
    nombre_original VARCHAR(255) NOT NULL,
    tipo_mime       VARCHAR(150) NOT NULL,
    tamano_bytes    BIGINT       NOT NULL,
    clave_fichero   UUID         NOT NULL UNIQUE,  -- nombre interno del fichero en disco
    clave_miniatura UUID,                          -- null si no hay miniatura
    descripcion     TEXT,
    -- Referencias a otros módulos, sin clave foránea (ver PersonaEliminadaEvent).
    persona_id      BIGINT,
    subido_por_id   BIGINT       NOT NULL,
    subido_en       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX documento_persona_idx ON documento (persona_id);
CREATE INDEX documento_subido_en_idx ON documento (subido_en DESC);

-- Relación N:M documento-sección. La FK a documento sí existe (mismo módulo);
-- la de sección no (otro módulo, ver SeccionEliminadaEvent).
CREATE TABLE documento_seccion (
    documento_id BIGINT NOT NULL REFERENCES documento (id) ON DELETE CASCADE,
    seccion_id   BIGINT NOT NULL,
    PRIMARY KEY (documento_id, seccion_id)
);

CREATE INDEX documento_seccion_seccion_idx ON documento_seccion (seccion_id);
