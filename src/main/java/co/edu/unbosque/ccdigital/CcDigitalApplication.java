package co.edu.unbosque.ccdigital;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Clase de arranque de la aplicación <strong>CCDigital</strong>.
 *
 * <p>
 * Inicializa el contexto de Spring Boot y habilita el escaneo de clases anotadas con
 * {@code @ConfigurationProperties} dentro del paquete base {@code co.edu.unbosque.ccdigital}.
 * </p>
 *
 * <h2>Responsabilidades</h2>
 * <ul>
 *   <li>Arrancar la aplicación Spring Boot.</li>
 *   <li>Habilitar el escaneo de propiedades de configuración mediante {@link ConfigurationPropertiesScan}.</li>
 * </ul>
 *
 * @author Danniel
 * @author Yeison
 * @since 1.0
 * @see SpringApplication
 */
@SpringBootApplication
@ConfigurationPropertiesScan("co.edu.unbosque.ccdigital")
public class CcDigitalApplication {

    /**
     * Punto de entrada principal de la aplicación.
     *
     * <p>
     * Delega el arranque en {@link SpringApplication#run(Class, String...)} para levantar el
     * contenedor y registrar los beans definidos en el contexto.
     * </p>
     *
     * @param args argumentos de línea de comandos.
     */
    public static void main(String[] args) {
        SpringApplication.run(CcDigitalApplication.class, args);
    }
}
