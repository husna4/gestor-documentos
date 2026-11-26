ALTER TABLE usuario ADD COLUMN idioma VARCHAR(2) NOT NULL DEFAULT 'es'
    CONSTRAINT usuario_idioma_valido CHECK (idioma IN ('es', 'en'));
