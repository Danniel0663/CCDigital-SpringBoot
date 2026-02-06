package co.edu.unbosque.ccdigital.repository;

import co.edu.unbosque.ccdigital.entity.PersonDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PersonDocumentRepository extends JpaRepository<PersonDocument, Long> {

    @Query("select distinct pd from PersonDocument pd left join fetch pd.files where pd.person.id = :personId")
    List<PersonDocument> findByPersonIdWithFiles(@Param("personId") Long personId);

    @Query("select pd from PersonDocument pd left join fetch pd.files where pd.id = :id")
    Optional<PersonDocument> findByIdWithFiles(@Param("id") Long id);
}
