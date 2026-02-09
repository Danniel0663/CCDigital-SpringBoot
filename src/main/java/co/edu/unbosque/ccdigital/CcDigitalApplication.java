package co.edu.unbosque.ccdigital;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import co.edu.unbosque.ccdigital.config.FileStorageProperties;

/**
 * Punto de entrada principal de la aplicación <b>CCDigital</b>.
 * <p>
 * Esta clase inicializa el contexto de Spring Boot  y realiza el arranque de la aplicación,
 * habilitando además la carga de propiedades de configuración asociadas al almacenamiento de archivos.
 * </p>
 *
 * <h2>Responsabilidades</h2>
 * <ul>
 *   <li>Arrancar la aplicación Spring Boot.</li>
 *   <li>Habilitar el enlace (binding) de propiedades de configuración mediante {@link EnableConfigurationProperties}.</li>
 * </ul>
 *
 * <h2>Configuración habilitada</h2>
 * <ul>
 *   <li>{@link FileStorageProperties}: propiedades relacionadas con la configuración de almacenamiento de archivos.</li>
 * </ul>
 *
 * @author Danniel
 * @author Yeison
 * @since 1.0
 */
@SpringBootApplication
@EnableConfigurationProperties({
        FileStorageProperties.class
})
public class CcDigitalApplication {

	 /**
     * Punto de entrada principal de la aplicación.
     *
     * @param args argumentos de línea de comandos.
     */
    public static void main(String[] args) {
        SpringApplication.run(CcDigitalApplication.class, args);
    }
}