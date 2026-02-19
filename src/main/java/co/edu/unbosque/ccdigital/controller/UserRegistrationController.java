package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.dto.UserRegisterForm;
import co.edu.unbosque.ccdigital.entity.IdType;
import co.edu.unbosque.ccdigital.service.UserAccountService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controlador web para registro de cuentas de usuario final.
 *
 * <p>
 * Expone la vista y procesamiento de {@code /register/user}, donde se capturan
 * datos completos del ciudadano y se delega la creación de la cuenta al servicio
 * de negocio.
 * </p>
 *
 * @since 3.0
 */
@Controller
@RequestMapping("/register/user")
public class UserRegistrationController {

    private final UserAccountService userAccountService;

    /**
     * Constructor del controlador.
     *
     * @param userAccountService servicio de registro de usuarios
     */
    public UserRegistrationController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    /**
     * Muestra el formulario de registro de usuario final.
     *
     * <p>
     * Además de inicializar el DTO del formulario, carga el catálogo de tipos
     * de identificación para poblar el selector de la vista.
     * </p>
     *
     * @param model modelo de Spring MVC
     * @return vista del formulario de registro
     */
    @GetMapping
    public String form(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new UserRegisterForm());
        }
        model.addAttribute("idTypes", IdType.values());
        return "auth/register-user";
    }

    /**
     * Crea un usuario final a partir de una persona existente.
     *
     * <p>
     * En caso de error de validación, conserva los datos no sensibles del formulario
     * y limpia los campos de contraseña antes de devolver la misma vista.
     * </p>
     *
     * @param form datos del formulario
     * @param model modelo de Spring MVC
     * @return vista del formulario con resultado de operación
     */
    @PostMapping
    public String register(@ModelAttribute("form") UserRegisterForm form, Model model) {
        model.addAttribute("idTypes", IdType.values());
        try {
            String email = userAccountService.registerFromExistingPerson(form);

            model.addAttribute("success", "Usuario creado correctamente.");
            model.addAttribute("createdEmail", email);
            model.addAttribute("form", new UserRegisterForm());
        } catch (IllegalArgumentException ex) {
            form.setPassword(null);
            form.setConfirmPassword(null);
            model.addAttribute("form", form);
            model.addAttribute("error", ex.getMessage());
        }

        return "auth/register-user";
    }
}
