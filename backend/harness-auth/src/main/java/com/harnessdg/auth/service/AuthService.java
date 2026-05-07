package com.harnessdg.auth.service;

import com.harnessdg.model.auth.dto.LoginRequest;
import com.harnessdg.model.auth.dto.LoginResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    LoginResponse refreshToken(String refreshToken);

    LoginResponse.UserInfo getCurrentUser(String username);
}
