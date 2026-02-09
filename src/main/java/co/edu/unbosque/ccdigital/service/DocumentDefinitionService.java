package co.edu.unbosque.ccdigital.service;

import co.edu.unbosque.ccdigital.entity.DocumentDefinition;
import co.edu.unbosque.ccdigital.repository.DocumentDefinitionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Servicio de dominio para operaciones sobre el catálogo de definiciones de documento.
 */
@Service
public class DocumentDefinitionService {

    private final DocumentDefinitionRepository repository;

    /**
     * Crea una instancia del servicio.
     *
     * @param repository repositorio de definiciones de documento
     */
    public DocumentDefinitionService(DocumentDefinitionRepository repository) {
        this.repository = repository;
    }

    /**
     * Obtiene las definiciones de documento permitidas para un emisor.
     *
     * @param issuerId identificador del emisor
     * @return lista de documentos permitidos
     */
    public List<DocumentDefinition> findAllowedByIssuer(Long issuerId) {
        return repository.findAllowedByIssuer(issuerId);
    }

    /**
     * Lista todas las definiciones de documento.
     *
     * @return lista completa del catálogo
     */
    public List<DocumentDefinition> findAll() {
        return repository.findAll();
    }

    /**
     * Obtiene una definición por id.
     *
     * @param id identificador del documento
     * @return {@link Optional} con la definición si existe
     */
    public Optional<DocumentDefinition> findById(Long id) {
        return repository.findById(id);
    }

    /**
     * Guarda una definición de documento.
     *
     * @param def definición a persistir
     * @return entidad persistida
     */
    public DocumentDefinition save(DocumentDefinition def) {
        return repository.save(def);
    }
}
