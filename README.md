<div align="center">
  <img src="src/main/resources/static/LogoUniversidad/Logo-u-bosque-01.jpg" alt="Logo Universidad El Bosque" width="240" />
</div>

<h1 align="center">
  <span style="font-family: 'Trebuchet MS', 'Segoe UI', sans-serif;">CCDigital</span>
</h1>

<p align="center">
  <strong>Proyecto investigativo de identidad digital y gestion documental ciudadana</strong><br/>
  <em>Universidad El Bosque</em>
</p>

<p align="center">
  <img alt="Java" src="https://img.shields.io/badge/Java_17-ED8B00?logo=openjdk&logoColor=white" />
  <img alt="Spring Boot" src="https://img.shields.io/badge/Spring_Boot-3.5.11-6DB33F?logo=springboot&logoColor=white" />
  <img alt="MySQL" src="https://img.shields.io/badge/MySQL-8-4479A1?logo=mysql&logoColor=white" />
  <img alt="Docker" src="https://img.shields.io/badge/Docker-2496ED?logo=docker&logoColor=white" />
  <img alt="Fabric" src="https://img.shields.io/badge/Hyperledger-Fabric-2F3134?logo=hyperledger&logoColor=white" />
  <img alt="Indy" src="https://img.shields.io/badge/Hyperledger-Indy-2F3134?logo=hyperledger&logoColor=white" />
</p>

<p align="center">
  <img alt="Tecnologias" src="https://skillicons.dev/icons?i=java,spring,mysql,docker,nodejs,python,linux,git&perline=8" />
</p>

| Campo | Valor |
|---|---|
| Tipo de proyecto | Proyecto investigativo aplicado |
| Universidad | Universidad El Bosque |
| Linea de trabajo | Identidad digital, trazabilidad y gobierno de acceso documental |
| Codigo del proyecto | `CCD-INV-2026` *(actualizar si el codigo oficial es otro)* |
| Integrantes | Yeison Hernandez Huertas, Danniel Parrado *(completar nombres oficiales)* |

Plataforma web para gestion de identidad y documentos ciudadanos con tres modulos funcionales:

- `Admin (Gobierno)`: alta de personas, carga/revision de documentos, gobierno de acceso, reportes y sincronizacion.
- `Emisor`: carga de documentos (PDF), creacion de solicitudes de acceso y consulta de documentos autorizados.
- `Usuario final`: registro, login con prueba Indy + segundo factor, aprobacion/rechazo de solicitudes y consulta de documentos.

La aplicacion es un monolito Spring Boot que integra:

- MySQL (persistencia principal)
- Sistema de archivos local (almacen de documentos)
- Hyperledger Fabric (registro documental y auditoria)
- Indy/ACA-Py (pruebas de credencial y sincronizacion de estado)

## 0) Base documental y control de adopcion (Indy, ACA-Py y Fabric)

Esta seccion documenta de forma explicita de donde sale la base tecnica de blockchain, que se adopto y como se usa en el sistema.

### 0.1 Fuentes de referencia tecnicas

- Hyperledger Fabric (documentacion oficial): `https://hyperledger-fabric.readthedocs.io/`
- Fabric Samples (repositorio oficial): `https://github.com/hyperledger/fabric-samples`
- ACA-Py (documentacion oficial): `https://aca-py.org/`
- ACA-Py (repositorio oficial): `https://github.com/openwallet-foundation/acapy`
- Aries RFCs (protocolo de interoperabilidad): `https://github.com/hyperledger/aries-rfcs`
- Hyperledger Indy (documentacion tecnica): `https://hyperledger-indy.readthedocs.io/`

### 0.2 Criterio de adopcion

Se aplico el siguiente criterio para incorporar tecnologia al proyecto:

1. Priorizar documentacion oficial y especificaciones del protocolo.
2. Validar cada flujo con pruebas tecnicas controladas (entorno local de laboratorio).
3. Encapsular la logica en servicios del backend, evitando dependencia directa del frontend a APIs blockchain.
4. Registrar variables, endpoints y scripts requeridos para reproducibilidad operativa.

### 0.3 Trazabilidad de uso en CCDigital

- Fabric se usa para trazabilidad de documentos y eventos de acceso.
- ACA-Py/Indy se usa para pruebas de credencial en login y sincronizacion de estado del usuario.
- La BD MySQL sigue siendo la fuente transaccional principal.
- Blockchain funciona como capa de confianza adicional (auditoria, verificacion y evidencia).

### 0.4 Evidencia de control tecnico

Para auditoria interna del proyecto se recomienda mantener una matriz simple por flujo:

- `Flujo`: login verificable, sync de estado, registro de acceso, consulta de documentos.
- `Fuente tecnica`: URL oficial, RFC o guia de referencia.
- `Implementacion`: servicio/clase/script donde quedo aplicado.
- `Prueba`: comando o caso funcional que valida el flujo.
- `Resultado`: evidencia de ejecucion (log, respuesta API, estado en BD/ledger).

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

Scripts esperados (Node.js):

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

## 10) Plantilla de entorno

```bash
# APP_NAME: nombre logico de la aplicacion
export APP_NAME='NOMBRE_APP'

# SERVER_PORT: puerto HTTP donde correra Spring Boot
export SERVER_PORT='PUERTO_APP'

# SERVER_SESSION_TIMEOUT: tiempo maximo de inactividad de sesion (ej: 30m)
export SERVER_SESSION_TIMEOUT='TIMEOUT_SESION'

# DB_URL: cadena JDBC completa (host, puerto, nombre BD y parametros)
export DB_URL='jdbc:mysql://<HOST_DB>:<PUERTO_DB>/<NOMBRE_BD>?<PARAMETROS>'

# DB_USERNAME: usuario tecnico con permisos sobre la BD de la app
export DB_USERNAME='USUARIO_BD'

# DB_PASSWORD: clave del usuario tecnico de BD
export DB_PASSWORD='CLAVE_BD'

# JPA_DDL_AUTO: estrategia Hibernate (none/validate/update/create)
export JPA_DDL_AUTO='MODO_DDL'

# JPA_SHOW_SQL: true/false para log de consultas SQL
export JPA_SHOW_SQL='BOOLEANO'

# JPA_FORMAT_SQL: true/false para formatear SQL en logs
export JPA_FORMAT_SQL='BOOLEANO'

# CCDIGITAL_FS_BASE_PATH: directorio absoluto donde se almacenan archivos
export CCDIGITAL_FS_BASE_PATH='DIRECTORIO_ALMACEN'

# ACAPY_VERIFIER_ADMIN_URL: URL de admin API del agente verificador
export ACAPY_VERIFIER_ADMIN_URL='URL_VERIFIER_ADMIN'

# ACAPY_HOLDER_ADMIN_URL: URL de admin API del agente holder
export ACAPY_HOLDER_ADMIN_URL='URL_HOLDER_ADMIN'

# ACAPY_CRED_DEF_ID: identificador de credencial emitida por la red Indy
export ACAPY_CRED_DEF_ID='CRED_DEF_ID'

# ACAPY_PROOF_POLL_INTERVAL_MS: intervalo de consulta de estado de proof
export ACAPY_PROOF_POLL_INTERVAL_MS='INTERVALO_MS'

# ACAPY_PROOF_POLL_TIMEOUT_MS: timeout maximo de espera de proof
export ACAPY_PROOF_POLL_TIMEOUT_MS='TIMEOUT_MS'

# INDY_ISSUER_ADMIN_URL: endpoint del agente issuer para operaciones administrativas
export INDY_ISSUER_ADMIN_URL='URL_ISSUER_ADMIN'

# INDY_HOLDER_ADMIN_URL: endpoint del agente holder para operaciones administrativas
export INDY_HOLDER_ADMIN_URL='URL_HOLDER_ADMIN'

# INDY_HOLDER_CONNECTION_ID: ID de conexion holder (o modo auto segun implementacion)
export INDY_HOLDER_CONNECTION_ID='CONNECTION_ID_O_AUTO'

# INDY_HOLDER_LABEL: etiqueta usada para resolver conexion holder
export INDY_HOLDER_LABEL='LABEL_HOLDER'

# INDY_CRED_DEF_ID: credencial a usar en login/sincronizacion
export INDY_CRED_DEF_ID='CRED_DEF_ID'

# INDY_ADMIN_API_KEY: API key de proteccion para admin API (si aplica)
export INDY_ADMIN_API_KEY='API_KEY'

# INDY_USER_ACCESS_SYNC_ENABLED: habilita/deshabilita sincronizacion de estado a Indy
export INDY_USER_ACCESS_SYNC_ENABLED='BOOLEANO'

# INDY_USER_ACCESS_SYNC_PATH: ruta de metadata para estado de usuario en ACA-Py
export INDY_USER_ACCESS_SYNC_PATH='RUTA_METADATA'

# FABRIC_WORKDIR: directorio base donde vive el cliente/script de Fabric
export FABRIC_WORKDIR='DIRECTORIO_FABRIC_CLIENTE'

# FABRIC_NODE_BIN: ejecutable Node.js disponible en el sistema
export FABRIC_NODE_BIN='BINARIO_NODE'

# FABRIC_LIST_DOCS_SCRIPT: script para listar documentos de una persona en ledger
export FABRIC_LIST_DOCS_SCRIPT='SCRIPT_LIST_DOCS'

# FABRIC_BLOCK_READER_SCRIPT: script para leer detalle de bloque/transaccion
export FABRIC_BLOCK_READER_SCRIPT='SCRIPT_BLOCK_READER'

# FABRIC_RECORD_ACCESS_SCRIPT: script para registrar eventos de acceso en ledger
export FABRIC_RECORD_ACCESS_SCRIPT='SCRIPT_RECORD_ACCESS'

# FABRIC_LIST_ACCESS_SCRIPT: script para listar auditoria de accesos
export FABRIC_LIST_ACCESS_SCRIPT='SCRIPT_LIST_ACCESS'

# FABRIC_SYNC_ALL_SCRIPT: script para sincronizacion masiva BD -> ledger
export FABRIC_SYNC_ALL_SCRIPT='SCRIPT_SYNC_ALL'

# FABRIC_SYNC_PERSON_SCRIPT: script para sincronizacion por persona BD -> ledger
export FABRIC_SYNC_PERSON_SCRIPT='SCRIPT_SYNC_PERSON'

# INDY_TOOLS_WORKDIR: directorio base de utilidades Python para emision
export INDY_TOOLS_WORKDIR='DIRECTORIO_INDY_TOOLS'

# INDY_VENV_ACTIVATE: comando para activar entorno virtual Python
export INDY_VENV_ACTIVATE='COMANDO_ACTIVACION_VENV'

# INDY_SCRIPT: script principal de emision/sincronizacion de credenciales
export INDY_SCRIPT='SCRIPT_INDY'

# EXTERNAL_TOOLS_TIMEOUT_SECONDS: timeout maximo de ejecucion de scripts externos
export EXTERNAL_TOOLS_TIMEOUT_SECONDS='TIMEOUT_SEGUNDOS'

# MAIL_HOST: servidor SMTP
export MAIL_HOST='SMTP_HOST'

# MAIL_PORT: puerto SMTP
export MAIL_PORT='SMTP_PUERTO'

# MAIL_USERNAME: cuenta remitente para OTP/recuperacion
export MAIL_USERNAME='SMTP_USUARIO'

# MAIL_PASSWORD: clave o app-password del servicio SMTP
export MAIL_PASSWORD='SMTP_CLAVE'

# MAIL_SMTP_AUTH: habilita autenticacion SMTP
export MAIL_SMTP_AUTH='BOOLEANO'

# MAIL_SMTP_STARTTLS_ENABLE: habilita STARTTLS
export MAIL_SMTP_STARTTLS_ENABLE='BOOLEANO'

# MAIL_SMTP_STARTTLS_REQUIRED: obliga STARTTLS
export MAIL_SMTP_STARTTLS_REQUIRED='BOOLEANO'

# FORGOT_PASSWORD_MAIL_FROM: remitente visible en recuperacion de clave
export FORGOT_PASSWORD_MAIL_FROM='MAIL_FROM'

# MAIL_TEST_CONNECTION: valida conexion SMTP al iniciar
export MAIL_TEST_CONNECTION='BOOLEANO'
```

## 11) Arranque local de la aplicacion

1. Cargar variables de entorno obligatorias.
2. Tener MySQL disponible y la base inicializada.
3. Importar backup si aplica:

```bash
mysql -h<HOST_DB> -P<PUERTO_DB> -u<USUARIO_BD> -p'<CLAVE_BD>' <NOMBRE_BD> < <ARCHIVO_BACKUP_SQL>
```

4. Ejecutar en modo desarrollo:

```bash
./mvnw spring-boot:run
```

5. O construir y correr jar:

```bash
./mvnw -DskipTests package spring-boot:repackage
java -jar target/<ARTEFACTO>.jar
```

## 12) Montaje general de red Indy + ACA-Py

Objetivo: tener issuer y holder operativos para emitir credenciales y ejecutar login por prueba verificable.

### 12.1 Prerrequisitos

- Linux o WSL2 con Docker y Docker Compose.
- Python 3.10+ y `venv` para scripts de emision.
- `curl` y `jq` para pruebas de API.
- Puertos libres para ledger y agentes (segun tu arquitectura).

### 12.2 Descarga de componentes base

1. Obtener una implementacion de ledger Indy para laboratorio local.
2. Descargar imagenes de ACA-Py (issuer y holder) compatibles con la version de ledger.
3. Crear una red Docker dedicada para aislar servicios.

### 12.3 Levantar ledger y registrar identidad emisora

1. Iniciar los nodos del ledger.
2. Validar salud del ledger.
3. Registrar DID/seed del issuer con permisos de escritura.

### 12.4 Levantar agentes ACA-Py

1. Iniciar agente issuer con wallet, endpoint y admin API.
2. Iniciar agente holder con wallet, endpoint y admin API.
3. Verificar conectividad entre agentes y estado `ready`.

### 12.5 Configurar credenciales

1. Crear schema en el issuer.
2. Crear credential definition asociada.
3. Guardar `cred_def_id` en configuracion de la aplicacion.

### 12.6 Establecer conexion y emitir

1. Generar invitacion desde issuer.
2. Aceptar invitacion desde holder.
3. Confirmar conexion activa.
4. Ejecutar script de emision desde BD para usuarios elegibles.

### 12.7 Verificacion operativa

- Probar login con present-proof.
- Confirmar extraccion de atributos esperados.
- Revisar logs de agente issuer/holder y backend.

## 13) Montaje general de red Hyperledger Fabric

Objetivo: tener una red operativa para registrar y consultar trazabilidad documental.

### 13.1 Prerrequisitos

- Docker + Docker Compose.
- `curl`, `git`, `jq`.
- Node.js LTS para scripts cliente.
- Binarios de Fabric (`peer`, `orderer`, `configtxlator`) segun version seleccionada.

### 13.2 Descarga e instalacion

1. Clonar `fabric-samples`.
2. Ejecutar script de bootstrap oficial para descargar binarios e imagenes.
3. Verificar version de binarios y compatibilidad de imagenes.

### 13.3 Levantar red base

1. Apagar cualquier red previa de prueba.
2. Levantar red con autoridades certificadoras.
3. Crear canal de negocio.
4. Confirmar peers/orderer en estado saludable.

### 13.4 Desplegar chaincode

1. Empaquetar chaincode.
2. Instalar y aprobar chaincode en organizaciones requeridas.
3. Hacer commit de definicion de chaincode en canal.
4. Validar invocacion y consulta basica.

### 13.5 Configurar cliente de integracion

1. Definir identidad MSP usada por scripts.
2. Configurar variables de entorno de peer TLS/MSP.
3. Preparar scripts Node.js para sync y consulta.

### 13.6 Sincronizacion y consulta

- Ejecutar sincronizacion total o por persona de BD hacia ledger.
- Consultar documentos on-chain y validar que coincidan con BD.
- Registrar eventos de acceso para trazabilidad.

### 13.7 Verificacion operativa

- Revisar logs de peers/orderer/chaincode.
- Validar que IDs y estados en ledger sean consistentes con la aplicacion.
