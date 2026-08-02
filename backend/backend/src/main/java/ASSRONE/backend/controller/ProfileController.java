package ASSRONE.backend.controller;

import ASSRONE.backend.dto.ChangePasswordRequest;
import ASSRONE.backend.dto.UpdateProfileRequest;
import ASSRONE.backend.dto.UserProfileDto;
import ASSRONE.backend.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserProfileService userProfileService;

    @GetMapping
    public UserProfileDto getProfile(Authentication authentication) {
        return userProfileService.getProfile(authentication.getName());
    }

    @PutMapping
    public UserProfileDto updateProfile(@Valid @RequestBody UpdateProfileRequest request, Authentication authentication) {
        return userProfileService.updateProfile(authentication.getName(), request);
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request, Authentication authentication) {
        userProfileService.changePassword(authentication.getName(), request);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserProfileDto uploadAvatar(@RequestParam("file") MultipartFile file, Authentication authentication) throws IOException {
        userProfileService.uploadAvatar(authentication.getName(), file);
        return userProfileService.getProfile(authentication.getName());
    }

    @GetMapping("/avatar")
    public ResponseEntity<Resource> getAvatar(Authentication authentication) throws IOException {
        Resource resource = userProfileService.loadAvatar(authentication.getName());
        if (resource == null) {
            return ResponseEntity.notFound().build();
        }
        String contentType = java.nio.file.Files.probeContentType(resource.getFile().toPath());
        return ResponseEntity.ok()
                .contentType(contentType != null ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}