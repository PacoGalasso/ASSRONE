package ASSRONE.backend.controller;

import ASSRONE.backend.dto.CommitteeMemberDto;
import ASSRONE.backend.dto.CreateCommitteeMemberRequest;
import ASSRONE.backend.dto.UpdateCommitteeMemberRequest;
import ASSRONE.backend.service.CommitteeMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

@RestController
@RequestMapping("/api/committee-members")
@RequiredArgsConstructor
public class CommitteeMemberController {

    private final CommitteeMemberService committeeMemberService;

    @GetMapping
    public List<CommitteeMemberDto> getPublicMembers() {
        return committeeMemberService.getPublicMembers();
    }

    @GetMapping("/admin")
    public List<CommitteeMemberDto> getAllForAdmin() {
        return committeeMemberService.getAllForAdmin();
    }

    @PostMapping
    public ResponseEntity<CommitteeMemberDto> create(@Valid @RequestBody CreateCommitteeMemberRequest request) {
        CommitteeMemberDto created = committeeMemberService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public CommitteeMemberDto update(@PathVariable Long id, @Valid @RequestBody UpdateCommitteeMemberRequest request) {
        return committeeMemberService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        committeeMemberService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommitteeMemberDto uploadPhoto(@PathVariable Long id, @RequestParam("file") MultipartFile file) throws IOException {
        return committeeMemberService.uploadPhoto(id, file);
    }

    @DeleteMapping("/{id}/photo")
    public CommitteeMemberDto deletePhoto(@PathVariable Long id) {
        return committeeMemberService.deletePhoto(id);
    }

    @GetMapping("/{id}/photo")
    public ResponseEntity<Resource> getPhoto(@PathVariable Long id, Authentication authentication) throws IOException {
        Resource resource = committeeMemberService.loadPhoto(id, authentication);
        if (resource == null) {
            return ResponseEntity.notFound().build();
        }
        String contentType = Files.probeContentType(resource.getFile().toPath());
        return ResponseEntity.ok()
                .contentType(contentType != null ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
