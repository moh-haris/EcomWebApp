package com.haris.SpringEcom.security;

import com.haris.SpringEcom.model.AuthProviderType;
import com.haris.SpringEcom.model.Role;
import com.haris.SpringEcom.model.User;
import com.haris.SpringEcom.model.dto.LoginRequestDto;
import com.haris.SpringEcom.model.dto.LoginResponseDto;
import com.haris.SpringEcom.model.dto.SignUpRequestDto;
import com.haris.SpringEcom.model.dto.SignupResponseDto;
import com.haris.SpringEcom.repo.UserRepository;
import com.haris.SpringEcom.error.UserAlreadyExistsException;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final AuthUtil authUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public LoginResponseDto login(LoginRequestDto loginRequestDto) {

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequestDto.getUsername(), loginRequestDto.getPassword())
        );

        User user = (User) authentication.getPrincipal();

        String token = authUtil.generateAccessToken(user);

        return new LoginResponseDto(token, user.getId(), user.getRole().name());
    }

    public User signUpInternal(SignUpRequestDto signupRequestDto, AuthProviderType authProviderType, String providerId) {
        User user = userRepository.findByUsername(signupRequestDto.getUsername()).orElse(null);

        if(user != null) throw new UserAlreadyExistsException(signupRequestDto.getUsername());

        user = User.builder()
                .username(signupRequestDto.getUsername())
                .providerId(providerId)
                .providerType(authProviderType)
                .role(Role.USER)
                .build();

        if(authProviderType == AuthProviderType.EMAIL) {
            user.setPassword(passwordEncoder.encode(signupRequestDto.getPassword()));
        }
       return userRepository.save(user);
    }
    public SignupResponseDto signup(SignUpRequestDto signupRequestDto) {
        User user=signUpInternal(signupRequestDto, AuthProviderType.EMAIL, null);

        return new SignupResponseDto(user.getId(), user.getUsername());
    }


    @Transactional
    public ResponseEntity<LoginResponseDto> handleOAuth2LoginRequest(OAuth2User oAuth2User, String registrationId) {
        AuthProviderType providerType = authUtil.getProviderTypeFromRegistrationId(registrationId);
        String providerId = authUtil.determineProviderIdFromOAuth2User(oAuth2User, registrationId);

        User user = userRepository.findByProviderIdAndProviderType(providerId, providerType).orElse(null);
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        User emailUser = userRepository.findByUsername(email).orElse(null);

        if(user == null && emailUser == null) {

            String username = authUtil.determineUsernameFromOAuth2User(oAuth2User, registrationId, providerId);
            user=signUpInternal(new SignUpRequestDto(username, null, name), providerType, providerId);

        } else if(user != null) {

            if(email != null && !email.isBlank() && !email.equals(user.getUsername())) {
                user.setUsername(email);
                userRepository.save(user);
            }

        } else {
            throw new BadCredentialsException("This email is already registered with provider "+emailUser.getProviderType());
        }
        LoginResponseDto loginResponseDto = new LoginResponseDto(authUtil.generateAccessToken(user), user.getId(), user.getRole().name());
        return ResponseEntity.ok(loginResponseDto); 
    }
}


