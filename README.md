# CCDigital

Plataforma web para gestion de identidad y documentos ciudadanos con tres modulos funcionales:

- `Admin (Gobierno)`: alta de personas, carga/revision de documentos, gobierno de acceso, reportes y sincronizacion.
- `Emisor`: carga de documentos (PDF), creacion de solicitudes de acceso y consulta de documentos autorizados.
- `Usuario final`: registro, login con prueba Indy + segundo factor, aprobacion/rechazo de solicitudes y consulta de documentos.

La aplicacion es un monolito Spring Boot que integra:

- MySQL (persistencia principal)
- Sistema de archivos local (almacen de documentos)
- Hyperledger Fabric (registro documental y auditoria)
- Indy/ACA-Py (pruebas de credencial y sincronizacion de estado)

## 1) Stack tecnico

- Java 17
- Spring Boot 3.5.11
- Spring MVC + Thymeleaf
- Spring Security
- Spring Data JPA (Hibernate)
- MySQL 8
- OpenHTMLToPDF + XChart (reportes PDF)
- Scripts externos Node.js/Python para integraciones blockchain

Dependencias definidas en [pom.xml](./pom.xml).

## 2) Estructura del proyecto

```text
src/main/java/co/edu/unbosque/ccdigital
  config/        Configuracion (security, properties, filtros)
  controller/    Endpoints MVC/REST de los modulos
  dto/           Formularios y vistas de datos
  entity/        Modelo JPA (tablas)
  repository/    Repositorios Spring Data
  security/      Principals de seguridad
  service/       Logica de negocio e integraciones

src/main/resources
  application.properties
  templates/     Vistas Thymeleaf (admin, auth, issuer, user)
  static/        CSS, JS, assets

docs/sql
  add-user-totp-columns.sql
  add-user-access-state-columns.sql
```

## 3) Arquitectura funcional

### 3.1 Modulo Admin

Endpoints base:

- `/admin/dashboard`
- `/admin/persons`
- `/admin/persons/{id}`
- `/admin/persons/{id}/upload`
- `/admin/person-documents/{id}/review`
- `/admin/persons/{id}/access-state`
- `/admin/sync`
- `/admin/reports`

Funciones clave:

- Crear persona y carpeta fisica asociada (`PersonService#createPersonAndFolder`).
- Cargar documento para persona (queda `PENDING` para revision).
- Aprobar/rechazar documentos (`ReviewStatus`).
- Cambiar estado de acceso del usuario (`ENABLED/SUSPENDED/DISABLED`) y sincronizar con Indy.
- Ejecutar sync de Fabric/Indy desde UI usando `ExternalToolsService`.
- Generar reportes analiticos y trazabilidad blockchain (incluye PDF).

### 3.2 Modulo Emisor

Endpoints base:

- `/issuer`
- `/issuer/upload`
- `/issuer/access-requests`
- `/issuer/access-requests/new`
- `/issuer/access-requests/{requestId}/documents/{personDocumentId}/view`
- `/issuer/access-requests/{requestId}/documents/{personDocumentId}/download`

Funciones clave:

- Buscar persona por tipo y numero.
- Cargar documento solo PDF (validacion por extension, MIME y firma `%PDF`).
- Documento cargado queda en estado de revision `PENDING`.
- Crear solicitudes de acceso para documentos aprobados.
- Consultar documentos solo cuando solicitud esta `APROBADA` y vigente.

### 3.3 Modulo Usuario final

Endpoints base:

- Registro: `/register/user`
- Login UI: `/login/user`
- Login API: `/user/auth/start`, `/user/auth/poll`, `/user/auth/otp/verify`, `/user/auth/otp/resend`
- Dashboard: `/user/dashboard`
- Solicitudes: `/user/requests`
- Documentos: `/user/docs/view/{docId}`, `/user/docs/download/{docId}`
- MFA TOTP: `/user/mfa/totp/*`
- Recuperacion clave: `/user/auth/forgot/*`

Funciones clave:

- Registro vinculado a `persons` existente (no crea persona desde cero en este flujo).
- Normalizacion de datos y validaciones fuertes de password.
- Verificacion de correo por OTP en registro.
- Activacion opcional de TOTP al registrar.
- Sincronizacion automatica de estado activo tras registro exitoso.
- Login con flujo Indy (present-proof) + segundo factor (TOTP o email OTP).

## 4) Seguridad

Configurada en `SecurityConfig` con cadenas separadas:

- Admin: `/admin/**` + `/login/admin`
- Emisor: `/issuer/**` + `/login/issuer`
- Usuario: `/user/**` + `/login/user`
- API interna: `/api/**` solo admin
- Publico: `/`, `/login/*`, `/register/user/**`, recursos estaticos

Controles implementados:

- Roles: `ROLE_GOBIERNO`, `ROLE_ISSUER`, `ROLE_USER`
- Sesion por inactividad (server + cliente)
- Endpoints de keepalive/expire de sesion
- Rate-limit en endpoints sensibles (`SensitiveEndpointRateLimitFilter`)
- CSP + headers de seguridad (HSTS, frame same-origin, referrer-policy, etc.)
- URLs firmadas para apertura/descarga de documentos (`SignedUrlService`)
- Validacion de rutas fisicas permitidas para documentos de usuario

## 5) Modelo de datos (MySQL)

Tablas principales:

- `persons`
- `users`
- `entity_users`
- `entities`
- `documents`
- `categories`
- `person_documents`
- `files`
- `access_requests`
- `access_request_items`
- `consents`
- `audit_events`
- `companies`
- `company_document_definitions`
- `entity_document_definitions`

Vistas:

- `v_documents`
- `v_person_full_documents`

Rutinas/procedimientos:

- `sp_add_person_document`
- `sp_create_user_with_person`
- `sp_upload_file_path`
- `sp_upload_pdf_blob`

Trigger:

- `trg_files_autoversion` (auto-versiona registros en `files`)

Relaciones importantes:

- `users.person_id -> persons.id`
- `person_documents.person_id -> persons.id`
- `person_documents.document_id -> documents.id`
- `files.person_document_id -> person_documents.id`
- `access_requests.entity_id -> entities.id`
- `access_requests.person_id -> persons.id`
- `access_request_items.access_request_id -> access_requests.id`
- `access_request_items.person_document_id -> person_documents.id`

## 6) Almacenamiento de archivos

Servicio: `FileStorageService`

- Base path: `ccdigital.fs.base-path` (env `CCDIGITAL_FS_BASE_PATH`)
- Crea carpeta por persona con nombre normalizado
- Guarda archivo fisico y metadatos (`sha256`, `size`, `path`) en `files`
- En consulta de usuario, se valida que el archivo este dentro de directorios permitidos

## 7) Integracion Fabric

Servicios Java involucrados:

- `ExternalToolsService`
- `FabricLedgerCliService`
- `FabricAuditCliService`
- `BlockchainTraceDetailService`

Scripts esperados (Node.js) en `FABRIC_WORKDIR`:

- `list-docs.js`
- `read-block-by-ref.js`
- `record-access-event.js`
- `list-access-events.js`
- `sync-db-to-ledger.js`

Uso funcional:

- Listado de documentos para usuario final desde ledger (`list-docs.js`).
- Auditoria de accesos y verificaciones en Fabric (`record-access-event.js`).
- Sync BD -> ledger (global o por persona).

## 8) Integracion Indy / ACA-Py

Servicios Java involucrados:

- `IndyAdminClient`
- `IndyProofLoginService`
- `UserAuthFlowService`
- `UserAccessGovernanceService`

Uso funcional:

- Login con present-proof 2.0 en ACA-Py.
- Extraccion de atributos verificados (`id_type`, `id_number`, `first_name`, `last_name`, `email`).
- Sincronizacion de estado de acceso de usuario hacia metadata de conexion ACA-Py.
- Emision de credenciales desde script Python (`issue_credentials_from_db.py`).

## 9) Configuracion por variables de entorno

`application.properties` esta mapeado para ejecutarse por variables de entorno.

### 9.1 App y servidor

- `APP_NAME` -> `spring.application.name`
- `SERVER_PORT` -> `server.port`
- `SERVER_SESSION_TIMEOUT` -> `server.servlet.session.timeout`

### 9.2 Base de datos

- `DB_URL` -> `spring.datasource.url`
- `DB_USERNAME` -> `spring.datasource.username`
- `DB_PASSWORD` -> `spring.datasource.password`
- `JPA_DDL_AUTO` -> `spring.jpa.hibernate.ddl-auto`
- `JPA_SHOW_SQL` -> `spring.jpa.show-sql`
- `JPA_FORMAT_SQL` -> `spring.jpa.properties.hibernate.format_sql`

### 9.3 Archivos

- `CCDIGITAL_FS_BASE_PATH` -> `ccdigital.fs.base-path`
- `CCDIGITAL_FS_BASE_PATH` tambien alimenta `app.user-files-base-dir`

Opcional:

- `APP_USER_FILES_LEGACY_BASE_DIR` -> `app.user-files-legacy-base-dir`

### 9.4 ACA-Py / Indy

- `ACAPY_VERIFIER_ADMIN_URL` -> `acapy.verifier.admin`
- `ACAPY_HOLDER_ADMIN_URL` -> `acapy.holder.admin`
- `ACAPY_CRED_DEF_ID` -> `acapy.cred-def-id`
- `ACAPY_PROOF_POLL_INTERVAL_MS` -> `acapy.proof.poll-interval-ms`
- `ACAPY_PROOF_POLL_TIMEOUT_MS` -> `acapy.proof.poll-timeout-ms`

- `INDY_ISSUER_ADMIN_URL` -> `ccdigital.indy.issuer-admin-url`
- `INDY_HOLDER_ADMIN_URL` -> `ccdigital.indy.holder-admin-url`
- `INDY_HOLDER_CONNECTION_ID` -> `ccdigital.indy.holder-connection-id`
- `INDY_HOLDER_LABEL` -> `ccdigital.indy.holder-label`
- `INDY_CRED_DEF_ID` -> `ccdigital.indy.cred-def-id`
- `INDY_ADMIN_API_KEY` -> `ccdigital.indy.admin-api-key`
- `INDY_USER_ACCESS_SYNC_ENABLED` -> `ccdigital.indy.user-access-sync-enabled`
- `INDY_USER_ACCESS_SYNC_PATH` -> `ccdigital.indy.user-access-sync-path`

### 9.5 Herramientas externas (Fabric/Indy)

- `FABRIC_WORKDIR` -> `external-tools.fabric.workdir`
- `FABRIC_NODE_BIN` -> `external-tools.fabric.node-bin`
- `FABRIC_LIST_DOCS_SCRIPT` -> `external-tools.fabric.list-docs-script`
- `FABRIC_BLOCK_READER_SCRIPT` -> `external-tools.fabric.block-reader-script`
- `FABRIC_RECORD_ACCESS_SCRIPT` -> `external-tools.fabric.record-access-script`
- `FABRIC_LIST_ACCESS_SCRIPT` -> `external-tools.fabric.list-access-script`
- `FABRIC_SYNC_ALL_SCRIPT` -> `external-tools.fabric.sync-all-script`
- `FABRIC_SYNC_PERSON_SCRIPT` -> `external-tools.fabric.sync-person-script`

- `INDY_TOOLS_WORKDIR` -> `external-tools.indy.workdir`
- `INDY_VENV_ACTIVATE` -> `external-tools.indy.venv-activate`
- `INDY_SCRIPT` -> `external-tools.indy.script`
- `EXTERNAL_TOOLS_TIMEOUT_SECONDS` -> `external-tools.default-timeout-seconds`

### 9.6 Correo y recuperacion de clave

- `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`
- `MAIL_SMTP_AUTH`, `MAIL_SMTP_STARTTLS_ENABLE`, `MAIL_SMTP_STARTTLS_REQUIRED`
- `FORGOT_PASSWORD_MAIL_FROM` -> `app.security.forgot-password.mail.from`
- `MAIL_TEST_CONNECTION` -> `spring.mail.test-connection`

### 9.7 Seguridad avanzada (opcionales)

- `APP_SECURITY_REQUIRE_HTTPS`
- `APP_SECURITY_SIGNED_URLS_SECRET`
- `APP_SECURITY_SIGNED_URLS_TTL_SECONDS`
- `APP_SECURITY_RATE_LIMIT_ENABLED`
- `APP_SECURITY_RATE_LIMIT_WINDOW_SECONDS`
- `APP_SECURITY_RATE_LIMIT_MAX_REQUESTS_PER_WINDOW`

- `APP_SECURITY_LOGIN_OTP_CODE_LENGTH`
- `APP_SECURITY_LOGIN_OTP_CODE_TTL_MINUTES`
- `APP_SECURITY_LOGIN_OTP_MAX_ATTEMPTS`
- `APP_SECURITY_LOGIN_OTP_RESEND_COOLDOWN_SECONDS`
- `APP_SECURITY_LOGIN_OTP_MAIL_FROM`

- `APP_SECURITY_REGISTER_EMAIL_OTP_CODE_LENGTH`
- `APP_SECURITY_REGISTER_EMAIL_OTP_CODE_TTL_MINUTES`
- `APP_SECURITY_REGISTER_EMAIL_OTP_MAX_ATTEMPTS`
- `APP_SECURITY_REGISTER_EMAIL_OTP_RESEND_COOLDOWN_SECONDS`
- `APP_SECURITY_REGISTER_EMAIL_OTP_MAIL_FROM`

- `APP_SECURITY_FORGOT_PASSWORD_CODE_LENGTH`
- `APP_SECURITY_FORGOT_PASSWORD_CODE_TTL_MINUTES`
- `APP_SECURITY_FORGOT_PASSWORD_MAX_ATTEMPTS`
- `APP_SECURITY_FORGOT_PASSWORD_RESEND_COOLDOWN_SECONDS`

- `APP_SECURITY_TOTP_ISSUER`
- `APP_SECURITY_TOTP_ISSUER_NAME`
- `APP_SECURITY_TOTP_DIGITS`
- `APP_SECURITY_TOTP_CODE_DIGITS`
- `APP_SECURITY_TOTP_PERIOD_SECONDS`
- `APP_SECURITY_TOTP_WINDOW_STEPS`
- `APP_SECURITY_TOTP_SECRET_BYTES`

> Nota: Spring Boot usa relaxed binding, por eso una propiedad como
> `app.security.signed-urls.secret` puede mapearse con env `APP_SECURITY_SIGNED_URLS_SECRET`.

## 10) Ejemplo de archivo de entorno (sin secretos reales)

```bash
# Core
export APP_NAME='CCDigital'
export SERVER_PORT='8088'
export SERVER_SESSION_TIMEOUT='30m'

# DB
export DB_URL='jdbc:mysql://localhost:3307/ciudadania_digital?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8'
export DB_USERNAME='root'
export DB_PASSWORD='REEMPLAZAR'
export JPA_DDL_AUTO='none'
export JPA_SHOW_SQL='false'
export JPA_FORMAT_SQL='false'

# Files
export CCDIGITAL_FS_BASE_PATH='/opt/ccdigital-prod/storage'

# Indy / ACA-Py
export INDY_ISSUER_ADMIN_URL='http://localhost:8021'
export INDY_HOLDER_ADMIN_URL='http://localhost:8031'
export INDY_CRED_DEF_ID='REEMPLAZAR_CRED_DEF_ID'
export INDY_HOLDER_CONNECTION_ID='auto'
export INDY_HOLDER_LABEL='Holder-CDigital'
export INDY_USER_ACCESS_SYNC_ENABLED='true'
export INDY_USER_ACCESS_SYNC_PATH='/connections/{conn_id}/metadata'

# Fabric scripts
export FABRIC_WORKDIR='/home/ccdigital/fabric/fabric-samples/test-network/client'
export FABRIC_NODE_BIN='node'
export FABRIC_LIST_DOCS_SCRIPT='list-docs.js'
export FABRIC_BLOCK_READER_SCRIPT='read-block-by-ref.js'
export FABRIC_RECORD_ACCESS_SCRIPT='record-access-event.js'
export FABRIC_LIST_ACCESS_SCRIPT='list-access-events.js'
export FABRIC_SYNC_ALL_SCRIPT='sync-db-to-ledger.js'
export FABRIC_SYNC_PERSON_SCRIPT='sync-db-to-ledger.js'

# Indy script
export INDY_TOOLS_WORKDIR='/home/ccdigital/cdigital-indy-python'
export INDY_VENV_ACTIVATE='source venv/bin/activate'
export INDY_SCRIPT='issue_credentials_from_db.py'
export EXTERNAL_TOOLS_TIMEOUT_SECONDS='180'

# Mail
export MAIL_HOST='smtp.gmail.com'
export MAIL_PORT='587'
export MAIL_USERNAME='tu_correo'
export MAIL_PASSWORD='tu_app_password'
export MAIL_SMTP_AUTH='true'
export MAIL_SMTP_STARTTLS_ENABLE='true'
export MAIL_SMTP_STARTTLS_REQUIRED='true'
export FORGOT_PASSWORD_MAIL_FROM='recuperacion@ccdigital.com'
export MAIL_TEST_CONNECTION='true'

# Seguridad opcional
export APP_SECURITY_SIGNED_URLS_SECRET='SECRETO_LARGO_ESTABLE'
```

## 11) Arranque local de la aplicacion

1. Cargar variables de entorno.
2. Tener MySQL disponible.
3. Importar backup si aplica:

```bash
mysql -h127.0.0.1 -P3307 -uroot -p'REEMPLAZAR' ciudadania_digital < /home/ccdigital/respaldo_completoCCDIGITAL.sql
```

4. Ejecutar:

```bash
./mvnw spring-boot:run
```

5. O construir jar:

```bash
./mvnw -DskipTests package spring-boot:repackage
java -jar target/CCDigital-1.0.0.jar
```

## 12) Paso a paso de red Indy (resumen integrado)

Basado en tu guia: `/home/ccdigital/CCDigital~/PasoAPasoIndy.txt`.

1. Bajar red Indy anterior (`von-network`) y liberar puertos 8020, 8021, 8030, 8031, 9000.
2. Levantar ledger local (`./manage build` y `./manage up`).
3. Crear red Docker `cdigital-net`.
4. Registrar DID/seed del issuer en el ledger (`/register`).
5. Levantar `acapy-issuer` (8020/8021).
6. Crear schema y cred def en issuer admin API.
7. Levantar `acapy-holder` (8030/8031).
8. Crear invitacion desde issuer/verifier y aceptarla en holder.
9. Verificar que conexion quede `ACTIVE`.
10. Ejecutar script Python de emision desde BD.

Comando de emision:

```bash
cd ~/cdigital-indy-python
source venv/bin/activate
python3 issue_credentials_from_db.py
```

## 13) Paso a paso de red Fabric (resumen integrado)

Basado en tu guia: `/home/ccdigital/CCDigital~/PasoAPasoFabric.txt`.

1. Arrancar Docker.
2. Levantar test-network:

```bash
cd $HOME/fabric/fabric-samples/test-network
./network.sh down
./network.sh up createChannel -c mychannel -ca
```

3. Desplegar chaincode `cddoc`:

```bash
./network.sh deployCC \
  -c mychannel \
  -ccn cddoc \
  -ccp $HOME/CCDigitalBlock/chaincode/cddoc-js \
  -ccl javascript \
  -ccv 1.0 \
  -ccs 1
```

4. Configurar entorno CLI peer (PATH, FABRIC_CFG_PATH, MSP, TLS).
5. Sincronizar BD -> ledger:

```bash
cd $HOME/fabric/fabric-samples/test-network/client
node sync-db-to-ledger.js --all
# o por persona
node sync-db-to-ledger.js --person CC 1019983896
```

6. Consultar docs on-chain:

```bash
node list-docs.js CC 1019983896
```

7. (Opcional) Decodificar bloques con `peer channel fetch` + `configtxlator`.

## 14) Migrations SQL

Archivos:

- [docs/sql/add-user-totp-columns.sql](./docs/sql/add-user-totp-columns.sql)
- [docs/sql/add-user-access-state-columns.sql](./docs/sql/add-user-access-state-columns.sql)

Aplicar sobre la BD objetivo antes de activar funcionalidades nuevas de MFA y gobierno de acceso.

## 15) Despliegue de referencia en VM (actual)

### 15.1 Servicio systemd

Ejemplo (ruta actual usada en entorno productivo):

- Jar: `/opt/ccdigital-prod/app/ccdigital.jar`
- Env: `/opt/ccdigital-prod/config/ccdigital.env`
- Unit: `/etc/systemd/system/ccdigital-prod.service`

Comandos:

```bash
systemctl daemon-reload
systemctl enable --now ccdigital-prod
systemctl status ccdigital-prod --no-pager
journalctl -u ccdigital-prod -f
```

### 15.2 Reverse proxy

Caddy publica `:80` hacia `127.0.0.1:8088`.

### 15.3 Exposicion externa

Puede usarse `ngrok`, `cloudflared` (Cloudflare Tunnel), o VPS+FRP.

## 16) Troubleshooting

### 16.1 Error MySQL definer no existe

Error tipico:

`The user specified as a definer ('CCDigital'@'localhost') does not exist`

Solucion:

```sql
CREATE USER IF NOT EXISTS 'CCDigital'@'localhost' IDENTIFIED BY 'REEMPLAZAR';
GRANT ALL PRIVILEGES ON ciudadania_digital.* TO 'CCDigital'@'localhost';
FLUSH PRIVILEGES;
```

### 16.2 403 al abrir documentos de usuario

Validar:

- `app.user-files-base-dir` apunta al directorio real de archivos.
- El `file_path` existe y es legible.
- La URL firmada no esta expirada (`exp`/`sig`).

### 16.3 Sesion expirada frecuente

Validar:

- `SERVER_SESSION_TIMEOUT` (backend)
- timeout en `ccdigital-loader.js` (frontend idle)
- recarga forzada del navegador luego de desplegar cambios (`Ctrl+F5`)

### 16.4 Fabric no refleja documentos aprobados

Validar:

- Scripts y `FABRIC_WORKDIR` correctos.
- Network y chaincode levantados.
- `sync-db-to-ledger.js --person <idType> <idNumber>` ejecutado sin error.

### 16.5 Login Indy no avanza

Validar:

- `INDY_ISSUER_ADMIN_URL` y `INDY_HOLDER_ADMIN_URL` accesibles.
- `INDY_CRED_DEF_ID` correcto.
- Conexion holder `ACTIVE` (manual o `auto` por label).

## 17) Buenas practicas operativas

- No subir secretos al repositorio.
- Mantener `APP_SECURITY_SIGNED_URLS_SECRET` estable en produccion.
- Mantener `MAIL_PASSWORD` como app password, no password real de correo.
- Versionar y respaldar `ccdigital.env` fuera de git.
- Antes de cambios en BD, tomar backup:

```bash
mysqldump -h127.0.0.1 -P3307 -uroot -p ciudadania_digital > backup_$(date +%F_%H%M%S).sql
```

## 18) Comandos utiles de soporte

```bash
# Build
./mvnw -DskipTests package spring-boot:repackage

# Ejecutar local
./mvnw spring-boot:run

# Estado del servicio
systemctl status ccdigital-prod --no-pager

# Logs
journalctl -u ccdigital-prod -n 200 --no-pager

# Verificar puertos
ss -ltnp | rg ':80|:8088|:8021|:8031|:7051|:7050'

# Ver URL ngrok activa (si aplica)
curl -s http://127.0.0.1:4040/api/tunnels
```

---

Si se modifica el flujo de negocio o las variables, actualizar este README y las guias externas:

- `/home/ccdigital/CCDigital~/PasoAPasoIndy.txt`
- `/home/ccdigital/CCDigital~/PasoAPasoFabric.txt`
