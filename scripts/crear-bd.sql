-- Crea el usuario y las bases de datos de la aplicación.
-- Ejecútalo UNA vez, como el superusuario postgres:
--
--   "C:\Program Files\PostgreSQL\17\bin\psql.exe" -U postgres -f scripts\crear-bd.sql
--
-- psql te pedirá primero la contraseña de postgres y luego la nueva
-- contraseña del usuario gestordocs (dos veces). Esa contraseña es la que
-- pondrás después en la variable de entorno DB_PASSWORD.

-- Usuario sin privilegios de administración: no puede crear bases de datos,
-- ni otros usuarios, ni saltarse permisos.
CREATE ROLE gestordocs LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE;
\echo
\echo 'Escribe la contraseña para el nuevo usuario gestordocs:'
\password gestordocs

-- Como dueño de estas bases de datos, gestordocs puede crear sus tablas y las
-- extensiones "trusted" (unaccent, pg_trgm), pero nada fuera de ellas.
CREATE DATABASE gestordocs OWNER gestordocs;
CREATE DATABASE gestordocs_test OWNER gestordocs;

-- Por defecto cualquier usuario de Postgres puede conectarse a una BD nueva.
-- Lo limitamos al dueño.
REVOKE CONNECT, TEMPORARY ON DATABASE gestordocs FROM PUBLIC;
REVOKE CONNECT, TEMPORARY ON DATABASE gestordocs_test FROM PUBLIC;

\echo
\echo 'Listo: usuario gestordocs y bases de datos gestordocs y gestordocs_test creados.'
