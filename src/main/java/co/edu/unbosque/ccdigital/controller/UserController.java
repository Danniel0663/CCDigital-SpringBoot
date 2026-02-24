package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.entity.AppUser;
import co.edu.unbosque.ccdigital.entity.Person;
import co.edu.unbosque.ccdigital.repository.AppUserRepository;
import co.edu.unbosque.ccdigital.repository.PersonRepository;
import co.edu.unbosque.ccdigital.security.IndyUserPrincipal;
import co.edu.unbosque.ccdigital.service.FabricLedgerCliService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.format.DateTimeFormatter;

/**
 * Controlador web del módulo de usuario final.
 *
 * <p>
 * Renderiza el dashboard del usuario autenticado y carga los documentos desde Fabric
 * para visualización en la interfaz.
 * </p>
 *
 * @since 3.0
 */
@Controller
public class UserController {

    private static final DateTimeFormatter UI_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final FabricLedgerCliService fabric;
    private final AppUserRepository appUserRepository;
    private final PersonRepository personRepository;

    /**
     * Constructor del controlador.
     *
     * @param fabric servicio de consulta de documentos en Fabric vía CLI
     */
    public UserController(FabricLedgerCliService fabric,
                          AppUserRepository appUserRepository,
                          PersonRepository personRepository) {
        this.fabric = fabric;
        this.appUserRepository = appUserRepository;
        this.personRepository = personRepository;
    }

    /**
     * Vista del dashboard del usuario.
     *
     * @param authentication autenticación actual
     * @param model modelo de Spring MVC
     * @return nombre de la vista del dashboard
     */
    @GetMapping("/user/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        IndyUserPrincipal p = (IndyUserPrincipal) authentication.getPrincipal();

        model.addAttribute("displayName", p.getDisplayName());
        model.addAttribute("idType", p.getIdType());
        model.addAttribute("idNumber", p.getIdNumber());
        model.addAttribute("email", p.getEmail());
        model.addAttribute("docs", fabric.listDocsView(p.getIdType(), p.getIdNumber()));
        addTotpStatus(model, p);

        return "user/dashboard";
    }

    private void addTotpStatus(Model model, IndyUserPrincipal principal) {
        AppUser appUser = findAppUserByPrincipal(principal);
        boolean totpEnabled = appUser != null
                && Boolean.TRUE.equals(appUser.getTotpEnabled())
                && appUser.getTotpSecretBase32() != null
                && !appUser.getTotpSecretBase32().isBlank();

        model.addAttribute("totpEnabled", totpEnabled);
        model.addAttribute(
                "totpConfirmedAtHuman",
                appUser != null && appUser.getTotpConfirmedAt() != null
                        ? UI_DATE_TIME.format(appUser.getTotpConfirmedAt())
                        : null
        );
    }

    private AppUser findAppUserByPrincipal(IndyUserPrincipal principal) {
        if (principal == null || principal.getIdNumber() == null || principal.getIdNumber().isBlank()) {
            return null;
        }

        Person person = personRepository.findByIdNumber(principal.getIdNumber().trim()).orElse(null);
        if (person == null || person.getId() == null) {
            return null;
        }
        return appUserRepository.findById(person.getId()).orElse(null);
    }
}
