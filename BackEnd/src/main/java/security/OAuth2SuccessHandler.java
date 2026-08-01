package com.haris.SpringEcom.security;

import com.haris.SpringEcom.model.dto.LoginResponseDto;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;

    // The React frontend URL that handles the OAuth2 callback
    private static final String FRONTEND_REDIRECT_URL = "http://localhost:5173/oauth2/redirect";

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String registrationId = token.getAuthorizedClientRegistrationId();

        try {
            ResponseEntity<LoginResponseDto> loginResponse = authService.handleOAuth2LoginRequest(oAuth2User, registrationId);

            // Extract the JWT from the response body
            String jwtToken = loginResponse.getBody().getJwt();

            // Redirect to the React frontend with the token as a URL query parameter.
            // The frontend's /oauth2/redirect page reads ?token=... and saves it to localStorage,
            // then navigates the user to the home page as a logged-in user.
            response.sendRedirect(FRONTEND_REDIRECT_URL + "?token=" + jwtToken);

        } catch (Exception e) {
            // If authentication fails (e.g. email already registered with a different provider),
            // redirect back to the login page with a human-readable error message
            // instead of showing a raw JSON black screen.
            String errorMessage = java.net.URLEncoder.encode(e.getMessage(), "UTF-8");
            response.sendRedirect("http://localhost:5173/login?error=" + errorMessage);
        }
    }
}
