package co.edu.unbosque.ccdigital.entity;

/**
 * Estrategia de almacenamiento del contenido de un archivo.
 *
 * <p>
 * Se utiliza en {@link FileRecord} para indicar dónde se encuentra el contenido: en base de datos
 * (BLOB), en el sistema de archivos (PATH) o en almacenamiento externo (S3).
 * </p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
public enum FileStoredAs {

    /** Almacenamiento en base de datos como contenido binario (BLOB). */
    BLOB,

    /** Almacenamiento en sistema de archivos, referenciado por una ruta. */
    PATH,

    /** Almacenamiento externo compatible con S3. */
    S3
}
