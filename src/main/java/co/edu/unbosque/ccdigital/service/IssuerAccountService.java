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
 * <p>Gestiona la persistencia en {@code entity_users} y garantiza unicidad de email
 * entre emisores.</p>
 */
@Service
public class IssuerAccountService {

    private final IssuingEntityRepository issuerRepo;
    private final EntityUserRepository entityUserRepo;
    private final PasswordEncoder encoder;

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
     * <p>Si no existe registro en {@code entity_users} para el {@code issuerId}, se crea.</p>
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
