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

The interface is available in **Spanish** and **English**. The preference belongs to each browser and is not stored or configured in user accounts.

On the first visit, the application uses the browser's `Accept-Language` header, respecting its preference order. For example, `en-GB` selects English and `es-ES` selects Spanish. If no supported language matches or the header is invalid, the application uses Spanish. Geographic location and the server's system language are not used.

The language selector appears in the navigation bar, on the sign-in page, and on the mandatory password change page. Selecting a language applies it automatically and saves an `idioma` cookie for one year. A manual selection takes precedence over the browser's advertised language. Each browser or device keeps its own selection when signing in, signing out, using **Remember me**, or switching accounts. A fallback button is available when JavaScript is disabled. Changes still use POST with CSRF protection and do not modify any account.

Migration `V6__idioma_en_navegador.sql` removes the former user language column. The previously applied V5 migration remains unchanged. Accounts, permissions, passwords, documents, and uploaded files are preserved.

Changing the language on the upload page reloads it. If files are pending, the application asks for confirmation **before** submitting the change. Canceling keeps the files and restores the previous dropdown selection. Continuing discards the selection without saving it. Language changes are disabled while previews are being prepared or files are being saved. Abandoned drafts are removed through the usual expiration process.

User-entered names, descriptions, notes, and files remain unchanged. Existing date and number formats and search settings are also preserved.

To maintain or add translations:

- `src/main/resources/messages.properties` is the complete Spanish base catalog; `messages_en.properties` contains the same keys in English. Both use UTF-8.
- Use semantic keys and complete messages with arguments such as `{0}` and `{1}`. Validation messages use the same catalogs with their own parameters, such as `{min}` and `{max}`.
- Services return `Mensaje` objects containing a key and arguments, independently of the HTTP locale. Thymeleaf and web adapters resolve text when displaying it. JSON contracts retain `mensaje`, `subidos`, `errores`, and `avisos`; the last two remain lists of text messages.
- The upload JavaScript receives only the messages it needs through safe Thymeleaf serialization. Dynamic content is displayed with `textContent`.
- Run `I18nCatalogosTest` to check matching keys and parameters. `IdiomaWebTest`, `I18nMigracionTest`, and `PantallasTest` cover preferences, security, migrations, validation, JSON responses, and rendering in both languages.

When working on concurrent changes, point `DB_TEST_URL` at a dedicated test database and keep storage and drafts inside the test worktree. Regular tests simulate DocScanner; the real service does not need to be running.
