package com.example.newsmaterial.security;

import com.example.newsmaterial.common.api.ApiException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthApi {
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository sessions;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final AccountService accounts;

    public AuthApi(AuthenticationManager authenticationManager, SecurityContextRepository sessions,
                   SessionAuthenticationStrategy sessionAuthenticationStrategy, AccountService accounts) {
        this.authenticationManager = authenticationManager;
        this.sessions = sessions;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.accounts = accounts;
    }

    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
                        HttpServletResponse response) {
        return establishSession(body.username(), body.password(), request, response);
    }

    @PostMapping("/register")
    ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest body,
                                           HttpServletRequest request,
                                           HttpServletResponse response) {
        AppUser account = accounts.register(body.username(), body.password());
        LoginResponse user = establishSession(account.getUsername(), body.password(), request, response);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @PostMapping("/password")
    ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthenticatedUser user,
                                        @Valid @RequestBody ChangePasswordRequest body) {
        accounts.changePassword(user.id(), body.currentPassword(), body.newPassword());
        return ResponseEntity.noContent().build();
    }

    private LoginResponse establishSession(String username, String password,
                                           HttpServletRequest request,
                                           HttpServletResponse response) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username, password));
            sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            sessions.saveContext(context, request, response);
            AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
            return new LoginResponse(user.id(), user.username());
        } catch (BadCredentialsException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password");
        }
    }

    @GetMapping("/me")
    LoginResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return new LoginResponse(user.id(), user.username());
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/csrf")
    CsrfResponse csrf(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token == null) {
            token = (CsrfToken) request.getAttribute("_csrf");
        }
        Cookie cookie = new Cookie("XSRF-TOKEN", token.getToken());
        cookie.setPath("/");
        response.addCookie(cookie);
        return new CsrfResponse(token.getToken(), token.getHeaderName());
    }

    record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    record RegisterRequest(
            @NotBlank
            @Size(min = 3, max = 32)
            @Pattern(
                    regexp = "[a-z0-9](?:[a-z0-9._-]*[a-z0-9])?",
                    message = "Use lowercase letters, numbers, dots, underscores, or hyphens")
            String username,
            @NotBlank @Size(min = 8, max = 72) String password) {
    }

    record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    record LoginResponse(UUID id, String username) {
    }

    record CsrfResponse(String token, String headerName) {
    }
}
