# Document Manager

**English** | [Español](README.es.md)

A web application for organizing documents by person and section, accessible from desktop and mobile devices.

- Administrator and read-only user roles.
- Multiple file uploads and photo capture with cropping and filters.
- Accent-insensitive search, image and PDF previews, and individual or ZIP downloads.
- File validation, size limits, and thumbnails.

Built with Java 21, Spring Boot, PostgreSQL, Thymeleaf, htmx, and Bootstrap. No Node.js build is required.

## Install with Docker

You need Docker with Linux containers and Compose v2 with BuildKit. From the repository root:

```sh
docker compose up --build -d --wait
```

Open [http://localhost:8090](http://localhost:8090) and sign in as `admin`. Retrieve the initial password with:

```sh
docker compose exec app cat /run/app-secrets/app.admin.password
```

You must change it when you first sign in. The command will continue to show the initial password afterward.

You do not need to create `.env`: passwords are generated and persisted automatically. To customize the port, limits, or initial username, copy [.env.example](.env.example) to `.env` before the first startup. Changing `ADMIN_USERNAME` afterward does not modify the existing account.

Docker starts the document manager, PostgreSQL, and DocScanner in an installation separate from your local development data.

### Common commands

```sh
docker compose ps                  # Check service status
docker compose logs --tail=100 app # View recent application logs
docker compose stop               # Stop services
docker compose start --wait        # Start services again
docker compose down               # Remove containers while keeping data
```

To update, get the latest code and run `docker compose up --build -d --wait` again. The build also uses the current version of the [DocScanner](https://github.com/husna4/doc-scanner) `main` branch.

### Data and backups

The `postgres` and `documentos` volumes store the database and uploaded files. The `secretos-postgres` and `secretos-app` volumes store credentials. Docker prefixes volume names with the project name.

For a complete backup, stop the application and back up the database, documents, and secrets. Keep backups in a private location on another device, together with `.env` if you use it.

**`docker compose down --volumes` deletes all data and secrets.** Do not use it to stop or update the application.

## Local development

You need JDK 21 and PostgreSQL 13 or later, with `psql` on your PATH. Maven Wrapper downloads Maven and its dependencies.

Create the database user and databases:

```powershell
psql -U postgres -f scripts/crear-bd.sql
```

The script prompts for the `gestordocs` password and creates the `gestordocs` and `gestordocs_test` databases. Flyway applies migrations at startup.

Set the environment variables and start the application in PowerShell:

```powershell
$env:DB_PASSWORD = 'your-database-password'
$env:ADMIN_USERNAME = 'admin'
$env:ADMIN_PASSWORD = 'your-initial-password'
$env:REMEMBER_ME_KEY = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
.\mvnw.cmd spring-boot:run
```

Open [http://localhost:8090](http://localhost:8090). The initial administrator must change their password when first signing in.

If you have already saved these variables as Windows user environment variables, you can start the application with:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\arrancar.ps1
```

### Configuration

| Variable | Default value or purpose |
|---|---|
| `DB_PASSWORD` | Required: PostgreSQL password |
| `REMEMBER_ME_KEY` | Required: signing key for persistent sign-in |
| `ADMIN_USERNAME`, `ADMIN_PASSWORD` | Required when no user accounts exist yet |
| `DB_URL` | `jdbc:postgresql://localhost:5432/gestordocs` |
| `DB_USER` | `gestordocs` |
| `APP_PORT` | `8090` |
| `APP_STORAGE_PATH` | `./datos/ficheros` |
| `APP_MAX_FILE_SIZE` | `20MB` per file |
| `APP_MAX_REQUEST_SIZE` | `200MB` per request |
| `DB_TEST_URL` | `jdbc:postgresql://localhost:5432/gestordocs_test` |

## Usage and photo scanning

Administrators manage people, sections, users, and documents. Read-only users can search, preview, and download documents.

**Take a photo** captures an image from a mobile device. Before saving, you can compare the original with cropped and filtered versions. **Save document** saves the selected version. **Original photo** keeps the file unchanged and remains available if scanning fails.

With Docker, DocScanner starts automatically. For local development, follow the [DocScanner README](https://github.com/husna4/doc-scanner). The document manager connects to `http://127.0.0.1:8091/api/scan` by default; override this with `DOCSCANNER_URL` if needed.

Photo drafts expire after 30 minutes of inactivity and are discarded on restart. Other scanning options are documented in [application.yml](src/main/resources/application.yml).

## Local development backups

Stop the application and back up both the database and the uploaded files directory:

```powershell
pg_dump -U gestordocs -F c -f copia-gestordocs.dump gestordocs
Compress-Archive -Path datos\ficheros -DestinationPath copia-ficheros.zip
```

If you customize `APP_STORAGE_PATH`, use that directory when making the backup.

## Tests and packaging

With PostgreSQL running and `DB_PASSWORD` configured:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
java -jar target\gestor-documentos-0.0.1-SNAPSHOT.jar
```

Tests use `gestordocs_test` and simulate DocScanner. To test against the real service, set `DOCSCANNER_LIVE_TEST=true`. The `muestras/` directory contains files for manual testing.

To check module boundaries without PostgreSQL:

```powershell
.\mvnw.cmd "-Dtest=ModularidadTest" test
```

## Interface languages

Available in English and Spanish. The app initially uses your browser's language. You can change it from the dropdown, and your choice is remembered in that browser.
