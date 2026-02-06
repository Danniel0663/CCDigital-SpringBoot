package co.edu.unbosque.ccdigital.repository;

import co.edu.unbosque.ccdigital.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
}
