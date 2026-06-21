package ASSRONE.backend.config;

import ASSRONE.backend.model.UserRole;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import java.util.Objects;

public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, @NonNull HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        String redirectURL = request.getContextPath();

        if (authentication.getAuthorities().stream().anyMatch(a -> Objects.equals(a.getAuthority(), String.valueOf(UserRole.ADMIN)))) {
            redirectURL = "/admin/home";
        } else if (authentication.getAuthorities().stream().anyMatch(a -> Objects.equals(a.getAuthority(), String.valueOf(UserRole.USER)))) {
            redirectURL = "/user/home";
        }

        response.sendRedirect(redirectURL);
    }
}
