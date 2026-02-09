package co.edu.unbosque.ccdigital;

import co.edu.unbosque.ccdigital.config.ExternalToolsProperties;
import co.edu.unbosque.ccdigital.config.FileStorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Clase principal (bootstrap) de la aplicación CCDigital.
 *
 * <p>Esta clase funciona como el punto de entrada del proyecto Spring Boot.
 * Al ejecutar el método {@link #main(String[])}, se inicializa el contexto de Spring,
 * se registran los beans y se habilita el auto-configurado de Spring Boot.</p>
 *
 * <h2>Propiedades tipadas habilitadas</h2>
 * <p>Mediante {@link EnableConfigurationProperties} se habilita el binding de propiedades
 * definidas en el archivo de configuración (por ejemplo: {@code application.properties}
 * o {@code application.yml}) hacia clases tipo POJO.</p>
 *
 * <ul>
 *   <li>{@link FileStorageProperties}: configuración relacionada con el almacenamiento de archivos
 *       (por ejemplo, ruta base, límites, nombres de carpetas, etc.).</li>
 *   <li>{@link ExternalToolsProperties}: configuración de herramientas externas o integraciones
 *       (por ejemplo, rutas de ejecutables, endpoints, flags de ejecución, etc.).</li>
 * </ul>
 *
 * <p><b>Nota:</b> Para que el binding funcione correctamente, las clases de propiedades deben estar
 * anotadas con {@code @ConfigurationProperties(prefix="...")} y tener getters/setters (o ser records
 * configurados para binding, según la versión de Spring).</p>
 *
 * @author
 * @since 1.0.0
 */
@SpringBootApplication // Marca esta clase como aplicación Spring Boot y activa el auto-configurado.
@EnableConfigurationProperties({
        FileStorageProperties.class,   // Habilita el cargue de propiedades de almacenamiento.
        ExternalToolsProperties.class  // Habilita el cargue de propiedades de herramientas externas.
})
public class CcDigitalApplication {

    /**
     * Método principal que arranca la aplicación.
     *
     * <p>Inicia el contexto de Spring Boot, aplica el auto-configurado y levanta el servidor embebido
     * (por ejemplo, Tomcat) si corresponde.</p>
     *
     * @param args argumentos de línea de comandos utilizados al arrancar la aplicación.
     */
    public static void main(String[] args) {
        // Ejecuta el arranque de Spring Boot y crea el ApplicationContext.
        SpringApplication.run(CcDigitalApplication.class, args);
    }
}
