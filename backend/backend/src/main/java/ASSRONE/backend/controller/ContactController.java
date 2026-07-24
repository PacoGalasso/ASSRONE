package ASSRONE.backend.controller;

import ASSRONE.backend.dto.ContactMessageRequest;
import ASSRONE.backend.service.ContactEmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contact")
@RequiredArgsConstructor
public class ContactController {

    private final ContactEmailService contactEmailService;

    @PostMapping
    public ResponseEntity<Void> sendMessage(@Valid @RequestBody ContactMessageRequest request) {
        contactEmailService.sendContactMessage(request);
        return ResponseEntity.ok().build();
    }
}