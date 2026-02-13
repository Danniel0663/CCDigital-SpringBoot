package co.edu.unbosque.ccdigital.service;

import co.edu.unbosque.ccdigital.config.IndyProperties;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio que implementa el login basado en presentación de prueba (present-proof 2.0) con ACA-Py.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Construir y enviar un proof request filtrado por {@code id_number}.</li>
 *   <li>Consultar el estado del intercambio (record) por {@code presExId}.</li>
 *   <li>Extraer atributos revelados una vez el proof está verificado.</li>
 *   <li>Resolver la conexión del holder (configurada o detectada automáticamente).</li>
 * </ul>
 *
 * <p>Notas:</p>
 * <ul>
 *   <li>Se usa el endpoint {@code /present-proof-2.0/send-request} para iniciar el flujo.</li>
 *   <li>Se consulta {@code /present-proof-2.0/records/{presExId}} para obtener el estado y resultados.</li>
 * </ul>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
@Service
public class IndyProofLoginService {

    private final RestTemplate rest;
    private final IndyProperties props;

    /**
     * Constructor con inyección de dependencias.
     *
     * @param rest cliente HTTP (Spring)
     * @param props propiedades de configuración Indy/ACA-Py
     */
    public IndyProofLoginService(RestTemplate rest, IndyProperties props) {
        this.rest = rest;
        this.props = props;
    }

    /**
     * Inicia un login por proof, filtrando credenciales por {@code idNumber}.
     *
     * <p>El flujo:</p>
     * <ol>
     *   <li>Resuelve {@code connectionId} del holder (configurado o modo auto).</li>
     *   <li>Construye un proof request con restricción:
     *       {@code cred_def_id} y {@code attr::id_number::value == idNumber}.</li>
     *   <li>Envía POST a {@code /present-proof-2.0/send-request}.</li>
     *   <li>Devuelve el body del record e inserta {@code presExId} si viene como
     *       {@code pres_ex_id} o {@code presentation_exchange_id}.</li>
     * </ol>
     *
     * @param idNumber número de identificación con el que se filtra la credencial del holder
     * @return mapa con el record inicial (incluye {@code presExId} si es posible)
     */
    public Map<String, Object> startLoginByIdNumber(String idNumber) {
        String connectionId = resolveHolderConnectionId();
        Map<String, Object> payload = buildSendRequestPayload(connectionId, idNumber);

        String verifierAdmin = require(props.getIssuerAdminUrl(), "ccdigital.indy.issuer-admin-url");
        String url = verifierAdmin + "/present-proof-2.0/send-request";

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(payload, buildAdminHeaders());
        ResponseEntity<Map> resp = rest.exchange(url, HttpMethod.POST, req, Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        if (body == null) body = new LinkedHashMap<>();

        Object id = body.get("pres_ex_id");
        if (id == null) id = body.get("presentation_exchange_id");
        if (id != null) body.put("presExId", String.valueOf(id));

        return body;
    }

    /**
     * Consulta el estado del proof exchange (present-proof 2.0).
     *
     * <p>Devuelve un mapa simplificado con:</p>
     * <ul>
     *   <li>{@code presExId}: id del exchange</li>
     *   <li>{@code state}: estado ACA-Py (ej. request-sent, presentation-received, done, abandoned)</li>
     *   <li>{@code verified}: indicador de verificación (true/false/null)</li>
     *   <li>{@code done}: true si el state indica finalización (done/abandoned)</li>
     *   <li>{@code error}: mensaje de error si ACA-Py lo reporta</li>
     * </ul>
     *
     * @param presExId identificador del proof exchange
     * @return mapa con estado resumido
     */
    public Map<String, Object> getProofStatus(String presExId) {
        Map<String, Object> record = getProofRecord(presExId);

        String state = asString(record.get("state"));
        Boolean verified = asBoolean(record.get("verified"));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("presExId", presExId);
        out.put("state", state);
        out.put("verified", verified);

        boolean done = "done".equalsIgnoreCase(state) || "abandoned".equalsIgnoreCase(state);
        out.put("done", done);

        String errorMsg = asString(record.get("error_msg"));
        if (!errorMsg.isBlank()) out.put("error", errorMsg);

        return out;
    }

    /**
     * Obtiene el resultado verificado (atributos revelados) para un {@code presExId}.
     *
     * <p>Reglas:</p>
     * <ul>
     *   <li>Requiere estado {@code done} o {@code presentation-received}.</li>
     *   <li>Requiere {@code verified == true}.</li>
     *   <li>Extrae atributos revelados del record y devuelve un mapa simple (String/String).</li>
     * </ul>
     *
     * @param presExId identificador del proof exchange
     * @return mapa con atributos del perfil (id_type, id_number, first_name, last_name, email)
     * @throws IllegalStateException si el proof no está listo o no está verificado
     */
    public Map<String, String> getVerifiedResultWithAttrs(String presExId) {
        Map<String, Object> record = getProofRecord(presExId);

        String state = asString(record.get("state"));
        Boolean verified = asBoolean(record.get("verified"));

        if (!"done".equalsIgnoreCase(state) && !"presentation-received".equalsIgnoreCase(state)) {
            throw new IllegalStateException("El proof aún no está listo. state=" + state);
        }
        if (!Boolean.TRUE.equals(verified)) {
            throw new IllegalStateException("Proof no verificado. verified=" + verified);
        }

        Map<String, Object> rawAttrs = extractRevealedAttrsRaw(record);

        Map<String, String> out = new LinkedHashMap<>();
        out.put("id_type", asString(rawAttrs.get("id_type")));
        out.put("id_number", asString(rawAttrs.get("id_number")));
        out.put("first_name", asString(rawAttrs.get("first_name")));
        out.put("last_name", asString(rawAttrs.get("last_name")));
        out.put("email", asString(rawAttrs.get("email")));
        return out;
    }

    /**
     * Construye el payload requerido por {@code /present-proof-2.0/send-request}.
     *
     * @param connectionId conexión ACA-Py con el holder
     * @param idNumber valor para filtrar la credencial del holder
     * @return payload completo para enviar al endpoint
     */
    private Map<String, Object> buildSendRequestPayload(String connectionId, String idNumber) {
        Map<String, Object> presentationRequest = buildIndyProofRequestSingleGroup(idNumber);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("comment", "CCDigital login proof (filter by id_number)");
        payload.put("connection_id", connectionId);
        payload.put("auto_verify", true);
        payload.put("auto_remove", false);
        payload.put("presentation_request", presentationRequest);

        return payload;
    }

    /**
     * Construye un proof request Indy (formato present-proof 2.0) con un solo grupo de atributos.
     *
     * <p>El grupo {@code profile} solicita revelar:</p>
     * <ul>
     *   <li>id_type</li>
     *   <li>id_number</li>
     *   <li>first_name</li>
     *   <li>last_name</li>
     *   <li>email</li>
     * </ul>
     *
     * <p>Restricción aplicada:</p>
     * <ul>
     *   <li>{@code cred_def_id == props.credDefId}</li>
     *   <li>{@code attr::id_number::value == idNumber}</li>
     * </ul>
     *
     * @param idNumber número de identificación por el que se filtra la credencial
     * @return estructura {@code presentation_request} con el bloque {@code indy}
     */
    private Map<String, Object> buildIndyProofRequestSingleGroup(String idNumber) {
        String credDefId = require(props.getCredDefId(), "ccdigital.indy.cred-def-id");

        Map<String, Object> restriction = new LinkedHashMap<>();
        restriction.put("cred_def_id", credDefId);
        restriction.put("attr::id_number::value", idNumber);

        Map<String, Object> requestedAttrsProfile = new LinkedHashMap<>();
        requestedAttrsProfile.put("names", List.of(
                "id_type", "id_number", "first_name", "last_name", "email"
        ));
        requestedAttrsProfile.put("restrictions", List.of(restriction));

        Map<String, Object> requestedAttributes = new LinkedHashMap<>();
        requestedAttributes.put("profile", requestedAttrsProfile);

        Map<String, Object> proofRequest = new LinkedHashMap<>();
        proofRequest.put("name", "CCDigital Login");
        proofRequest.put("version", "1.0");
        proofRequest.put("requested_attributes", requestedAttributes);
        proofRequest.put("requested_predicates", Map.of());

        Map<String, Object> indy = new LinkedHashMap<>();
        indy.put("name", proofRequest.get("name"));
        indy.put("version", proofRequest.get("version"));
        indy.put("requested_attributes", requestedAttributes);
        indy.put("requested_predicates", Map.of());

        Map<String, Object> fmt = new LinkedHashMap<>();
        fmt.put("indy", indy);

        return fmt;
    }

    /**
     * Consulta el record completo del proof exchange en ACA-Py.
     *
     * @param presExId identificador del exchange
     * @return mapa con el record (puede estar vacío si la respuesta viene sin body)
     */
    private Map<String, Object> getProofRecord(String presExId) {
        String verifierAdmin = require(props.getIssuerAdminUrl(), "ccdigital.indy.issuer-admin-url");
        String url = verifierAdmin + "/present-proof-2.0/records/" + presExId;

        HttpEntity<Void> req = new HttpEntity<>(buildAdminHeaders());
        ResponseEntity<Map> resp = rest.exchange(url, HttpMethod.GET, req, Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        if (body == null) body = new LinkedHashMap<>();
        return body;
    }

    /**
     * Extrae atributos revelados desde un record de present-proof 2.0.
     *
     * <p>Orden de lectura:</p>
     * <ol>
     *   <li>{@code by_format.pres.indy.requested_proof.revealed_attr_groups.profile.values}</li>
     *   <li>{@code revealed_attrs} como fallback</li>
     * </ol>
     *
     * @param record record completo del proof exchange
     * @return mapa con claves de atributos y su valor {@code raw}
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractRevealedAttrsRaw(Map<String, Object> record) {
        Map<String, Object> byFormat = asMap(record.get("by_format"));
        Map<String, Object> pres = asMap(byFormat.get("pres"));
        Map<String, Object> indy = asMap(pres.get("indy"));
        Map<String, Object> requestedProof = asMap(indy.get("requested_proof"));

        Map<String, Object> groups = asMap(requestedProof.get("revealed_attr_groups"));
        Map<String, Object> profile = asMap(groups.get("profile"));
        Map<String, Object> values = asMap(profile.get("values"));
        if (!values.isEmpty()) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : values.entrySet()) {
                Map<String, Object> vObj = asMap(e.getValue());
                out.put(e.getKey(), asString(vObj.get("raw")));
            }
            return out;
        }

        Map<String, Object> revealedAttrs = asMap(requestedProof.get("revealed_attrs"));
        if (!revealedAttrs.isEmpty()) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : revealedAttrs.entrySet()) {
                Map<String, Object> vObj = asMap(e.getValue());
                out.put(e.getKey(), asString(vObj.get("raw")));
            }
            return out;
        }

        return Map.of();
    }

    /**
     * Resuelve el {@code connection_id} del holder.
     *
     * <p>Modos soportados:</p>
     * <ul>
     *   <li>Fijo: si {@code holderConnectionId} está configurado y no es {@code auto}.</li>
     *   <li>Auto: consulta conexiones {@code active} y busca coincidencia por {@code their_label}
     *       con {@code holderLabel}.</li>
     * </ul>
     *
     * @return connection_id activo
     * @throws IllegalStateException si no se encuentra una conexión activa en modo auto
     */
    private String resolveHolderConnectionId() {
        String configured = props.getHolderConnectionId();
        if (configured != null && !configured.isBlank() && !"auto".equalsIgnoreCase(configured)) {
            return configured.trim();
        }

        String holderLabel = require(props.getHolderLabel(), "ccdigital.indy.holder-label");
        String issuerAdmin = require(props.getIssuerAdminUrl(), "ccdigital.indy.issuer-admin-url");

        String url = issuerAdmin + "/connections?state=active";
        HttpEntity<Void> req = new HttpEntity<>(buildAdminHeaders());
        ResponseEntity<Map> resp = rest.exchange(url, HttpMethod.GET, req, Map.class);

        Map<String, Object> body = castMap(resp.getBody());
        List<Object> results = castList(body.get("results"));

        for (Object o : results) {
            Map<String, Object> conn = castMap(o);
            String label = asString(conn.get("their_label"));
            if (holderLabel.equalsIgnoreCase(label)) {
                return asString(conn.get("connection_id"));
            }
        }

        throw new IllegalStateException(
                "No se encontró conexión ACTIVE con their_label='" + holderLabel + "'. " +
                "Define ccdigital.indy.holder-connection-id o revisa la conexión en ACA-Py."
        );
    }

    /**
     * Construye headers para invocar Admin API de ACA-Py.
     *
     * @return headers con content-type JSON y, si aplica, {@code X-API-KEY}
     */
    private HttpHeaders buildAdminHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);

        String apiKey = props.getAdminApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            h.set("X-API-KEY", apiKey.trim());
        }
        return h;
    }

    /**
     * Valida que una propiedad de configuración exista y no esté en blanco.
     *
     * @param v valor
     * @param key nombre lógico de la propiedad (para el mensaje de error)
     * @return valor trim
     * @throws IllegalStateException si el valor es null o blank
     */
    private static String require(String v, String key) {
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Falta configuración: " + key);
        }
        return v.trim();
    }

    /**
     * Conversión segura de objeto a Map.
     *
     * @param o objeto
     * @return map si {@code o} es Map; si no, map vacío
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        return (o instanceof Map) ? (Map<String, Object>) o : new LinkedHashMap<>();
    }

    /**
     * Conversión segura de objeto a List.
     *
     * @param o objeto
     * @return list si {@code o} es List; si no, lista vacía
     */
    @SuppressWarnings("unchecked")
    private static List<Object> castList(Object o) {
        return (o instanceof List) ? (List<Object>) o : List.of();
    }

    /**
     * Conversión segura de objeto a Map (inmutable vacío si no aplica).
     *
     * @param o objeto
     * @return map si {@code o} es Map; si no, {@code Map.of()}
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return (o instanceof Map) ? (Map<String, Object>) o : Map.of();
    }

    /**
     * Convierte un objeto a String (no null).
     *
     * @param o objeto
     * @return string, o cadena vacía si {@code o} es null
     */
    private static String asString(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    /**
     * Convierte un objeto a Boolean, si es posible.
     *
     * @param o objeto
     * @return boolean si es Boolean; null si {@code o} es null; en otro caso parsea {@code Boolean.valueOf(String)}
     */
    private static Boolean asBoolean(Object o) {
        if (o instanceof Boolean b) return b;
        if (o == null) return null;
        return Boolean.valueOf(String.valueOf(o));
    }
}
