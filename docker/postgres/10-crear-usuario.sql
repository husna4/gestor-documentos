-- Solo se ejecuta al inicializar un volumen vacío. Flyway crea todo el esquema.
-- La interpolación literal de psql protege contraseñas con comillas o símbolos.
\set app_password `cat /run/app-secrets/spring.datasource.password`
CREATE ROLE gestordocs LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'app_password';
CREATE DATABASE gestordocs OWNER gestordocs;
REVOKE CONNECT, TEMPORARY ON DATABASE gestordocs FROM PUBLIC;
