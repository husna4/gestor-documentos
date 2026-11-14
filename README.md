# Gestor de documentos

Aplicación web para organizar documentos por personas y secciones, con acceso desde ordenador y móvil.

## Funciones

- Usuarios con roles de administrador y consulta.
- Gestión de personas, secciones y clasificación de documentos.
- Subida múltiple y captura de fotos desde el móvil, con previsualización y filtros antes de guardar.
- Validación del contenido de los archivos, límites de tamaño y miniaturas.
- Búsqueda sin distinguir tildes, previsualización de imágenes y PDF, y descarga individual o en ZIP.
- Inicio de sesión, cambio de contraseña y permisos por rol.

## Tecnologías

Java 21, Spring Boot 4.1, Spring Security, Spring Data JPA, Flyway, PostgreSQL y Spring Modulith. Frontend con Thymeleaf, htmx y Bootstrap, sin compilación con Node.js ni recursos de CDN.

## Instalación con Docker

Necesitas Docker Desktop con contenedores Linux, o Docker Engine con Docker Compose v2
y BuildKit. No necesitas instalar Java, Maven, PostgreSQL ni Python en el equipo.
La primera construcción descarga imágenes y dependencias de Internet.

Desde la raíz del repositorio, tanto en Windows como en Linux, ejecuta:

```sh
docker compose up --build -d --wait
```

**No necesitas crear `.env` ni elegir contraseñas antes de arrancar.** El contenedor
`inicializacion` genera contraseñas aleatorias independientes y una clave de sesión,
las guarda en volúmenes locales de Docker y termina. PostgreSQL y el gestor esperan
a que termine correctamente; el gestor espera además a que PostgreSQL esté disponible.
Los siguientes arranques conservan los secretos existentes y no los muestran en los logs.

Abre **http://localhost:8090**. El usuario inicial es `admin`; consulta su contraseña con:

```sh
docker compose exec app cat /run/app-secrets/app.admin.password
```

Deberás cambiarla en el primer acceso. El archivo conserva la contraseña inicial:
después del cambio, entra con la nueva contraseña. Si personalizaste el usuario inicial,
puedes consultarlo con `docker compose exec app cat /run/app-secrets/app.admin.username`.

`.env.example` contiene únicamente opciones de personalización. Puedes copiarlo a
`.env` para cambiar `APP_PORT` (por ejemplo `8095`), límites o `ADMIN_USERNAME` antes
del primer arranque. Las variables de la terminal prevalecen sobre ese archivo.
Cambiar `ADMIN_USERNAME` después no modifica el usuario ya creado.

PostgreSQL lee su contraseña administrativa con `POSTGRES_PASSWORD_FILE`. Su script
de creación de usuarios lee la contraseña de conexión del mismo archivo que el gestor.
Spring Boot importa `configtree:/run/app-secrets/`: cada nombre de archivo corresponde
a una propiedad, como `spring.datasource.password` o `app.recordarme.clave`.
El gestor solo monta sus secretos; los servicios los montan en modo de solo lectura,
con permisos que permiten a PostgreSQL leer únicamente la contraseña de conexión
compartida. El inicializador es el único que los monta para escritura.

Las variables antiguas `POSTGRES_PASSWORD`, `DB_PASSWORD`, `ADMIN_PASSWORD` y
`REMEMBER_ME_KEY` de la terminal o de `.env` no se pasan a estos contenedores.
El arranque de desarrollo local sigue utilizando sus variables de entorno habituales.
Puedes validar Compose sin mostrar secretos con `docker compose config --quiet`.
Se publica únicamente el puerto web del gestor; PostgreSQL y DocScanner se comunican
por la red de Compose, sin puertos publicados en el equipo.

Este despliegue crea una **instalación nueva**, independiente de una base de datos o
carpeta `datos` utilizadas en desarrollo. Importar datos existentes es una operación
separada: requiere trasladar tanto la base de datos como los archivos.

### Estado, parada y actualización

```sh
docker compose ps
docker compose logs --tail=100 app db docscanner
docker compose stop
docker compose start --wait
docker compose restart
docker compose down
```

`inicializacion` debe aparecer como terminado con código `0`; es su estado normal.
`stop`, `restart` y `down` conservan los volúmenes. Para levantar la instalación
construida sin descargar código de Git, usa `docker compose up -d --wait --no-build`.
Los borradores de fotos son temporales: se descartan al reiniciar el gestor.

Para actualizar, obtén el código nuevo del gestor y vuelve a ejecutar:

```sh
docker compose up --build -d --wait
```

Cada construcción de DocScanner consulta el estado actual de la rama **main** de
[husna4/doc-scanner](https://github.com/husna4/doc-scanner), incluso con caché disponible,
y utiliza su Dockerfile y dependencias bloqueadas. Puede incorporar cambios del proyecto
externo. Un arranque o reinicio sin construcción conserva el código de su imagen existente.
Si Git no está accesible, la construcción falla. El healthcheck de DocScanner verifica
`/health`; el gestor verifica su página de login y espera a PostgreSQL disponible.
`--wait` comprueba los servicios y la finalización del inicializador. Durante el uso,
si el escáner deja de responder, el gestor sigue permitiendo guardar **Foto original**.

### Volúmenes y copias con Docker

Los volúmenes con nombre `<proyecto>_postgres` y `<proyecto>_documentos` contienen,
respectivamente, PostgreSQL y los documentos con sus miniaturas. Otros dos volúmenes,
`<proyecto>_secretos-postgres` y `<proyecto>_secretos-app`, conservan las credenciales
y la clave de sesión. Por defecto el nombre del proyecto es el de la carpeta;
`docker volume ls` muestra los nombres efectivos
y `docker volume inspect NOMBRE` permite consultar su ubicación, gestionada por Docker
(dentro de su máquina virtual en Docker Desktop).

**`docker compose down --volumes` borra los cuatro volúmenes, los secretos y todos los datos.**
No lo uses para una parada o actualización habitual.
Si ya existe una base de datos y faltan secretos, el inicializador falla sin generar
contraseñas nuevas: restaura los volúmenes de secretos de esa misma instalación.
Para una instalación anterior basada en `.env`, traslada sus credenciales a los
archivos de secretos antes de adoptar esta configuración; no se rotan automáticamente.

Haz siempre una copia conjunta de base de datos y documentos con el gestor detenido,
para evitar cambios entre ambas copias. Ejemplo en PowerShell, sin redirigir datos
binarios a través de la terminal:

```powershell
New-Item -ItemType Directory -Force copias | Out-Null
docker compose stop app
try {
    docker compose exec -T db pg_dump -U postgres -F c -f /tmp/gestordocs.dump gestordocs
    if ($LASTEXITCODE -ne 0) { throw 'Falló pg_dump' }
    $dbContainer = docker compose ps -q db
    docker cp "${dbContainer}:/tmp/gestordocs.dump" ./copias/gestordocs.dump
    if ($LASTEXITCODE -ne 0) { throw 'Falló la copia de la base de datos' }
    docker compose run --rm --no-deps --user 0:0 --entrypoint tar -v "${PWD}/copias:/copias" app -C /datos/ficheros -czf /copias/documentos.backup .
    if ($LASTEXITCODE -ne 0) { throw 'Falló la copia de documentos' }
    docker compose run --rm --no-deps --entrypoint tar -v "${PWD}/copias:/copias" inicializacion -C /run -czf /copias/secretos.backup db-secrets app-secrets
    if ($LASTEXITCODE -ne 0) { throw 'Falló la copia de secretos' }
} finally {
    docker compose start --wait app
}
```

Guarda las tres copias en un lugar privado fuera del equipo: `secretos.backup` contiene
contraseñas reales. Guarda también `.env` si personalizaste opciones. Usa una carpeta
distinta por copia para conservar versiones anteriores. Los borradores no se respaldan.

Para restaurar en una instalación nueva con volúmenes vacíos, construye las imágenes
con `docker compose build` y coloca las tres copias en `copias/`. Restaura los secretos
antes de inicializar PostgreSQL para conservar las contraseñas originales:

```powershell
docker compose run --rm --no-deps --entrypoint tar -v "${PWD}/copias:/copias:ro" inicializacion -C /run -xzf /copias/secretos.backup
if ($LASTEXITCODE -ne 0) { throw 'Falló la restauración de secretos' }
docker compose up -d --wait db
if ($LASTEXITCODE -ne 0) { throw 'Falló el arranque de PostgreSQL' }
$dbContainer = docker compose ps -q db
docker cp ./copias/gestordocs.dump "${dbContainer}:/tmp/gestordocs.dump"
if ($LASTEXITCODE -ne 0) { throw 'Falló la copia de la base de datos' }
docker compose exec -T db pg_restore -U postgres --role=gestordocs --no-owner --exit-on-error -d gestordocs /tmp/gestordocs.dump
if ($LASTEXITCODE -ne 0) { throw 'Falló la restauración de la base de datos' }
docker compose run --rm --no-deps --user 0:0 --entrypoint tar -v "${PWD}/copias:/copias:ro" app -C /datos/ficheros -xzf /copias/documentos.backup
if ($LASTEXITCODE -ne 0) { throw 'Falló la restauración de documentos' }
docker compose up -d --wait --no-build
```

No superpongas esta restauración sobre datos existentes. El archivo conserva el usuario
numérico `10001` de los documentos; el gestor se ejecuta con ese usuario sin privilegios.
Flyway actualiza el esquema al arrancar y el usuario `gestordocs`, dueño de su base de
datos, puede crear las extensiones confiables `unaccent` y `pg_trgm` sin ser superusuario.

## Desarrollo local: requisitos

- JDK 21.
- PostgreSQL 13 o superior, con `psql` disponible en el PATH.

Maven Wrapper descarga Maven y las dependencias en la primera ejecución.

## Instalación para desarrollo local

Desde la carpeta del proyecto, crea el usuario y las bases de datos:

```powershell
psql -U postgres -f scripts/crear-bd.sql
```

El script solicita una contraseña para el usuario `gestordocs` y crea `gestordocs` y `gestordocs_test`. Flyway aplica las migraciones al arrancar.

Configura las variables de entorno antes de ejecutar la aplicación:

| Variable | Valor predeterminado o uso |
|---|---|
| `DB_PASSWORD` | Obligatoria: contraseña del usuario de PostgreSQL |
| `REMEMBER_ME_KEY` | Obligatoria: clave aleatoria para firmar la cookie de sesión persistente |
| `ADMIN_USERNAME`, `ADMIN_PASSWORD` | Credenciales del administrador inicial; obligatorias si aún no hay usuarios |
| `DB_URL` | `jdbc:postgresql://localhost:5432/gestordocs` |
| `DB_USER` | `gestordocs` |
| `APP_PORT` | `8090` |
| `APP_STORAGE_PATH` | `./datos/ficheros` |
| `APP_MAX_FILE_SIZE` | `20MB` por archivo |
| `APP_MAX_REQUEST_SIZE` | `200MB` por petición |
| `DB_TEST_URL` | `jdbc:postgresql://localhost:5432/gestordocs_test`, utilizado por los tests |

Ejemplo para la sesión actual de PowerShell:

```powershell
$env:DB_PASSWORD = 'contraseña-de-la-base-de-datos'
$env:ADMIN_USERNAME = 'admin'
$env:ADMIN_PASSWORD = 'contraseña-inicial'
$env:REMEMBER_ME_KEY = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
.\mvnw.cmd spring-boot:run
```

Abre **http://localhost:8090**. El administrador inicial debe cambiar su contraseña en el primer acceso.

Si las variables están guardadas como variables de usuario de Windows, puedes cargarlas y arrancar con:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\arrancar.ps1
```

## Uso

El administrador crea las personas, secciones y usuarios, y sube los documentos. Cada documento puede asignarse a una persona y a varias secciones. Los usuarios de consulta pueden buscar, previsualizar y descargar los documentos.

El botón **Hacer foto** permite capturar una imagen desde el móvil. Las imágenes y los PDF se muestran en el visor; los demás formatos se descargan.

## Escaneo automático de fotos

El formulario permite previsualizar cada foto antes de guardarla. Ofrece documento mejorado,
escala de grises, blanco y negro y sin filtro (manteniendo recorte y perspectiva). **Foto original**
conserva el archivo sin cambios. Puedes comparar con el original, ampliar la vista o quitar fotos.
Cada foto tiene su propia selección; los PDF, GIF y demás documentos mantienen su formato original.

El móvil sube cada foto una sola vez al gestor. Para cada filtro nuevo, el gestor envía el archivo
original a DocScanner, que detecta los bordes, recorta, corrige la perspectiva y aplica el filtro.
El gestor conserva temporalmente el resultado para reutilizarlo si vuelves a elegir ese filtro.
**Guardar documento** guarda exactamente la versión elegida, sin volver a escanear. Si un archivo
falla, permanece en pantalla para reintentarlo; los guardados se retiran de la lista.

Con Docker, DocScanner se construye y arranca automáticamente con el gestor.
Para desarrollo sin Compose, el servicio debe estar iniciado para aplicar filtros. Descarga el código de
[Doc Scanner](https://github.com/husna4/doc-scanner) y sigue las instrucciones de su README
para instalarlo y arrancarlo en tu equipo. El navegador solo se conecta al gestor.
Si no se detectan bordes, se informa y el filtro se aplica a la imagen completa. Si falla el servicio,
puedes reintentar o elegir **Foto original**.

| Variable | Predeterminado |
|---|---|
| `DOCSCANNER_URL` | `http://127.0.0.1:8091/api/scan` |
| `DOCSCANNER_CONNECT_TIMEOUT` | `3s` |
| `DOCSCANNER_REQUEST_TIMEOUT` | `30s` por llamada, incluyendo la respuesta completa |
| `DOCSCANNER_MAX_RESPONSE_SIZE` | `20MB` |
| `APP_SCAN_TEMP_PATH` | `./datos/temporales/escaneo` |
| `APP_SCAN_TEMP_TTL` | `30m` sin actividad por borrador |
| `APP_SCAN_TEMP_MAX_SIZE` | `500MB` para fuentes y variantes de todos los usuarios |
| `APP_SCAN_TEMP_MAX_PER_USER` | `20` borradores por usuario |
| `APP_SCAN_TEMP_MAX_TOTAL` | `100` borradores en total |

Solo el administrador que subió la foto puede acceder a su borrador. Se elimina al guardar o
descartar la foto, o tras 30 minutos de inactividad por defecto.

Los borradores funcionan con una sola instancia del gestor. Un reinicio hace que los borradores
pendientes dejen de estar disponibles. Los documentos guardados siguen disponibles.
Usa una carpeta temporal exclusiva, separada de los documentos guardados y fuera de Git.

## Almacenamiento y copias de seguridad

PostgreSQL guarda los metadatos. Los archivos y las miniaturas se almacenan en disco mediante claves UUID, dentro de `APP_STORAGE_PATH`. Las descargas requieren una sesión autenticada.

Una copia completa debe incluir tanto la base de datos como la carpeta de archivos:

```powershell
pg_dump -U gestordocs -F c -f copia-gestordocs.dump gestordocs
Compress-Archive -Path datos\ficheros -DestinationPath copia-ficheros.zip
```

Si personalizas `APP_STORAGE_PATH`, utiliza esa carpeta al hacer la copia.

## Tests

Con PostgreSQL en marcha y `DB_PASSWORD` configurada:

```powershell
.\mvnw.cmd test
```

Las pruebas utilizan `gestordocs_test`, `target/ficheros-test` y `target/borradores-test`. Cubren arquitectura modular, autenticación, permisos, subida y validación de archivos, búsquedas, ZIP, borrados y renderizado de pantallas.

La carpeta `muestras/` contiene archivos para pruebas manuales, incluidos tipos no permitidos. No forma parte del almacenamiento de documentos de la aplicación.

Las pruebas habituales simulan el servicio de escaneo y no necesitan DocScanner en marcha.
`ConfiguracionSecretosTest` comprueba que Spring importa los archivos de secretos sobre
la configuración del classpath, sin necesitar PostgreSQL. Puedes probar también la
generación, conservación y detección de secretos perdidos con directorios desechables
dentro de un contenedor (desde la raíz del proyecto, en Windows o Linux):

```sh
docker run --rm --network none --tmpfs /run/app-secrets --tmpfs /run/db-secrets --tmpfs /var/lib/postgresql/data --mount type=bind,source=./docker/inicializacion,target=/scripts,readonly --entrypoint sh postgres:17-bookworm /scripts/probar-secretos.sh
```

Las pruebas de escaneo cubren reutilización de filtros, aislamiento de propietarios, CSRF y roles, cuotas, caducidad,
JPEG incompletos y limpieza de borradores después de commit, rollback y fallos de borrado.
Para comprobar también el recorrido con el servicio real, utilizando únicamente una foto sintética y
la base de datos de tests:

```powershell
$env:DOCSCANNER_LIVE_TEST = 'true'
.\mvnw.cmd test
Remove-Item Env:DOCSCANNER_LIVE_TEST
```

## Empaquetado

```powershell
.\mvnw.cmd package
java -jar target\gestor-documentos-0.0.1-SNAPSHOT.jar
```

## Estructura

El código se organiza por dominio en `es.familia.gestordocs`:

- `usuarios`: cuentas, roles y seguridad.
- `personas` y `secciones`: clasificación de documentos.
- `documentos`: subida, consulta, búsqueda y descargas.
- `almacenamiento`: acceso a los archivos en disco.
- `portal`: pantallas que combinan los módulos.
- `compartido`: excepciones y utilidades comunes.

Las migraciones están en `src/main/resources/db/migration`; las plantillas y estilos, en `src/main/resources/templates` y `static`.
