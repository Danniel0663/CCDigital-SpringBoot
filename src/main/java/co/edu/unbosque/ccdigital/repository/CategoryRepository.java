package co.edu.unbosque.ccdigital.repository;

import co.edu.unbosque.ccdigital.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio JPA para {@link Category}.
 *
 * <p>
 * Provee operaciones CRUD sobre la tabla {@code categories}.
 * </p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {
}
