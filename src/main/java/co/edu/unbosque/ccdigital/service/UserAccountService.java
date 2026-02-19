package co.edu.unbosque.ccdigital.service;

import co.edu.unbosque.ccdigital.dto.UserRegisterForm;
import co.edu.unbosque.ccdigital.entity.AppUser;
import co.edu.unbosque.ccdigital.entity.IdType;
import co.edu.unbosque.ccdigital.entity.Person;
import co.edu.unbosque.ccdigital.repository.AppUserRepository;
import co.edu.unbosque.ccdigital.repository.PersonRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de negocio para registro de cuentas de usuario final.
 *
 * <p>
 * A partir de un formulario completo de registro:
 * </p>
 * <ul>
 *   <li>Valida la información obligatoria del usuario.</li>
 *   <li>Ubica una persona existente por número de identificación.</li>
 *   <li>Actualiza los datos de la persona (nombres, correo, teléfono, etc.).</li>
 *   <li>Crea el registro en {@code users} enlazado por {@code person_id}.</li>
 *   <li>Persiste la contraseña usando {@link PasswordEncoder} (BCrypt).</li>
 * </ul>
 *
 * @since 3.0
 */
@Service
public class UserAccountService {

    private static final String USER_ROLE = "USUARIO";

    private final PersonRepository personRepository;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder encoder;

    /**
     * Constructor con inyección de dependencias.
     *
     * @param personRepository repositorio de personas
     * @param appUserRepository repositorio de usuarios de aplicación
     * @param encoder encoder BCrypt para contraseña
     */
    public UserAccountService(PersonRepository personRepository,
                              AppUserRepository appUserRepository,
                              PasswordEncoder encoder) {
        this.personRepository = personRepository;
        this.appUserRepository = appUserRepository;
        this.encoder = encoder;
    }

    /**
     * Crea un usuario final en {@code users} a partir de una persona existente en {@code persons}.
     *
     * <p>
     * Validaciones:
     * </p>
     * <ul>
     *   <li>La persona debe existir por número de identificación.</li>
     *   <li>No debe existir ya un usuario para ese {@code person_id}.</li>
     *   <li>Se capturan y validan todos los datos del formulario.</li>
     *   <li>Se actualizan los datos de la persona con la información ingresada.</li>
     *   <li>El correo no debe estar asignado a otro usuario.</li>
     *   <li>La contraseña y su confirmación deben ser válidas y coincidir.</li>
     * </ul>
     *
     * @param form formulario de registro
     * @return correo asociado al usuario creado
     * @throws IllegalArgumentException si falta información requerida o alguna validación de negocio falla
     */
    @Transactional
    public String registerFromExistingPerson(UserRegisterForm form) {
        String idTypeValue = normalize(form == null ? null : form.getIdType());
        String idNumberNorm = normalize(form == null ? null : form.getIdNumber());
        String firstNameValue = normalize(form == null ? null : form.getFirstName());
        String lastNameValue = normalize(form == null ? null : form.getLastName());
        String emailValue = normalize(form == null ? null : form.getEmail());
        String phoneValue = normalize(form == null ? null : form.getPhone());
        var birthdateValue = form == null ? null : form.getBirthdate();
        String rawPasswordValue = form == null || form.getPassword() == null ? "" : form.getPassword();
        String confirmPasswordValue = form == null || form.getConfirmPassword() == null ? "" : form.getConfirmPassword();

        if (idTypeValue.isBlank()) {
            throw new IllegalArgumentException("Tipo de identificación requerido.");
        }
        if (idNumberNorm.isBlank()) {
            throw new IllegalArgumentException("Número de identificación requerido.");
        }
        if (firstNameValue.isBlank()) {
            throw new IllegalArgumentException("Nombres requeridos.");
        }
        if (lastNameValue.isBlank()) {
            throw new IllegalArgumentException("Apellidos requeridos.");
        }
        if (emailValue.isBlank()) {
            throw new IllegalArgumentException("Correo requerido.");
        }
        if (phoneValue.isBlank()) {
            throw new IllegalArgumentException("Teléfono requerido.");
        }
        if (birthdateValue == null) {
            throw new IllegalArgumentException("Fecha de nacimiento requerida.");
        }
        if (rawPasswordValue.isBlank()) {
            throw new IllegalArgumentException("Contraseña requerida.");
        }
        if (!rawPasswordValue.equals(confirmPasswordValue)) {
            throw new IllegalArgumentException("La confirmación de contraseña no coincide.");
        }

        IdType idType;
        try {
            idType = IdType.valueOf(idTypeValue);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Tipo de identificación inválido.");
        }

        Person person = personRepository.findByIdNumber(idNumberNorm)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe una persona registrada con esa identificación."
                ));

        if (appUserRepository.existsById(person.getId())) {
            throw new IllegalArgumentException("Ya existe un usuario asociado a esta persona.");
        }

        appUserRepository.findByEmailIgnoreCase(emailValue).ifPresent(existing -> {
            if (!existing.getPersonId().equals(person.getId())) {
                throw new IllegalArgumentException("El correo ingresado ya está en uso por otro usuario.");
            }
        });

        person.setIdType(idType);
        person.setFirstName(firstNameValue);
        person.setLastName(lastNameValue);
        person.setEmail(emailValue);
        person.setPhone(phoneValue);
        person.setBirthdate(birthdateValue);

        AppUser user = new AppUser();
        user.setPersonId(person.getId());
        user.setFullName(buildFullName(person));
        user.setEmail(emailValue);
        user.setPasswordHash(encoder.encode(rawPasswordValue));
        user.setRole(USER_ROLE);
        user.setIsActive(Boolean.TRUE);

        personRepository.save(person);
        appUserRepository.save(user);
        return emailValue;
    }

    private String buildFullName(Person person) {
        String fullName = normalize(person.getFullName());
        return fullName.isBlank() ? person.getIdNumber() : fullName;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
