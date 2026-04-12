# CCDigital: Consolidado de evidencia para Results

Fecha de consolidación: `2026-03-24`

Este consolidado se reconstruyó con evidencia operativa real del despliegue activo, no con artefactos formales de una suite de pruebas preservada. La regla de clasificación usada aquí fue:

- `Passed`: existe evidencia operativa directa del escenario en ejecución.
- `Partially passed`: existe implementación y/o evidencia indirecta, pero no quedó artefacto autónomo suficiente para probar el escenario completo.
- `Failed`: el comportamiento observado contradice el resultado esperado.

## 1. Matriz real de resultados funcionales e integración

| Scenario | Expected outcome | Observed outcome | Status | Evidence | Notes |
|---|---|---|---|---|---|
| Registro de usuario | Se crea la cuenta final y queda habilitada en la plataforma. | Se recuperaron 4 cuentas de usuario final creadas entre `2026-03-05` y `2026-03-06` (`users.id` 35-38). Para varios casos existe evento Fabric `USER_ACCESS_STATE_CHANGE` con razón `Registro web de usuario`. | Passed | DB + Fabric | No se recuperó grabación/captura del formulario, pero sí resultado persistido. |
| Verificación de cuenta | La cuenta solo se materializa después de validar OTP de correo. | El flujo de código exige `verifyCode(...)` antes de crear/sincronizar el usuario, pero no se localizaron tablas ni logs históricos con los OTP emitidos o confirmados. | Partially passed | Code + DB | Hay evidencia de diseño y de resultado, no evidencia autónoma del paso OTP. |
| Login | El usuario autentica correo/clave y entra al flujo SSI. | ACA-Py conserva 26 pruebas `done=true` y `verified=true`. Sin embargo, el `2026-03-24` el endpoint `POST /user/auth/start` respondió `302` hacia `/login/user?expired=true` en 10/10 corridas de Newman. | Partially passed | Indy + Newman + Code | La verificación SSI sí ocurrió históricamente; la entrada HTTP actual no confirmó cierre del login completo. |
| Segundo factor | Tras la prueba SSI se exige TOTP u OTP por correo. | 3 de 4 cuentas de usuario final tienen `totp_enabled=1` y `totp_confirmed_at`. El código selecciona `totp` o `email` después del proof, pero no se recuperó un evento persistido de 2FA exitoso completando sesión. | Partially passed | DB + Code | La capacidad está integrada; la evidencia histórica del uso exitoso del segundo factor es incompleta. |
| Recuperación de contraseña | `POST /user/auth/forgot/verify` devuelve JSON genérico `200` y permite continuar al reset. | El controlador implementa respuesta `200`, pero el `2026-03-24` el endpoint respondió `302` hacia `/login/user?expired=true` en 10/10 corridas. No se recuperó evidencia de reset exitoso. | Failed | Code + Newman | Hay desajuste entre contrato esperado y comportamiento observado en despliegue. |
| Carga de documento | El PDF se almacena off-chain con hash e índice asociado al ciudadano. | Existen 43 registros en `files`; todos los muestreados están como `stored_as=PATH`, con `content=NULL`. Se verificaron 35 archivos físicos en el almacenamiento. | Passed | DB + filesystem | Los binarios PDF no quedaron en Fabric. |
| Revisión/aprobación | El documento cambia a `APPROVED`/`REJECTED` y conserva marca temporal de revisión. | `person_documents.review_status`: 10 `APPROVED`, 4 `REJECTED`, 30 `PENDING`. Los revisados tienen `reviewed_at` diligenciado. | Passed | DB | `reviewed_by_user` está `NULL`, así que falta identidad del revisor. |
| Solicitud de acceso | El emisor crea una solicitud para documentos del ciudadano. | Hay 6 solicitudes registradas. Ejemplo: request 6 creada el `2026-03-06 23:35:31`. Fabric registra `REQUEST_CREATED` para las solicitudes activas. | Passed | DB + Fabric | No quedaron solicitudes pendientes al corte. |
| Aprobación o rechazo del ciudadano | El ciudadano decide aprobar o rechazar la solicitud. | `access_requests`: 4 `APROBADA`, 2 `RECHAZADA`. Journald registra `Solicitud decidida` para requests 2, 3, 4, 5 y 6. | Passed | DB + journalctl + Fabric | El consentimiento está embebido en la decisión de la solicitud, no en la tabla `consents`. |
| Consulta autorizada | Solo una solicitud aprobada habilita la visualización del documento solicitado. | Fabric registra `DOC_VIEW_GRANTED` y journald registra `Consulta autorizada de documento` para requests 3, 4 y 6. En request 6 la aprobación fue a las `23:36:01` y la consulta autorizada a las `23:36:17` del `2026-03-06`. | Passed | DB + Fabric + journalctl | Buen caso de autorización explícita seguida de acceso efectivo. |
| Sincronización con blockchain | Los documentos aprobados y eventos de acceso quedan sincronizados en Fabric. | `list-docs.js` devuelve documentos on-chain para ciudadanos con documentos aprobados. `read-block-by-ref.js` resolvió transacciones válidas, por ejemplo bloque 21 para documento y bloque 57 para auditoría de acceso. | Passed | Fabric | `person_documents.ledger_tx_id` está vacío; la validación operativa se hizo contra Fabric. |
| Trazabilidad | Cada operación relevante deja rastro verificable. | Fabric registra `REQUEST_CREATED`, `DOC_VERIFY_ON_REQUEST`, `DOC_VIEW_GRANTED` y `USER_ACCESS_STATE_CHANGE`. El reporte exportado del `2026-03-02` resume 37 bloques Fabric y 11 Indy en el periodo `2026-02-01` a `2026-03-02`. | Passed | Fabric + reportes exportados | La tabla SQL `audit_events` está vacía; la auditoría efectiva quedó on-chain. |
| Verificación SSI | El holder presenta una prueba verificable y el verifier la valida. | ACA-Py muestra 1 conexión activa issuer-holder por lado y 26 registros de proof, todos en estado `done` y `verified=true`. | Passed | ACA-Py admin API | Es la evidencia más fuerte de integración SSI real. |
| Integración end-to-end | El flujo issuer-holder-verifier se completa de punta a punta. | Los requests 3, 4 y 6 enlazan documento registrado, solicitud, decisión del ciudadano, consulta autorizada y evento auditable en Fabric. No se recuperó evidencia equivalente y completa para login/2FA dentro del mismo hilo. | Partially passed | DB + Fabric + Indy + journalctl | El flujo documental end-to-end está bien sustentado; el frente de autenticación debe redactarse con más cautela. |

## 2. Evidencia concreta de seguridad, privacidad y trazabilidad

### 2.1 PDFs off-chain

- La tabla `files` guarda los PDFs como `stored_as=PATH`; en la muestra consultada `content` está `NULL`.
- Se verificaron 35 archivos físicos bajo `/home/ccdigital/CCDigitalBlock/CCDigital`.
- Esto respalda que los binarios PDF permanecen fuera de la cadena.

Veredicto: `Evidencia completa`.

### 2.2 Qué queda on-chain en Fabric

Para el documento `Pasaporte` del ciudadano con identificación terminada en `3896`, `read-block-by-ref.js` resolvió una transacción `VALID` en el bloque 21. El estado on-chain contiene:

- `docId`
- `idType`
- `idNumber`
- `title`
- `issuingEntity`
- `filePath`
- `sizeBytes`
- `sha256`
- `createdAt`

Esto demuestra que Fabric no almacena el PDF binario, pero sí metadatos operativos e identificadores directos, incluyendo la ruta del archivo.

Veredicto: `Minimal on-chain exposure` solo `parcialmente` demostrado. La redacción correcta no es “solo hash on-chain”, sino “binario off-chain con integridad y metadatos operativos on-chain”.

### 2.3 Acceso condicionado por autorización explícita del ciudadano

- `access_requests` registra 4 decisiones `APROBADA` y 2 `RECHAZADA`.
- Solo después de una decisión `APROBADA` aparecen eventos `DOC_VIEW_GRANTED`.
- Ejemplo directo:
  - request 6 aprobada el `2026-03-06 23:36:01`
  - evento Fabric `DOC_VIEW_GRANTED` el `2026-03-06 23:36:17`
  - emisor: `SECRETARÍA DE HACIENDA DE BOGOTÁ`

La autorización explícita sí existe operacionalmente, pero no se persiste en una tabla independiente de consentimientos: `consents` está vacía.

Veredicto: `Evidencia completa` para autorización por solicitud aprobada; `evidencia incompleta` para un modelo de consentimiento desacoplado.

### 2.4 2FA o autenticación reforzada

- 3 de 4 cuentas de usuario final tienen `totp_enabled=1`.
- Sus confirmaciones TOTP quedaron con fecha:
  - `2026-03-05 17:33:32`
  - `2026-03-05 17:38:36`
  - `2026-03-05 22:56:57`
- El flujo de código implementa contraseña + SSI proof + TOTP/OTP por correo.

No obstante:

- no se recuperó un evento persistido de login exitoso que documente esa secuencia completa;
- el `2026-03-24` los endpoints `POST /user/auth/start` y `POST /user/auth/forgot/verify` redirigieron a login (`302`) en las corridas de Newman.

Veredicto: `Evidencia parcial`.

### 2.5 Eventos auditables registrados

- Fabric conserva eventos `REQUEST_CREATED`, `DOC_VERIFY_ON_REQUEST`, `DOC_VIEW_GRANTED` y `USER_ACCESS_STATE_CHANGE`.
- `read-block-by-ref.js` resolvió:
  - bloque 21 / TX `ea4c0bfc...` para registro documental;
  - bloque 57 / TX `d8fa7b61...` para `DOC_VIEW_GRANTED`.
- Los reportes exportados del sistema incluyen resumen de bloques Fabric e Indy.

La tabla SQL `audit_events` existe pero está vacía; la evidencia de auditoría efectiva está en Fabric y en los reportes exportados.

Veredicto: `Evidencia completa`.

## 3. Resultados de Newman

Se ejecutó la colección reproducible `docs/evidence/newman-smoke.postman_collection.json` sobre el despliegue activo en `http://localhost:8088`.

### 3.1 Corrida usada como referencia

La corrida más útil para Results fue la de `2026-03-24` con `--ignore-redirects`, porque evita que Newman convierta un `302` de seguridad en el `200` final de la página de login.

Resumen:

| Metric | Value |
|---|---|
| Iteraciones | 10 |
| Solicitudes procesadas | 100 |
| Fallos de transporte/red | 0 |
| Respuestas fuera del resultado esperado | 20 |
| Tiempo mínimo | 4 ms |
| Tiempo promedio | 16.17 ms |
| Tiempo máximo | 63 ms |

### 3.2 Endpoints evaluados

| Endpoint | Expected | Observed | Count | Min / Avg / Max |
|---|---|---|---:|---|
| `GET /` | 200 | 200 | 10 | 9 / 19.6 / 63 ms |
| `GET /login/user` | 200 | 200 | 10 | 9 / 17.0 / 28 ms |
| `GET /register/user` | 200 | 200 | 10 | 17 / 30.3 / 43 ms |
| `GET /login/user/forgot` | 200 | 200 | 10 | 8 / 15.9 / 28 ms |
| `GET /login/admin` | 200 | 200 | 10 | 8 / 16.3 / 23 ms |
| `GET /login/issuer` | 200 | 200 | 10 | 7 / 12.7 / 18 ms |
| `GET /api/persons` | 302 | 302 (`/login/admin?denied=true`) | 10 | 5 / 10.6 / 14 ms |
| `GET /actuator/health` | 403 | 403 | 10 | 11 / 21.4 / 34 ms |
| `POST /user/auth/forgot/verify` | 200 | 302 (`/login/user?expired=true`) | 10 | 6 / 9.5 / 12 ms |
| `POST /user/auth/start` | 200 | 302 (`/login/user?expired=true`) | 10 | 4 / 8.4 / 13 ms |

### 3.3 Fallas o fricciones detectadas

- `POST /user/auth/forgot/verify` falló 10/10 veces contra la expectativa del controlador REST: devolvió `302` hacia login.
- `POST /user/auth/start` falló 10/10 veces contra la expectativa del controlador REST: devolvió `302` hacia login.
- `GET /api/persons` mostró estabilidad correcta del control de acceso administrativo: redirección consistente a `/login/admin?denied=true`.
- `GET /actuator/health` mostró protección consistente por rol: `403` en 10/10 iteraciones.

### 3.4 Problemas por parámetros dinámicos

Sí hubo fricción metodológica por parámetros/contexto dinámico:

- `Newman` con seguimiento automático de redirects convirtió el `302` de `/api/persons` en `200` de la página de login y generó falsos fallos de lectura.
- No se incluyeron en el smoke test:
  - `GET /user/auth/poll`
  - `POST /user/auth/otp/verify`
  - `POST /user/auth/otp/resend`
  - vistas/descargas firmadas de documentos

Estos endpoints dependen de `presExId`, OTPs efímeros, sesión activa o parámetros firmados; por tanto no son estables para una prueba ciega sin orquestación previa del flujo SSI.

## 4. Resultados de evaluación de usabilidad con docentes expertos

No se localizaron, en el repositorio ni en las rutas del entorno inspeccionadas el `2026-03-24`, instrumentos, bases de respuesta, consolidados o reportes atribuibles a una evaluación con docentes expertos.

Estado recuperable:

| Item | Result |
|---|---|
| Número de docentes participantes | No recuperable |
| Instrumento aplicado | No recuperable |
| Respuestas por pregunta o dimensión | No recuperable |
| Promedios, frecuencias o conteos | No recuperable |
| Comentarios abiertos | No recuperable |
| Principales dificultades/fortalezas observadas | No recuperable |

Conclusión: esta subsección no debería redactarse como resultado empírico cerrado hasta recuperar la evidencia externa correspondiente.

## 5. Capturas del prototipo para figura compuesta

Sí existen capturas locales bajo `/home/ccdigital/Imágenes/Capturas de pantalla`, pero no fue posible clasificarlas automáticamente con suficiente confianza para etiquetarlas como login, 2FA, dashboard, carga, revisión o trazabilidad sin revisión visual manual.

Inventario recuperado:

- `Captura desde 2026-02-26 11-35-11.png` (`1223x68`)
- `Captura desde 2026-02-26 11-42-04.png` (`1365x68`)
- `Captura desde 2026-02-27 10-12-27.png` (`2451x1202`)
- `Captura desde 2026-02-27 11-18-18.png` (`2451x1202`)
- `Captura desde 2026-02-27 19-36-59.png` (`1335x827`)
- `Captura desde 2026-02-27 21-31-35.png` (`1335x827`)
- `Captura desde 2026-02-27 22-13-03.png` (`290x414`)
- `Captura desde 2026-03-02 10-33-45.png` (`656x421`)
- `Captura desde 2026-03-02 18-45-33.png` (`299x394`)
- `Captura desde 2026-03-04 22-31-57.png` (`1513x753`)

Conclusión: hay material candidato, pero no una figura compuesta trazable lista para publicar sin curaduría manual.

## 6. Confirmación de qué sí salió bien y qué no

### 6.1 Afirmaciones que sí están sustentadas

- El prototipo ejecutó flujos documentales end-to-end entre emisor, ciudadano y verificador para solicitudes de acceso aprobadas.
- El sistema registró eventos trazables en blockchain.
- Los binarios PDF permanecieron off-chain.
- La verificación SSI sí ocurrió en operación real.

### 6.2 Afirmaciones que deben redactarse con más cautela

| Claim | Verdict | Recommended wording |
|---|---|---|
| “Minimal on-chain exposure” | Parcial | “Los binarios sensibles permanecieron off-chain, mientras Fabric conservó metadatos operativos e integridad criptográfica.” |
| “Explicit citizen consent” | Parcialmente completo | “El acceso a documentos dependió de solicitudes aprobadas explícitamente por el ciudadano.” |
| “Strengthened authentication validated” | Parcial | “El prototipo integró contraseña, SSI y segundo factor; se verificó enrolamiento TOTP y pruebas SSI, pero la validación operativa del login completo requiere evidencia adicional.” |
| “Account verification and password recovery were validated” | Débil / no suficiente | “Los flujos están implementados; sin embargo, la evidencia recuperable del despliegue actual no es suficiente para afirmarlos como plenamente validados.” |
| “End-to-end processes executed” | Parcialmente completo | “Se ejecutaron de punta a punta los flujos de depósito, solicitud, aprobación/rechazo, consulta autorizada y trazabilidad blockchain.” |

### 6.3 Señales de cautela adicionales

- `audit_events` y `consents` existen en SQL pero están vacías.
- `reviewed_by_user` está `NULL` en documentos revisados.
- El reporte de cumplimiento exportado del `2026-03-02` resume `15` requisitos: `5 cumple`, `10 parcial`, `0 no cumple`.

## 7. Fuentes usadas

Artefactos generados en este repositorio:

- `/home/ccdigital/eclipse-workspace/CCDigital/docs/evidence/newman-smoke.postman_collection.json`
- `/home/ccdigital/eclipse-workspace/CCDigital/docs/evidence/newman-smoke-results.json`
- `/home/ccdigital/eclipse-workspace/CCDigital/docs/evidence/newman-smoke-ignore-redirects-results.json`

Reportes y activos recuperados del entorno:

- `/home/ccdigital/Descargas/ccdigital-reporte-20260201-20260302.json`
- `/home/ccdigital/Descargas/ccdigital-reporte-20260201-20260302.csv`
- `/home/ccdigital/Descargas/ccdigital-compliance-report-20260302.json`
- `/home/ccdigital/Descargas/ccdigital-compliance-report-20260302.csv`
- `/home/ccdigital/Descargas/ccdigital-reporte-trazabilidad-20260201-20260302.pdf`
- `/home/ccdigital/Imágenes/Capturas de pantalla/`

Referencias de implementación usadas para contrastar contrato esperado vs comportamiento observado:

- `/home/ccdigital/eclipse-workspace/CCDigital/src/main/java/co/edu/unbosque/ccdigital/controller/ForgotPasswordController.java`
- `/home/ccdigital/eclipse-workspace/CCDigital/src/main/java/co/edu/unbosque/ccdigital/controller/UserAuthController.java`
- `/home/ccdigital/eclipse-workspace/CCDigital/src/main/java/co/edu/unbosque/ccdigital/config/SecurityConfig.java`
- `/home/ccdigital/eclipse-workspace/CCDigital/src/main/java/co/edu/unbosque/ccdigital/service/FileStorageService.java`
- `/home/ccdigital/eclipse-workspace/CCDigital/src/main/java/co/edu/unbosque/ccdigital/service/AccessRequestService.java`
- `/home/ccdigital/eclipse-workspace/CCDigital/src/main/java/co/edu/unbosque/ccdigital/service/UserRegistrationFlowService.java`

Fuentes operativas consultadas directamente:

- MySQL del despliegue activo (`ciudadania_digital`)
- Fabric CLI en `/home/ccdigital/fabric/fabric-samples/test-network/client`
- ACA-Py admin APIs en `http://localhost:8021` y `http://localhost:8031`
- Journald del servicio `ccdigital-prod.service`
