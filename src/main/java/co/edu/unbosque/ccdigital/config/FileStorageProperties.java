package co.edu.unbosque.ccdigital.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de configuración para el almacenamiento de archivos del sistema.
 *
 * <p>Las propiedades se mapean desde la configuración de Spring (application.properties o application.yml)
 * usando el prefijo {@code ccdigital.fs}.</p>
 *
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "ccdigital.fs")
public class FileStorageProperties {

    /**
     * Ruta base donde se almacenan los archivos del sistema.
     */
    private String basePath;

    /**
     * Obtiene la ruta base de almacenamiento.
     *
     * @return ruta base
     */
    public String getBasePath() {
        return basePath;
    }

    /**
     * Establece la ruta base de almacenamiento.
     *
     * @param basePath ruta base
     */
    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }
}
