package ASSRONE.backend.controller;

import ASSRONE.backend.dto.UserDto;
import ASSRONE.backend.service.UserInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserInfoService userInfoService;

    @GetMapping
    public List<UserDto> getAll() {
        return userInfoService.getAll();
    }
}
