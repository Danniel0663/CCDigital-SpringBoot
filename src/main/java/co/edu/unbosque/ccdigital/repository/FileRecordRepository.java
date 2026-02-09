package co.edu.unbosque.ccdigital.repository;

import co.edu.unbosque.ccdigital.entity.FileRecord;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio JPA para la entidad {@link FileRecord}.
 */
public interface FileRecordRepository extends JpaRepository<FileRecord, Long> {
}
