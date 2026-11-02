# Gestor de documentos

Aplicación web para organizar documentos por personas y secciones, con acceso desde ordenador y móvil.

## Funciones

- Usuarios con roles de administrador y consulta.
- Gestión de personas, secciones y clasificación de documentos.
- Subida múltiple y captura de fotos desde el móvil.
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

Las pruebas utilizan `gestordocs_test` y `target/ficheros-test`. Cubren arquitectura modular, autenticación, permisos, subida y validación de archivos, búsquedas, ZIP, borrados y renderizado de pantallas.

La carpeta `muestras/` contiene archivos para pruebas manuales, incluidos tipos no permitidos. No forma parte del almacenamiento de documentos de la aplicación.

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
