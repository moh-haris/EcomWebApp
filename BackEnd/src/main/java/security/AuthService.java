package com.haris.SpringEcom.security;

import com.haris.SpringEcom.model.AuthProviderType;
import com.haris.SpringEcom.model.Role;
import com.haris.SpringEcom.model.User;
import com.haris.SpringEcom.model.dto.LoginRequestDto;
import com.haris.SpringEcom.model.dto.LoginResponseDto;
import com.haris.SpringEcom.model.dto.SignUpRequestDto;
import com.haris.SpringEcom.model.dto.SignupResponseDto;
import com.haris.SpringEcom.repo.UserRepository;
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

        // RBAC: Include the role in the login response so the frontend
        // knows immediately whether to show admin controls or not.
        return new LoginResponseDto(token, user.getId(), user.getRole().name());
    }
//Fetch providerType and providerId. Save the providerType and provider id info with user
    //if the user has an account: directly login otherwise, first signup and then login

    //This is internal method
    //Remember Bcrypt error occur if we want to encrypt but it give eror for null pswd can't encrypt null pswd
    public User signUpInternal(SignUpRequestDto signupRequestDto, AuthProviderType authProviderType, String providerId) {
        User user = userRepository.findByUsername(signupRequestDto.getUsername()).orElse(null);

        if(user != null) throw new IllegalArgumentException("User already exists");

        user = User.builder()
                .username(signupRequestDto.getUsername())
                .providerId(providerId)
                .providerType(authProviderType)
                // RBAC: All self-registered users (email or OAuth2) always get
                // the USER role. Only the DataSeeder creates the hardcoded ADMIN.
                .role(Role.USER)
                .build();

        if(authProviderType == AuthProviderType.EMAIL) {
            user.setPassword(passwordEncoder.encode(signupRequestDto.getPassword()));
        }
       return userRepository.save(user);
    }
    //This is login controller method(through email id) here provider id and type is null
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
    //Checking If user's email already exist so avoid duplicate login
        String name = oAuth2User.getAttribute("name");

        User emailUser = userRepository.findByUsername(email).orElse(null);
//Login through OAuth2 provider
        if(user == null && emailUser == null) {
            // signup flow:
            String username = authUtil.determineUsernameFromOAuth2User(oAuth2User, registrationId, providerId);
            user=signUpInternal(new SignUpRequestDto(username, null, name), providerType, providerId);
        //Pswd will not store coz if provider is google paswd not stored it only store if user sign with email & pswd
        } else if(user != null) {
            //if user got form provider id and provider type then no need to signup just login
            if(email != null && !email.isBlank() && !email.equals(user.getUsername())) {
                user.setUsername(email);
                userRepository.save(user);
            }
        //in above code now we store in username anything like name or email but when correct email get I will store
        //email and remove any other username.

        } else {
        //if user is null and emailuser is not null that means this email already exist and I entered in Oauth2 it try
        //to login with email but email aleady exist so it should not try.
            throw new BadCredentialsException("This email is already registered with provider "+emailUser.getProviderType());
        }

        // RBAC: Include role in OAuth2 login response too, same as email login.
        LoginResponseDto loginResponseDto = new LoginResponseDto(authUtil.generateAccessToken(user), user.getId(), user.getRole().name());
        return ResponseEntity.ok(loginResponseDto); //Response is return in json format
    }
}


