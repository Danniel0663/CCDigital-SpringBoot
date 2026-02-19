package co.edu.unbosque.ccdigital.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public class BcryptPrinterApp {

    public static void main(String[] args) {
        // 1) Prioridad: argumento por consola
        String raw = (args != null && args.length > 0) ? args[0] : null;

        // 2) Alternativa: system property (opcional)
        if (raw == null || raw.isBlank()) {
            raw = System.getProperty("app.print-bcrypt", "Danniel06*");
        }

        // 3) Alternativa: variable de entorno (opcional)
        if (raw == null || raw.isBlank()) {
            raw = System.getenv("APP_PRINT_BCRYPT");
        }

        if (raw == null || raw.isBlank()) {
            System.err.println("Uso:");
            System.err.println("  java co.edu.unbosque.ccdigital.security.BcryptPrinterApp \"miPassword\"");
            System.err.println("  o  java -Dapp.print-bcrypt=miPassword co.edu.unbosque.ccdigital.security.BcryptPrinterApp");
            System.err.println("  o  APP_PRINT_BCRYPT=miPassword java co.edu.unbosque.ccdigital.security.BcryptPrinterApp");
            System.exit(1);
        }

        PasswordEncoder encoder = new BCryptPasswordEncoder();
        System.out.println("BCrypt => " + encoder.encode(raw));
    }
}
