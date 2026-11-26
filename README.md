# Gestor de documentos

Aplicación web para organizar documentos por personas y secciones, con acceso desde ordenador y móvil.

- Usuarios con roles de administrador y consulta.
- Subida múltiple y captura de fotos con recorte y filtros.
- Búsqueda sin distinguir tildes, visor de imágenes y PDF, y descarga individual o en ZIP.
- Validación de archivos, límites de tamaño y miniaturas.

Desarrollada con Java 21, Spring Boot, PostgreSQL y Thymeleaf, htmx y Bootstrap. No requiere compilación con Node.js.

## Instalación con Docker

Necesitas Docker con contenedores Linux y Compose v2 con BuildKit. Desde la raíz del repositorio:

```sh
docker compose up --build -d --wait
```

Abre **http://localhost:8090** e inicia sesión como `admin`. Consulta la contraseña inicial con:

```sh
docker compose exec app cat /run/app-secrets/app.admin.password
```

Deberás cambiarla en el primer acceso; el comando seguirá mostrando la contraseña inicial.

No necesitas crear `.env`: las contraseñas se generan y conservan automáticamente. Para personalizar el puerto, los límites o el usuario inicial, copia [.env.example](.env.example) a `.env` antes del primer arranque. Cambiar `ADMIN_USERNAME` después no modifica el usuario creado.

Docker arranca el gestor, PostgreSQL y DocScanner en una instalación independiente de los datos de desarrollo local.

### Comandos habituales

```sh
docker compose ps                  # Consultar el estado
docker compose logs --tail=100 app # Ver los últimos logs del gestor
docker compose stop               # Detener
docker compose start --wait        # Volver a arrancar
docker compose down               # Eliminar contenedores, conservando los datos
```

Para actualizar, descarga el código nuevo y repite `docker compose up --build -d --wait`. La construcción también toma la versión actual de la rama `main` de [DocScanner](https://github.com/husna4/doc-scanner).

### Datos y copias

Los volúmenes `postgres` y `documentos` guardan la base de datos y los archivos; `secretos-postgres` y `secretos-app`, las credenciales. Docker añade el nombre del proyecto como prefijo.

Para una copia completa, detén el gestor y respalda la base de datos, los documentos y los secretos. Guarda las copias en un lugar privado fuera del equipo, junto con `.env` si lo utilizas.

**`docker compose down --volumes` elimina todos los datos y secretos.** No lo uses para detener o actualizar la aplicación.

## Desarrollo local

Necesitas JDK 21 y PostgreSQL 13 o superior, con `psql` en el PATH. Maven Wrapper descarga Maven y las dependencias.

Crea el usuario y las bases de datos:

```powershell
psql -U postgres -f scripts/crear-bd.sql
```

El script solicita la contraseña de `gestordocs` y crea las bases `gestordocs` y `gestordocs_test`. Flyway aplica las migraciones al arrancar.

Configura las variables y arranca en PowerShell:

```powershell
$env:DB_PASSWORD = 'contraseña-de-la-base-de-datos'
$env:ADMIN_USERNAME = 'admin'
$env:ADMIN_PASSWORD = 'contraseña-inicial'
$env:REMEMBER_ME_KEY = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
.\mvnw.cmd spring-boot:run
```

Abre **http://localhost:8090**. El administrador inicial debe cambiar su contraseña en el primer acceso.

Si ya guardaste las variables como variables de usuario de Windows, puedes arrancar con:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\arrancar.ps1
```

### Configuración

| Variable | Valor predeterminado o uso |
|---|---|
| `DB_PASSWORD` | Obligatoria: contraseña de PostgreSQL |
| `REMEMBER_ME_KEY` | Obligatoria: clave para la sesión persistente |
| `ADMIN_USERNAME`, `ADMIN_PASSWORD` | Obligatorias si aún no hay usuarios |
| `DB_URL` | `jdbc:postgresql://localhost:5432/gestordocs` |
| `DB_USER` | `gestordocs` |
| `APP_PORT` | `8090` |
| `APP_STORAGE_PATH` | `./datos/ficheros` |
| `APP_MAX_FILE_SIZE` | `20MB` por archivo |
| `APP_MAX_REQUEST_SIZE` | `200MB` por petición |
| `DB_TEST_URL` | `jdbc:postgresql://localhost:5432/gestordocs_test` |

## Uso y escaneo de fotos

El administrador gestiona personas, secciones, usuarios y documentos. Los usuarios de consulta pueden buscar, previsualizar y descargar documentos.

**Hacer foto** permite capturar imágenes desde el móvil. Antes de guardar puedes comparar el original con versiones recortadas y filtradas. **Guardar documento** conserva la versión elegida; **Foto original** guarda el archivo sin cambios y sigue disponible si falla el escáner.

Con Docker, DocScanner arranca automáticamente. Para desarrollo local, sigue el [README de DocScanner](https://github.com/husna4/doc-scanner). El gestor lo busca en `http://127.0.0.1:8091/api/scan`; puedes cambiarlo con `DOCSCANNER_URL`.

Los borradores de fotos caducan tras 30 minutos de inactividad y se descartan al reiniciar. Las demás opciones de escaneo están en [application.yml](src/main/resources/application.yml).

## Copias en desarrollo local

Detén el gestor y copia tanto la base de datos como la carpeta de archivos:

```powershell
pg_dump -U gestordocs -F c -f copia-gestordocs.dump gestordocs
Compress-Archive -Path datos\ficheros -DestinationPath copia-ficheros.zip
```

Si personalizas `APP_STORAGE_PATH`, utiliza esa carpeta al hacer la copia.

## Tests y empaquetado

Con PostgreSQL en marcha y `DB_PASSWORD` configurada:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
java -jar target\gestor-documentos-0.0.1-SNAPSHOT.jar
```

Los tests usan `gestordocs_test` y simulan DocScanner. Para probar con el servicio real, configura `DOCSCANNER_LIVE_TEST=true`. La carpeta `muestras/` contiene archivos para pruebas manuales.

Para comprobar solo los límites entre módulos, sin PostgreSQL:

```powershell
.\mvnw.cmd "-Dtest=ModularidadTest" test
```

## Idiomas de la interfaz

La interfaz está disponible en **Español** y **English**. Español es el valor inicial de todas
las cuentas, incluido el administrador de primer arranque y los usuarios anteriores a la
migración aditiva `V5__idioma_usuario.sql`. Solo se guardan los códigos `es` y `en`.

El selector aparece en la navegación, el login y la pantalla de cambio obligatorio de
contraseña. El cambio se envía por POST con CSRF. Con sesión iniciada guarda la preferencia
propia en la cuenta y una cookie de navegador; el administrador también puede elegir el idioma
al crear o editar otra cuenta. La preferencia de la cuenta prevalece sobre la cookie, incluso
con «Recordarme», y los cambios administrativos se reflejan en la siguiente petición.
Sin sesión solo se consulta la cookie validada; sin ella se usa español, independientemente
del idioma del navegador o del sistema. Tras salir puede mantenerse el último idioma elegido.

En la subida, cambiar de idioma recarga la pantalla. Si hay archivos pendientes, se pide
confirmación **antes** de enviar el cambio; cancelar conserva la selección. Continuar pierde
esa selección sin guardarla. Durante la preparación o el guardado se bloquea el cambio.
Los borradores abandonados se eliminan mediante la caducidad habitual.

Los nombres, descripciones, notas y ficheros de usuarios se mantienen tal como se escribieron.
También se mantienen los formatos actuales de fechas y números y la configuración de búsqueda.

Para mantener o añadir traducciones:

- `src/main/resources/messages.properties` es el catálogo base completo en español;
  `messages_en.properties` contiene las mismas claves en inglés. Ambos usan UTF-8.
- Usar claves semánticas y mensajes completos con argumentos `{0}`, `{1}`, etc. Las
  validaciones usan los mismos catálogos con sus parámetros propios (`{min}`, `{max}`).
- Los servicios devuelven `Mensaje` (clave y argumentos), sin depender del idioma HTTP.
  Thymeleaf y los adaptadores web resuelven el texto al presentarlo. Los contratos JSON
  conservan `mensaje`, `subidos`, `errores` y `avisos`; estos dos últimos siguen siendo listas de texto.
- El JavaScript de subida recibe únicamente sus mensajes mediante la serialización segura
  de Thymeleaf. Los datos dinámicos se muestran con `textContent`.
- Ejecutar `I18nCatalogosTest` para comprobar equivalencia de claves y parámetros.
  `IdiomaWebTest`, `I18nMigracionTest` y `PantallasTest` cubren preferencias, seguridad,
  migración, validaciones, JSON y renderizado en ambos idiomas.

En trabajos simultáneos, `DB_TEST_URL` debe apuntar a una base dedicada y el almacenamiento
y los borradores deben quedar dentro del worktree de pruebas. Las pruebas normales mantienen
DocScanner simulado; no se necesita arrancar el servicio real.
