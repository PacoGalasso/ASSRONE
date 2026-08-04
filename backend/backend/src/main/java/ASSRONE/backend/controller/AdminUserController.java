package ASSRONE.backend.controller;

import ASSRONE.backend.dto.RoleChangeRequest;
import ASSRONE.backend.dto.UserDto;
import ASSRONE.backend.service.AdminUserManagementService;
import ASSRONE.backend.service.UserInfoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserInfoService userInfoService;
    private final AdminUserManagementService adminUserManagementService;

    @GetMapping
    public List<UserDto> getAll() {
        return userInfoService.getAll();
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<Void> changeRole(@PathVariable Long id, @Valid @RequestBody RoleChangeRequest request,
                                            Authentication authentication) {
        adminUserManagementService.changeRole(authentication.getName(), id, request.getRole());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id, Authentication authentication) {
        adminUserManagementService.deleteUser(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
