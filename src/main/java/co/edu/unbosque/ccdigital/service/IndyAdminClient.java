package co.edu.unbosque.ccdigital.service;

import co.edu.unbosque.ccdigital.config.IndyProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Objects;

/**
 * Cliente HTTP simple para invocar endpoints del Admin API de ACA-Py (Indy/Aries).
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Construir solicitudes GET/POST hacia un {@code baseUrl} + {@code path}.</li>
 *   <li>Aplicar cabecera {@code X-API-Key} si está configurada en {@link IndyProperties}.</li>
 *   <li>Leer la respuesta y convertirla a {@link JsonNode}.</li>
 * </ul>
 *
 * <p>Este cliente no implementa reintentos ni manejo avanzado de errores; los errores de parseo o
 * respuestas no JSON se propagan como {@link IllegalStateException}.</p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
@Component
public class IndyAdminClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;
    private final IndyProperties indyProperties;

    /**
     * Constructor con inyección de dependencias.
     *
     * @param objectMapper mapper JSON
     * @param indyProperties propiedades Indy/ACA-Py
     */
    public IndyAdminClient(ObjectMapper objectMapper, IndyProperties indyProperties) {
        this.objectMapper = objectMapper;
        this.indyProperties = indyProperties;
    }

    /**
     * Ejecuta una petición GET al Admin API.
     *
     * @param baseUrl base URL (ej. {@code http://localhost:8021})
     * @param path path del endpoint (ej. {@code /status})
     * @return respuesta parseada como {@link JsonNode}
     * @throws IllegalStateException si la respuesta no es JSON válido
     */
    public JsonNode get(String baseUrl, String path) {
        String url = normalize(baseUrl) + path;
        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
        ResponseEntity<String> res = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        return readJson(Objects.requireNonNull(res.getBody()));
    }

    /**
     * Ejecuta una petición POST al Admin API con body JSON.
     *
     * @param baseUrl base URL (ej. {@code http://localhost:8021})
     * @param path path del endpoint (ej. {@code /connections/create-invitation})
     * @param body objeto a serializar a JSON
     * @return respuesta parseada como {@link JsonNode}
     * @throws IllegalStateException si no se puede serializar el body o la respuesta no es JSON válido
     */
    public JsonNode post(String baseUrl, String path, Object body) {
        String url = normalize(baseUrl) + path;
        HttpHeaders headers = buildHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String payload;
        try {
            payload = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo serializar body a JSON", e);
        }

        HttpEntity<String> entity = new HttpEntity<>(payload, headers);
        ResponseEntity<String> res = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        return readJson(Objects.requireNonNull(res.getBody()));
    }

    /**
     * Construye headers para Admin API.
     *
     * @return headers con {@code X-API-Key} si está configurado
     */
    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (indyProperties.getAdminApiKey() != null && !indyProperties.getAdminApiKey().isBlank()) {
            headers.set("X-API-Key", indyProperties.getAdminApiKey());
        }
        return headers;
    }

    /**
     * Parsea una respuesta cruda a {@link JsonNode}.
     *
     * @param raw respuesta en texto
     * @return árbol JSON
     * @throws IllegalStateException si no es JSON válido
     */
    private JsonNode readJson(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Respuesta no es JSON válido: " + raw, e);
        }
    }

    /**
     * Normaliza el baseUrl para evitar doble slash al concatenar con {@code path}.
     *
     * @param baseUrl base URL
     * @return base URL sin slash final
     */
    private String normalize(String baseUrl) {
        if (baseUrl.endsWith("/")) return baseUrl.substring(0, baseUrl.length() - 1);
        return baseUrl;
    }
}
