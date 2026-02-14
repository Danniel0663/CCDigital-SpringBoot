package co.edu.unbosque.ccdigital.repository;

import co.edu.unbosque.ccdigital.entity.FileRecord;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio JPA para {@link FileRecord}.
 *
 * <p>
 * Provee operaciones CRUD sobre la tabla {@code files}. Si se requieren búsquedas específicas
 * (por ejemplo por {@code personDocument.id}, por {@code sha256_hex} o por {@code document.id}),
 * se pueden agregar métodos derivados o consultas con {@code @Query}.
 * </p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
public interface FileRecordRepository extends JpaRepository<FileRecord, Long> {
}
