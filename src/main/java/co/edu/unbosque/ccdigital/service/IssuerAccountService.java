package co.edu.unbosque.ccdigital.service;

import co.edu.unbosque.ccdigital.entity.EntityUser;
import co.edu.unbosque.ccdigital.entity.IssuingEntity;
import co.edu.unbosque.ccdigital.repository.EntityUserRepository;
import co.edu.unbosque.ccdigital.repository.IssuingEntityRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio para administración de credenciales de usuarios emisores.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Crear o actualizar el registro de {@link EntityUser} asociado a un emisor.</li>
 *   <li>Aplicar hashing de contraseña con {@link PasswordEncoder}.</li>
 *   <li>Garantizar unicidad de email entre emisores.</li>
 * </ul>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
@Service
public class IssuerAccountService {

    private final IssuingEntityRepository issuerRepo;
    private final EntityUserRepository entityUserRepo;
    private final PasswordEncoder encoder;

    /**
     * Constructor con inyección de dependencias.
     *
     * @param issuerRepo repositorio de emisores
     * @param entityUserRepo repositorio de usuarios de emisor
     * @param encoder encoder para hashing de contraseñas
     */
    public IssuerAccountService(IssuingEntityRepository issuerRepo,
                                EntityUserRepository entityUserRepo,
                                PasswordEncoder encoder) {
        this.issuerRepo = issuerRepo;
        this.entityUserRepo = entityUserRepo;
        this.encoder = encoder;
    }

    /**
     * Asigna o actualiza credenciales para un emisor.
     *
     * <p>Comportamiento:</p>
     * <ul>
     *   <li>Valida parámetros obligatorios.</li>
     *   <li>Valida que el emisor exista.</li>
     *   <li>Valida que el email no esté asignado a otro emisor.</li>
     *   <li>Crea {@link EntityUser} si no existe para ese {@code issuerId} o actualiza el existente.</li>
     *   <li>Persiste el hash de contraseña (no guarda la contraseña en texto plano).</li>
     * </ul>
     *
     * @param issuerId identificador del emisor
     * @param email correo del usuario emisor
     * @param rawPassword contraseña en texto plano (se almacena como hash)
     * @throws IllegalArgumentException si faltan datos, el emisor no existe o el email ya está en uso
     */
    @Transactional
    public void setCredentials(Long issuerId, String email, String rawPassword) {
        if (issuerId == null) throw new IllegalArgumentException("issuerId requerido");
        if (email == null || email.isBlank()) throw new IllegalArgumentException("email requerido");
        if (rawPassword == null || rawPassword.isBlank()) throw new IllegalArgumentException("password requerida");

        IssuingEntity issuer = issuerRepo.findById(issuerId)
                .orElseThrow(() -> new IllegalArgumentException("Emisor no encontrado"));

        String emailNorm = email.trim();

        entityUserRepo.findByEmailIgnoreCase(emailNorm).ifPresent(other -> {
            if (!other.getEntityId().equals(issuerId)) {
                throw new IllegalArgumentException("Ese email ya está en uso por otro emisor.");
            }
        });

        EntityUser entityUser = entityUserRepo.findById(issuerId).orElseGet(() -> {
            EntityUser nu = new EntityUser();
            nu.setEntityId(issuerId);
            nu.setIsActive(Boolean.TRUE);
            nu.setFullName(issuer.getName());
            return nu;
        });

        entityUser.setEmail(emailNorm);
        entityUser.setFullName(issuer.getName());
        entityUser.setPasswordHash(encoder.encode(rawPassword));
        entityUser.setIsActive(Boolean.TRUE);

        entityUserRepo.save(entityUser);
    }
}
