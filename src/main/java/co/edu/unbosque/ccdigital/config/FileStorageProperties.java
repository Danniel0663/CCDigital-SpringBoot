package co.edu.unbosque.ccdigital.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de configuración para el almacenamiento de archivos.
 *
 * <p>
 * Mapea la configuración bajo el prefijo {@code ccdigital.fs}. El valor configurado se utiliza
 * como directorio raíz para guardar y localizar archivos asociados al proyecto.
 * </p>
 *
 * <h2>Ejemplo</h2>
 * <pre>
 * ccdigital.fs.base-path=/home/ccdigital/CCDigitalBlock/storage
 * </pre>
 *
 * <p>El valor de {@link basePath} se utiliza como directorio raíz para guardar y/o
 * localizar archivos asociados al proyecto.</p>
 *
 * @author Danniel
 * @author Yeison
 * @since 1.0
 */
@ConfigurationProperties(prefix = "ccdigital.fs")
public class FileStorageProperties {

    /**
     * Ruta base donde la aplicación almacenará y buscará archivos.
     *
     * <p>El proceso debe contar con permisos de lectura y escritura sobre esta ruta.</p>
     */
    private String basePath;

    /**
     * Retorna la ruta base de almacenamiento de archivos.
     *
     * @return ruta base del sistema de archivos
     */
    public String getBasePath() {
        return basePath;
    }

    /**
     * Establece la ruta base de almacenamiento de archivos.
     *
     * @param basePath ruta base del sistema de archivos
     */
    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }
}
