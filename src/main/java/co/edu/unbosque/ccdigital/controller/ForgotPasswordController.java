package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.dto.ForgotResetRequest;
import co.edu.unbosque.ccdigital.dto.ForgotVerifyRequest;
import co.edu.unbosque.ccdigital.service.ForgotPasswordService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/user/auth/forgot")
public class ForgotPasswordController {

    private final ForgotPasswordService forgotPasswordService;

    public ForgotPasswordController(ForgotPasswordService forgotPasswordService) {
        this.forgotPasswordService = forgotPasswordService;
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verify(@RequestBody ForgotVerifyRequest req) {
        boolean ok = forgotPasswordService.verify(req.getEmail(), req.getIdType(), req.getIdNumber());
        return ResponseEntity.ok(Map.of("verified", ok));
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> reset(@RequestBody ForgotResetRequest req) {
        boolean ok = forgotPasswordService.reset(
                req.getEmail(),
                req.getIdType(),
                req.getIdNumber(),
                req.getNewPassword()
        );
        return ResponseEntity.ok(Map.of("ok", ok));
    }
}