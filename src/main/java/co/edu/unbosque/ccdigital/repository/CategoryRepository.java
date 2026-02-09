package co.edu.unbosque.ccdigital.repository;

import co.edu.unbosque.ccdigital.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio JPA para la entidad {@link Category}.
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {
}
