# Documentos de casa

Aplicación web para guardar y consultar los documentos de la familia (DNIs, contratos, facturas, fotos…)
desde cualquier dispositivo de la red de casa. Pensada para 10–20 personas y para usarse sobre todo desde el móvil.

- **Admin**: gestiona usuarios, personas y secciones; sube, edita y borra documentos.
- **Usuario**: ve, previsualiza y descarga documentos (individualmente o en ZIP).

Stack: Java 21 · Spring Boot 4.1 · Spring Security · Spring Data JPA · Flyway · PostgreSQL · Thymeleaf + htmx · Bootstrap 5 · Spring Modulith.

---

## 1. Requisitos

| Programa | Versión | Comprobar |
|---|---|---|
| JDK | 21 (por ejemplo [Eclipse Temurin](https://adoptium.net/temurin/releases/?version=21)) | `java -version` |
| PostgreSQL | 13 o superior (probado con 17) | servicio `postgresql-x64-17` en marcha |

No hace falta instalar Maven ni Spring Boot: el *Maven Wrapper* (`mvnw.cmd`) descarga Maven y todas
las dependencias la primera vez.

## 2. Crear la base de datos (una sola vez)

```powershell
& "C:\Program Files\PostgreSQL\17\bin\psql.exe" -U postgres -f scripts\crear-bd.sql
```

Crea el usuario `gestordocs` (sin privilegios de administrador) y dos bases de datos de las que es dueño:
`gestordocs` (la de verdad) y `gestordocs_test` (la usan los tests). Pide la contraseña de `postgres` y
la nueva contraseña de `gestordocs`.

Las tablas no se crean a mano: **Flyway** aplica al arrancar los scripts de
`src/main/resources/db/migration` (`V1__...sql`, `V2__...sql`…) que aún no se hayan aplicado.

## 3. Configuración (variables de entorno)

Toda la configuración está en `src/main/resources/application.yml`. Los valores sensibles se leen de
variables de entorno; defínelas una vez como variables de usuario de Windows:

| Variable | Obligatoria | Para qué |
|---|---|---|
| `DB_PASSWORD` | sí | Contraseña del usuario `gestordocs` de PostgreSQL |
| `REMEMBER_ME_KEY` | sí | Clave secreta para firmar la cookie de «Recordarme» (cualquier texto largo y aleatorio) |
| `ADMIN_USERNAME`, `ADMIN_PASSWORD` | en el primer arranque | Admin inicial. Solo se usan si no hay ningún usuario; en su primer login se le obliga a cambiar la contraseña |
| `APP_PORT` | no (8090) | Puerto web |
| `APP_STORAGE_PATH` | no (`./datos/ficheros`) | Carpeta donde se guardan los ficheros |
| `APP_MAX_FILE_SIZE` | no (`20MB`) | Tamaño máximo por fichero |
| `DB_URL`, `DB_USER` | no | Si PostgreSQL no está en `localhost:5432` o el usuario no es `gestordocs` |

```powershell
setx DB_PASSWORD "la-contraseña-de-gestordocs"
setx ADMIN_USERNAME "admin"
setx ADMIN_PASSWORD "una-contraseña-temporal"
setx REMEMBER_ME_KEY ([guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N'))
```

`setx` solo afecta a las terminales que se abran después. Para ver el valor de una variable:
`[Environment]::GetEnvironmentVariable('ADMIN_PASSWORD','User')`.

## 4. Arrancar

```powershell
powershell -ExecutionPolicy Bypass -File scripts\arrancar.ps1
```

(es `mvnw.cmd spring-boot:run` cargando antes las variables de usuario). Luego abre <http://localhost:8090>
y entra con `ADMIN_USERNAME` / `ADMIN_PASSWORD`.

Para producción, genera un único `.jar` y ejecútalo:

```powershell
.\mvnw.cmd -DskipTests package
java -jar target\gestor-documentos-0.0.1-SNAPSHOT.jar
```

**Primeros pasos**: crea las personas de la familia (*Administrar → Personas*), las secciones
(*Administrar → Secciones*: «DNI», «Casa», «Ayuda alquiler»…), los usuarios, y empieza a subir documentos.

## 5. Acceder desde otros dispositivos de casa

La aplicación escucha en todas las interfaces de red (`server.address: 0.0.0.0`).

1. **Averigua la IP del PC** donde corre la aplicación: `ipconfig` → «Dirección IPv4» del adaptador Wi-Fi
   o Ethernet, por ejemplo `192.168.1.40`.
2. **Abre el puerto en el Firewall de Windows** (PowerShell **como administrador**, una sola vez):

   ```powershell
   New-NetFirewallRule -DisplayName "Documentos de casa" -Direction Inbound -Protocol TCP -LocalPort 8090 -Action Allow -Profile Private
   ```

   La red de casa debe estar marcada como **Privada** en Windows (*Configuración → Red e Internet*).
   **Si desde el móvil no conecta**: la primera vez que arranca, Windows pregunta si deja que Java se comunique
   por la red. Si nadie pulsa «Permitir», crea una regla que **bloquea** `java.exe`, y esa regla gana a la de
   arriba. Para quitarla (PowerShell como administrador):

   ```powershell
   Get-NetFirewallRule -DisplayName "Java(TM) Platform SE binary" | Remove-NetFirewallRule
   ```
3. Desde el móvil, conectado a la misma Wi-Fi, abre `http://192.168.1.40:8090`.
4. **Recomendado**: en el router, reserva esa IP para el PC (*DHCP estático* / *reserva de IP*); si no,
   puede cambiar al reiniciar. En el móvil puedes añadir la página a la pantalla de inicio.

Desde el móvil, el botón **«Hacer foto»** de *Subir* abre directamente la cámara.

> **Seguridad**: la aplicación usa HTTP sin cifrar. Dentro de casa suele ser aceptable, pero cualquiera
> conectado a tu Wi-Fi podría, con conocimientos, ver el tráfico. No la expongas a internet
> (no abras el puerto en el router). Si en el futuro quieres HTTPS, se puede añadir un certificado autofirmado.

## 6. Copias de seguridad

Hay que copiar **las dos cosas**: la base de datos (metadatos) y la carpeta de ficheros.

```powershell
& "C:\Program Files\PostgreSQL\17\bin\pg_dump.exe" -U gestordocs -F c -f copia-gestordocs.dump gestordocs
Compress-Archive -Path datos\ficheros -DestinationPath copia-ficheros.zip
```

Restaurar: `pg_restore -U gestordocs -d gestordocs --clean copia-gestordocs.dump` y descomprimir la carpeta.

## 7. Tests

```powershell
$env:DB_PASSWORD = [Environment]::GetEnvironmentVariable('DB_PASSWORD','User'); .\mvnw.cmd test
```

Usan la base de datos `gestordocs_test` (cada test deshace sus cambios) y la carpeta `target/ficheros-test`.
Incluyen:

- **Arquitectura** (`ModularidadTest`): ningún módulo usa clases internas de otro y no hay ciclos.
- **Login y seguridad**: login, usuario inactivo, cambio de contraseña obligatorio, CSRF.
- **Permisos** (`PermisosTest`): un USER no puede subir, editar ni borrar, ni en los services ni por la web.
- **Servicios**: subida (tipo real, tamaño, miniatura, ficheros huérfanos), edición, borrado de personas y
  secciones, búsqueda sin tildes, ZIP.
- **Pantallas** (`PantallasTest`): renderiza todas las páginas con datos reales.

La carpeta `muestras/` tiene ficheros para probar a mano (fotos, PDFs, un TXT, un ejecutable disfrazado de
`.jpg` que debe rechazarse y uno de 21 MB que supera el límite).

## 8. Arquitectura

Monolito modular organizado **por dominio**, verificado con Spring Modulith:

```
es.familia.gestordocs
├── compartido      excepciones comunes
├── usuarios        cuentas, login, seguridad, admin inicial
├── personas        miembros de la familia
├── secciones       categorías de documentos
├── almacenamiento  StorageService (interfaz) + implementación en disco
├── documentos      subida, consulta, descarga, búsqueda, miniaturas, ZIP
└── portal          pantallas que combinan varios módulos (inicio, ficha de persona, borrados)
```

Reglas:

- En el paquete raíz de cada módulo está su **API pública** (service, DTOs, eventos). Los subpaquetes
  `internal` (entidades JPA, repositorios) y `web` (controllers) no se usan desde fuera.
- Entre módulos solo hay **referencias por id** (`Documento.personaId`), sin relaciones JPA ni claves foráneas.
  Al borrar una persona o sección se publica un evento (`PersonaEliminadaEvent`, `SeccionEliminadaEvent`) y los
  módulos afectados reaccionan dentro de la misma transacción.
- Toda la lógica está en los **services**, que reciben y devuelven DTOs y declaran sus permisos con
  `@PreAuthorize`. Los controllers solo llaman al service y preparan la vista. Para añadir una API REST
  (por ejemplo para una SPA) bastaría con crear `@RestController` que llamen a los mismos services.
- Los ficheros se guardan con un nombre interno UUID detrás de `StorageService`; cambiar a MinIO o S3 sería
  escribir otra implementación de esa interfaz.
- Los ficheros nunca se sirven como estáticos: todas las descargas pasan por un controller que exige sesión.
