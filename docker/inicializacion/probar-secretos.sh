#!/bin/sh
# Ejecutar solo en un contenedor desechable, con /run/*-secrets y PGDATA en tmpfs.
set -eu

initializer=/scripts/inicializar-secretos.sh
app_secrets=/run/app-secrets
db_secrets=/run/db-secrets
db_data=/var/lib/postgresql/data

# Impedir que una ejecución accidental use una instalación real.
for directory in "$app_secrets" "$db_secrets" "$db_data"; do
    if ! mountpoint -q "$directory" || [ -n "$(ls -A "$directory")" ]; then
        echo "Las pruebas requieren directorios montados y vacíos: $directory" >&2
        exit 1
    fi
done

sh "$initializer" > /tmp/primera-inicializacion.log
for secret in "$db_secrets/postgres-password" \
    "$app_secrets/spring.datasource.password" \
    "$app_secrets/app.admin.password" \
    "$app_secrets/app.recordarme.clave"; do
    test "$(wc -c < "$secret")" -eq 64
    if grep -q '[^0-9a-f]' "$secret" || grep -Fq -f "$secret" /tmp/primera-inicializacion.log; then
        echo "Error: secreto con formato incorrecto o expuesto en los logs." >&2
        exit 1
    fi
done
test "$(cat "$app_secrets/app.admin.username")" = admin
test "$(cat "$app_secrets/spring.datasource.password")" != "$(cat "$db_secrets/postgres-password")"

sha256sum "$app_secrets/"* "$db_secrets/"* > /tmp/secretos.sha256
ADMIN_USERNAME=otro-usuario sh "$initializer" > /dev/null
sha256sum --check --status /tmp/secretos.sha256
echo "OK: generación aleatoria, sin secretos en logs e inicialización idempotente."

# La copia de secretos debe conservar los bytes y sus permisos al restaurarla.
mkdir /tmp/restauracion
tar -C /run -cf /tmp/secretos.tar app-secrets db-secrets
tar -C /tmp/restauracion -xf /tmp/secretos.tar
for secret in "$app_secrets/"* "$db_secrets/"*; do
    restored=/tmp/restauracion/${secret#/run/}
    cmp "$secret" "$restored"
    test "$(stat -c '%u:%g:%a' "$secret")" = "$(stat -c '%u:%g:%a' "$restored")"
done
echo "OK: copia y restauración de secretos conserva contenido, propietario y permisos."

# Simular una BD inicializada y la pérdida de un secreto. Debe fallar sin regenerar.
printf '%s' 17 > "$db_data/PG_VERSION"
mv "$app_secrets/app.admin.password" /tmp/admin-password-original
if sh "$initializer" > /tmp/secreto-ausente.log 2>&1; then
    echo "Error: se aceptó una base de datos con secretos incompletos." >&2
    exit 1
fi
test ! -e "$app_secrets/app.admin.password"
mv /tmp/admin-password-original "$app_secrets/app.admin.password"
sha256sum --check --status /tmp/secretos.sha256
echo "OK: una BD existente impide regenerar secretos perdidos."

# Un archivo vacío tampoco puede convertirse silenciosamente en otra contraseña.
mv "$db_secrets/postgres-password" /tmp/postgres-password-original
: > "$db_secrets/postgres-password"
if sh "$initializer" > /tmp/secreto-vacio.log 2>&1; then
    echo "Error: se aceptó un secreto vacío." >&2
    exit 1
fi
test ! -s "$db_secrets/postgres-password"
mv /tmp/postgres-password-original "$db_secrets/postgres-password"
sh "$initializer" > /dev/null
sha256sum --check --status /tmp/secretos.sha256
echo "OK: detección de secretos vacíos y recuperación con los valores originales."
