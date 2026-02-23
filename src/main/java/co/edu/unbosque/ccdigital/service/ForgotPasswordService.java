package co.edu.unbosque.ccdigital.service;

import co.edu.unbosque.ccdigital.entity.AppUser;
import co.edu.unbosque.ccdigital.entity.Person;
import co.edu.unbosque.ccdigital.repository.AppUserRepository;
import co.edu.unbosque.ccdigital.repository.PersonRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class ForgotPasswordService {

    private final AppUserRepository appUserRepository;
    private final PersonRepository personRepository;
    private final PasswordEncoder passwordEncoder;

    public ForgotPasswordService(
            AppUserRepository appUserRepository,
            PersonRepository personRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.appUserRepository = appUserRepository;
        this.personRepository = personRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public boolean verify(String email, String idType, String idNumber) {
        if (isBlank(email) || isBlank(idType) || isBlank(idNumber)) return false;

        AppUser user = appUserRepository.findByEmailIgnoreCase(email.trim()).orElse(null);
        if (user == null) return false;

        Long personId = user.getPersonId();
        if (personId == null) return false;

        Person p = personRepository.findById(personId).orElse(null);
        if (p == null) return false;

        // Ajusta estos getters si tu Person los nombra diferente
        String dbType = p.getIdType() == null ? "" : p.getIdType().toString();
        String dbNum = p.getIdNumber() == null ? "" : p.getIdNumber().trim();

        String reqType = idType.trim().toUpperCase(Locale.ROOT);
        String reqNum = idNumber.trim();

        return dbType.equalsIgnoreCase(reqType) && dbNum.equalsIgnoreCase(reqNum);
    }

    @Transactional
    public boolean reset(String email, String idType, String idNumber, String newPassword) {
        if (!verify(email, idType, idNumber)) return false;
        if (isBlank(newPassword) || newPassword.trim().length() < 6) return false;

        AppUser user = appUserRepository.findByEmailIgnoreCase(email.trim()).orElse(null);
        if (user == null) return false;

        // Aquí está el cambio clave según tu entidad:
        user.setPasswordHash(passwordEncoder.encode(newPassword.trim()));

        appUserRepository.save(user);
        return true;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}