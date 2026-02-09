package co.edu.unbosque.ccdigital.entity;

/**
 * Enum que representa la estrategia/medio en el que un archivo es almacenado.
 *
 * <p>Se utiliza junto con entidades como {@code FileRecord} para indicar dónde se encuentra
 * físicamente el contenido del archivo</p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
public enum FileStoredAs {

    /**
     * El archivo se guarda en base de datos como contenido binario (BLOB).
     */
    BLOB,

    /**
     * El archivo se guarda en el sistema de archivos, referenciado por una ruta.
     */
    PATH,

    /**
     * El archivo se guarda en almacenamiento externo compatible con S3.
     */
    S3
}
