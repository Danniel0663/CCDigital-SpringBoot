package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.entity.ReviewStatus;
import co.edu.unbosque.ccdigital.service.PersonDocumentService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/person-documents")
public class PersonDocumentAdminController {

    private final PersonDocumentService service;

    public PersonDocumentAdminController(PersonDocumentService service) {
        this.service = service;
    }

    @PostMapping("/{id}/review")
    public String review(@PathVariable Long id,
                         @RequestParam("status") ReviewStatus status,
                         @RequestParam(value = "notes", required = false) String notes,
                         @RequestParam("personId") Long personId) {

        service.review(id, status, notes);
        return "redirect:/admin/persons/" + personId;
    }
}
