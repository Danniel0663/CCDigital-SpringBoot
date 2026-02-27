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
     * @return usuario creado (persistido) con datos de acceso
     * @throws IllegalArgumentException si falta información requerida o alguna validación de negocio falla
     */
    @Transactional
    public AppUser registerFromExistingPerson(UserRegisterForm form) {
        String idTypeValue = normalize(form == null ? null : form.getIdType());
        String idNumberNorm = normalize(form == null ? null : form.getIdNumber());
        String firstNameValue = normalize(form == null ? null : form.getFirstName());
        String lastNameValue = normalize(form == null ? null : form.getLastName());
        String emailValue = normalize(form == null ? null : form.getEmail());
        String phoneValue = normalize(form == null ? null : form.getPhone());
        var birthdateValue = form == null ? null : form.getBirthdate();
        String rawPasswordValue = normalize(form == null ? null : form.getPassword());
        String confirmPasswordValue = normalize(form == null ? null : form.getConfirmPassword());

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
        if (!isStrongEnoughPassword(rawPasswordValue)) {
            throw new IllegalArgumentException(
                    "La contraseña debe tener mínimo 8 caracteres e incluir letras, números y un carácter especial."
            );
        }

        IdType idType;
        try {
            idType = IdType.valueOf(idTypeValue);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Tipo de identificación inválido.");
        }

        Person person = personRepository.findByIdTypeAndIdNumber(idType, idNumberNorm)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe una persona registrada con ese tipo y número de identificación."
                ));

        appUserRepository.findById(person.getId()).ifPresent(existingUser -> {
            throw new IllegalArgumentException(
                    "Ya existe un usuario asociado a esta persona (correo: "
                            + normalize(existingUser.getEmail())
                            + ", rol: "
                            + normalize(existingUser.getRole())
                            + ")."
            );
        });

        appUserRepository.findByEmailIgnoreCase(emailValue).ifPresent(existing -> {
            if (!existing.getPersonId().equals(person.getId())) {
                throw new IllegalArgumentException("El correo ingresado ya está en uso por otro usuario.");
            }
        });

        validateIdentityFieldsAgainstPerson(person, firstNameValue, lastNameValue);
        updateContactDataIfChanged(person, emailValue, phoneValue, birthdateValue);

        AppUser user = new AppUser();
        user.setPersonId(person.getId());
        user.setFullName(buildFullName(person));
        user.setEmail(emailValue);
        user.setPasswordHash(encoder.encode(rawPasswordValue));
        user.setRole(USER_ROLE);
        user.setIsActive(Boolean.TRUE);

        personRepository.saveAndFlush(person);
        return appUserRepository.save(user);
    }

    private String buildFullName(Person person) {
        String fullName = normalize(person.getFullName());
        return fullName.isBlank() ? person.getIdNumber() : fullName;
    }

    /**
     * Valida que los nombres/apellidos ingresados coincidan con la persona ya registrada.
     *
     * <p>La identificación (tipo y número) ya fue validada previamente para ubicar la persona.
     * Esta comprobación evita que se creen usuarios usando una identificación válida con nombres
     * diferentes a los registrados.</p>
     *
     * @param person persona encontrada en base de datos
     * @param firstNameValue nombres ingresados en el formulario
     * @param lastNameValue apellidos ingresados en el formulario
     */
    private void validateIdentityFieldsAgainstPerson(Person person, String firstNameValue, String lastNameValue) {
        String dbFirstName = normalize(person.getFirstName());
        String dbLastName = normalize(person.getLastName());

        if (!dbFirstName.equalsIgnoreCase(normalize(firstNameValue))
                || !dbLastName.equalsIgnoreCase(normalize(lastNameValue))) {
            throw new IllegalArgumentException(
                    "Los nombres o apellidos no coinciden con la persona registrada para esa identificación."
            );
        }
    }

    /**
     * Actualiza en {@code persons} únicamente los datos de contacto que difieran de lo ingresado.
     *
     * @param person persona a actualizar
     * @param emailValue correo ingresado
     * @param phoneValue teléfono ingresado
     * @param birthdateValue fecha de nacimiento ingresada
     */
    private void updateContactDataIfChanged(Person person,
                                            String emailValue,
                                            String phoneValue,
                                            java.time.LocalDate birthdateValue) {
        if (!normalize(person.getEmail()).equalsIgnoreCase(normalize(emailValue))) {
            person.setEmail(emailValue);
        }
        if (!normalize(person.getPhone()).equals(normalize(phoneValue))) {
            person.setPhone(phoneValue);
        }
        if (person.getBirthdate() == null || !person.getBirthdate().equals(birthdateValue)) {
            person.setBirthdate(birthdateValue);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Aplica la misma regla de complejidad usada en el flujo de restablecimiento de contraseña.
     *
     * <p>Regla mínima: 8 caracteres, con al menos una letra, un número y un carácter especial.</p>
     *
     * @param pwd contraseña normalizada
     * @return {@code true} si cumple la política mínima
     */
    private boolean isStrongEnoughPassword(String pwd) {
        if (pwd == null || pwd.isBlank()) return false;
        if (pwd.length() < 8) return false;
        boolean hasLetter = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;
        for (char c : pwd.toCharArray()) {
            if (Character.isLetter(c)) hasLetter = true;
            if (Character.isDigit(c)) hasDigit = true;
            if (!Character.isLetterOrDigit(c)) hasSpecial = true;
        }
        return hasLetter && hasDigit && hasSpecial;
    }
}
