package co.edu.unbosque.ccdigital.service;

import co.edu.unbosque.ccdigital.entity.DocumentDefinition;
import co.edu.unbosque.ccdigital.repository.DocumentDefinitionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Servicio de negocio para la gestión del catálogo de documentos ({@link DocumentDefinition}).
 *
 * <p>Esta capa encapsula el acceso al repositorio {@link DocumentDefinitionRepository} y expone
 * operaciones típicas para:</p>
 *
 * <ul>
 *   <li>Consultar el catálogo completo de definiciones.</li>
 *   <li>Consultar una definición específica por id.</li>
 *   <li>Persistir (crear/actualizar) definiciones de documentos.</li>
 *   <li>Consultar definiciones permitidas para un emisor (issuer) usando la tabla puente
 *       {@code entity_document_definitions}.</li>
 * </ul>
 *
 * <p>Se usa tanto desde controladores REST (API) como desde controladores MVC.</p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
@Service
public class DocumentDefinitionService {

    /**
     * Repositorio de acceso a datos para definiciones de documentos.
     */
    private final DocumentDefinitionRepository repository;

    /**
     * Crea una instancia del servicio.
     *
     * @param repository repositorio de {@link DocumentDefinition}
     */
    public DocumentDefinitionService(DocumentDefinitionRepository repository) {
        this.repository = repository;
    }

    /**
     * Retorna las definiciones de documentos permitidas para un emisor.
     *
     * <p>Se apoya en una consulta del repositorio, que usa la tabla puente
     * {@code entity_document_definitions} para filtrar por {@code issuerId}.</p>
     *
     * @param issuerId id del emisor (entidad) para el cual se consultan documentos permitidos
     * @return lista de documentos permitidos para el emisor
     */
    public List<DocumentDefinition> findAllowedByIssuer(Long issuerId) {
        return repository.findAllowedByIssuer(issuerId);
    }

    /**
     * Lista todas las definiciones de documentos del catálogo.
     *
     * @return lista completa de {@link DocumentDefinition}
     */
    public List<DocumentDefinition> findAll() {
        return repository.findAll();
    }

    /**
     * Busca una definición de documento por su id.
     *
     * @param id id de la definición
     * @return {@link Optional} con la definición si existe; vacío si no existe
     */
    public Optional<DocumentDefinition> findById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("El id de definición es requerido.");
        }
        return repository.findById(id);
    }

    /**
     * Guarda o actualiza una definición de documento.
     *
     * <p>Si {@code def.id} es {@code null}, crea un nuevo registro.
     * Si no es {@code null}, actualiza el existente.</p>
     *
     * @param def definición de documento a persistir
     * @return definición persistida (con id asignado si era nuevo)
     */
    public DocumentDefinition save(DocumentDefinition def) {
        if (def == null) {
            throw new IllegalArgumentException("La definición de documento es requerida.");
        }
        return repository.save(def);
    }
}
