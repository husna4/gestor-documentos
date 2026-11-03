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

## Requisitos

- JDK 21.
- PostgreSQL 13 o superior, con `psql` disponible en el PATH.

Maven Wrapper descarga Maven y las dependencias en la primera ejecución.

## Instalación

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

El móvil sube cada foto una sola vez al gestor. Java llama a DocScanner al solicitar un filtro nuevo
y conserva temporalmente la variante: volver a un filtro ya calculado no repite el procesamiento.
**Guardar documento** guarda exactamente la versión elegida, sin volver a escanear. Si un archivo
falla, permanece en pantalla para reintentarlo; los guardados se retiran de la lista.

DocScanner debe estar iniciado para aplicar filtros. El navegador solo se conecta al gestor.
Si no se detectan bordes, se informa y el filtro se aplica a la imagen completa. Si falla el servicio,
puedes reintentar o elegir **Foto original**. Los filtros siempre parten de la fuente subida.

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

Los temporales pertenecen al administrador que los creó, se sirven con autenticación y `no-store`,
y sus operaciones requieren CSRF. Se eliminan al descartar o después de confirmar el guardado en BD.
Un rollback conserva el borrador. La limpieza se ejecuta al arrancar y cada minuto; los errores de
borrado se reintentan. Cerrar la página deja el borrador hasta su caducidad.

Esta implementación está preparada para **una instancia** del gestor. Sus metadatos temporales están
en memoria: un reinicio invalida los borradores pendientes y sus restos se limpian cuando caducan.
Los documentos ya guardados no se ven afectados. El almacenamiento de borradores tiene una interfaz
independiente para sustituir el disco local; varias instancias requerirían también metadatos y bloqueos
compartidos. La ruta temporal debe ser exclusiva del servicio y distinta de los archivos definitivos;
no se sirve como carpeta pública ni debe versionarse si se cambia fuera de `datos/`.

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
Cubren reutilización de filtros, aislamiento de propietarios, CSRF y roles, cuotas, caducidad,
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
