package ASSRONE.backend.controller;

import ASSRONE.backend.dto.CreateMembershipApplicationRequest;
import ASSRONE.backend.dto.MembershipApplicationDto;
import ASSRONE.backend.service.MembershipApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/membership-applications")
@RequiredArgsConstructor
public class MembershipApplicationController {

    private final MembershipApplicationService service;

    @PostMapping
    public ResponseEntity<MembershipApplicationDto> submit(@Valid @RequestBody CreateMembershipApplicationRequest request) {
        MembershipApplicationDto created = service.submit(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public List<MembershipApplicationDto> getAll() {
        return service.getAll();
    }
}