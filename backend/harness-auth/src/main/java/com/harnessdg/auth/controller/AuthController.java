package com.harnessdg.auth.controller;

import com.harnessdg.auth.service.AuthService;
import com.harnessdg.common.response.R;
import com.harnessdg.model.auth.dto.LoginRequest;
import com.harnessdg.model.auth.dto.LoginResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return R.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public R<LoginResponse> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        return R.ok(authService.refreshToken(refreshToken));
    }

    @GetMapping("/me")
    public R<LoginResponse.UserInfo> me(Authentication authentication) {
        String username = (String) authentication.getPrincipal();
        return R.ok(authService.getCurrentUser(username));
    }
}
