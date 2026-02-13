package co.edu.unbosque.ccdigital.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

/**
 * Principal de sesión para usuarios autenticados mediante verificación (proof) con Indy/Aries.
 *
 * <p>
 * Esta clase encapsula los atributos mínimos verificados a partir de un {@code presentation_exchange}
 * y expone la autoridad {@code ROLE_USER} para integrarse con las reglas de {@code SecurityConfig}.
 * </p>
 *
 * <p>
 * No implementa {@link org.springframework.security.core.userdetails.UserDetails} porque el flujo de login
 * se construye manualmente (por ejemplo, creando un {@code UsernamePasswordAuthenticationToken} y
 * persistiendo el contexto en sesión).
 * </p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
public class IndyUserPrincipal {

    private static final String DEFAULT_ROLE = "ROLE_USER";

    private final String idType;
    private final String idNumber;
    private final String firstName;
    private final String lastName;
    private final String email;

    /**
     * Construye un principal de usuario verificado.
     *
     * @param idType tipo de identificación verificado (por ejemplo {@code CC}, {@code CE})
     * @param idNumber número de identificación verificado
     * @param firstName nombres verificados
     * @param lastName apellidos verificados
     * @param email correo verificado (opcional)
     */
    public IndyUserPrincipal(String idType, String idNumber, String firstName, String lastName, String email) {
        this.idType = normalize(idType);
        this.idNumber = normalize(idNumber);
        this.firstName = normalize(firstName);
        this.lastName = normalize(lastName);
        this.email = normalize(email);
    }

    /**
     * Retorna el tipo de identificación verificado.
     *
     * @return tipo de identificación
     */
    public String getIdType() {
        return idType;
    }

    /**
     * Retorna el número de identificación verificado.
     *
     * @return número de identificación
     */
    public String getIdNumber() {
        return idNumber;
    }

    /**
     * Retorna los nombres verificados.
     *
     * @return nombres
     */
    public String getFirstName() {
        return firstName;
    }

    /**
     * Retorna los apellidos verificados.
     *
     * @return apellidos
     */
    public String getLastName() {
        return lastName;
    }

    /**
     * Retorna el correo verificado (si fue provisto).
     *
     * @return correo (puede estar vacío)
     */
    public String getEmail() {
        return email;
    }

    /**
     * Retorna un nombre para visualización en UI.
     *
     * <p>
     * Si el nombre completo no está disponible, retorna el número de identificación.
     * </p>
     *
     * @return nombre para mostrar (nunca {@code null})
     */
    public String getDisplayName() {
        String full = (firstName + " " + lastName).trim();
        return full.isBlank() ? idNumber : full;
    }

    /**
     * Retorna las autoridades del usuario para Spring Security.
     *
     * <p>
     * Incluye {@code ROLE_USER} para que expresiones como {@code hasRole("USER")} funcionen.
     * </p>
     *
     * @return colección de autoridades
     */
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(DEFAULT_ROLE));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
