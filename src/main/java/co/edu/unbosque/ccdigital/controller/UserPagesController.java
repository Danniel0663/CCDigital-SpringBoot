package co.edu.unbosque.ccdigital.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UserPagesController {

    @GetMapping("/user")
    public String root() {
        return "redirect:/user/login";
    }

    @GetMapping("/user/login")
    public String login() {
        return "auth/login";
    }
    
    @GetMapping("/login/user/forgot")
    public String forgotUser() {
        return "auth/forgot-user";
    }

}