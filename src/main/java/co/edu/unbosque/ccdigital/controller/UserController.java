package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.security.IndyUserPrincipal;
import co.edu.unbosque.ccdigital.service.FabricLedgerCliService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

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

    private final FabricLedgerCliService fabric;

    /**
     * Constructor del controlador.
     *
     * @param fabric servicio de consulta de documentos en Fabric vía CLI
     */
    public UserController(FabricLedgerCliService fabric) {
        this.fabric = fabric;
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

        return "user/dashboard";
    }
}
