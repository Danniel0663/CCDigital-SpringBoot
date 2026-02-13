package co.edu.unbosque.ccdigital.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de integración con Indy/Aries y ACA-Py.
 *
 * <p>
 * Mapea la configuración bajo el prefijo {@code ccdigital.indy}. Centraliza URLs del Admin API,
 * identificadores Indy (schema / cred def) y parámetros necesarios para flujos de autenticación
 * y emisión/verificación.
 * </p>
 *
 * <h2>Secciones principales</h2>
 * <ul>
 *   <li><strong>Admin API</strong>: URLs para Issuer y Holder.</li>
 *   <li><strong>Artefactos Indy</strong>: {@code schemaId} y {@code credDefId}.</li>
 *   <li><strong>Seguridad</strong>: {@code adminApiKey} (si el Admin API requiere autenticación).</li>
 *   <li><strong>Sesión Holder</strong>: {@code holderConnectionId} o {@code holderLabel} para resolución de conexión.</li>
 * </ul>
 *
 * @since 1.0
 */
@ConfigurationProperties(prefix = "ccdigital.indy")
public class IndyProperties {

    /** URL base del Admin API del agente Issuer. */
    private String issuerAdminUrl;

    /** URL base del Admin API del agente Holder. */
    private String holderAdminUrl;

    /** Identificador del esquema Indy usado para emisión/verificación. */
    private String schemaId;

    /** Identificador del Credential Definition Indy usado para emisión/verificación. */
    private String credDefId;

    /** API Key para acceder al Admin API (si aplica). */
    private String adminApiKey;

    /** Identificador de conexión del Holder (usado en flujos de proof/login). */
    private String holderConnectionId;

    /** Etiqueta esperada del Holder ({@code their_label}) para resolver el {@code connection_id}. */
    private String holderLabel;

    public String getIssuerAdminUrl() {
        return issuerAdminUrl;
    }

    public void setIssuerAdminUrl(String issuerAdminUrl) {
        this.issuerAdminUrl = issuerAdminUrl;
    }

    public String getHolderAdminUrl() {
        return holderAdminUrl;
    }

    public void setHolderAdminUrl(String holderAdminUrl) {
        this.holderAdminUrl = holderAdminUrl;
    }

    public String getSchemaId() {
        return schemaId;
    }

    public void setSchemaId(String schemaId) {
        this.schemaId = schemaId;
    }

    public String getCredDefId() {
        return credDefId;
    }

    public void setCredDefId(String credDefId) {
        this.credDefId = credDefId;
    }

    public String getAdminApiKey() {
        return adminApiKey;
    }

    public void setAdminApiKey(String adminApiKey) {
        this.adminApiKey = adminApiKey;
    }

    public String getHolderConnectionId() {
        return holderConnectionId;
    }

    public void setHolderConnectionId(String holderConnectionId) {
        this.holderConnectionId = holderConnectionId;
    }

    public String getHolderLabel() {
        return holderLabel;
    }

    public void setHolderLabel(String holderLabel) {
        this.holderLabel = holderLabel;
    }
}
