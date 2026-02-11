package co.edu.unbosque.ccdigital.entity;

/**
 * Enum que representa el tipo de entidad dentro del sistema.
 *
 * <p>Este tipo se utiliza para clasificar entidades según su rol o función en la plataforma.
 * Actualmente se contempla el tipo {@link #EMISOR}, pero el enum está diseñado para crecer
 * en caso de que el proyecto incorpore más roles.</p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
public enum EntityType {

    /**
     * Entidad emisora autorizada para emitir documentos y credenciales.
     */
    EMISOR
}
