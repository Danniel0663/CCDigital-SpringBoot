package co.edu.unbosque.ccdigital.repository;

import co.edu.unbosque.ccdigital.entity.FileRecord;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio JPA para la entidad {@link FileRecord}.
 *
 * <p>Provee operaciones CRUD y consultas básicas sobre la tabla {@code files}
 * mediante {@link JpaRepository}:</p>
 *
 * <p>Si se requieren búsquedas específicas (por ejemplo por {@code personDocumentId},
 * por {@code sha256}, o por {@code documentId}), se pueden agregar métodos derivados
 * o consultas {@code @Query}.</p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
public interface FileRecordRepository extends JpaRepository<FileRecord, Long> {
}
