#!/bin/sh
set -eu
umask 077

app_secrets=/run/app-secrets
db_secrets=/run/db-secrets
db_data=/var/lib/postgresql/data

# Resolver UID/GID desde la misma imagen que ejecuta PostgreSQL.
# El gestor utiliza UID 10001. Solo el password de conexión se comparte con postgres.
app_uid=10001
postgres_gid=$(id -g postgres)
postgres_uid=$(id -u postgres)

mkdir -p "$app_secrets" "$db_secrets"
chown "$app_uid:$postgres_gid" "$app_secrets"
chmod 0750 "$app_secrets"
chown "$postgres_uid:$postgres_gid" "$db_secrets"
chmod 0700 "$db_secrets"

# Una BD existente debe conservar exactamente sus secretos. No generar otros que
# dejarían al gestor sin acceso, ni sustituir la clave persistente de «Recordarme».
if [ -f "$db_data/PG_VERSION" ]; then
    for secret in \
        "$db_secrets/postgres-password" \
        "$app_secrets/spring.datasource.password" \
        "$app_secrets/app.admin.username" \
        "$app_secrets/app.admin.password" \
        "$app_secrets/app.recordarme.clave"; do
        if [ ! -s "$secret" ]; then
            echo "Error: existe una base de datos pero falta un secreto. Restaura los volúmenes de secretos de esta instalación antes de arrancar." >&2
            exit 1
        fi
    done
fi

create_secret() {
    secret_path=$1
    secret_uid=$2
    secret_gid=$3
    secret_mode=$4
    if [ ! -e "$secret_path" ]; then
        # Publicar solo archivos completos; conservar los existentes tras un reinicio.
        temporary=$(mktemp "${secret_path}.XXXXXX")
        if [ "$secret_path" = "$app_secrets/app.admin.username" ]; then
            printf '%s' "${ADMIN_USERNAME:-admin}" > "$temporary"
        else
            od -An -N32 -tx1 /dev/urandom | tr -d ' \n' > "$temporary"
        fi
        chown "$secret_uid:$secret_gid" "$temporary"
        chmod "$secret_mode" "$temporary"
        mv "$temporary" "$secret_path"
    fi
    if [ ! -s "$secret_path" ]; then
        echo "Error: hay un archivo de secreto vacío. Restaura la configuración de esta instalación." >&2
        exit 1
    fi
    chown "$secret_uid:$secret_gid" "$secret_path"
    chmod "$secret_mode" "$secret_path"
}

create_secret "$db_secrets/postgres-password" "$postgres_uid" "$postgres_gid" 0400
create_secret "$app_secrets/spring.datasource.password" "$app_uid" "$postgres_gid" 0440
create_secret "$app_secrets/app.admin.username" "$app_uid" "$postgres_gid" 0400
create_secret "$app_secrets/app.admin.password" "$app_uid" "$postgres_gid" 0400
create_secret "$app_secrets/app.recordarme.clave" "$app_uid" "$postgres_gid" 0400

echo "Secretos preparados. Los valores existentes se conservan."
echo "Consulta el usuario inicial con: docker compose exec app cat /run/app-secrets/app.admin.username"
echo "Consulta su contraseña con: docker compose exec app cat /run/app-secrets/app.admin.password"
