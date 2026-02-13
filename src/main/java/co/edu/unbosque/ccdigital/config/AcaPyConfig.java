package co.edu.unbosque.ccdigital.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuración de integración con ACA-Py mediante {@link RestTemplate}.
 *
 * <p>
 * Expone un {@link RestTemplate} dedicado para consumir el Admin API de ACA-Py (Issuer/Holder).
 * Si {@link IndyProperties#getAdminApiKey()} está definido, se inyecta el encabezado
 * {@code X-API-Key} en todas las solicitudes.
 * </p>
 *
 * <h2>Uso</h2>
 * <ul>
 *   <li>Inyectar {@code RestTemplate} por nombre de bean: {@code acapyRestTemplate}.</li>
 *   <li>Configurar {@code ccdigital.indy.admin-api-key} si el Admin API está protegido.</li>
 * </ul>
 *
 * @since 1.0
 */
@Configuration
public class AcaPyConfig {

    /**
     * Crea un {@link RestTemplate} para consumir el Admin API de ACA-Py.
     *
     * @param indyProperties propiedades de configuración Indy/Aries (URLs, credenciales, API key)
     * @return instancia de {@link RestTemplate} con interceptor opcional de {@code X-API-Key}
     */
    @Bean
    public RestTemplate acapyRestTemplate(IndyProperties indyProperties) {
        RestTemplate rt = new RestTemplate();

        String apiKey = indyProperties.getAdminApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>(rt.getInterceptors());
            interceptors.add((request, body, execution) -> {
                request.getHeaders().set("X-API-Key", apiKey);
                return execution.execute(request, body);
            });
            rt.setInterceptors(interceptors);
        }

        return rt;
    }
}
