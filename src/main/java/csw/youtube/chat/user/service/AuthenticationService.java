package csw.youtube.chat.user.service;


import csw.youtube.chat.common.config.security.JwtService;
import csw.youtube.chat.common.exception.DuplicateResourceException;
import csw.youtube.chat.common.exception.ResourceNotFoundException;
import csw.youtube.chat.user.dto.AuthenticationRequest;
import csw.youtube.chat.user.dto.AuthenticationResponse;
import csw.youtube.chat.user.dto.RefreshTokenRequest;
import csw.youtube.chat.user.dto.UserRegisterRequest;
import csw.youtube.chat.user.model.User;
import csw.youtube.chat.user.model.permission.Role;
import csw.youtube.chat.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthenticationService {
    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional // Dirty Read
    // AuthenticationResponse
    public void register(UserRegisterRequest request) {
        // Check for duplicate username
        // 트랜잭션 전파 수준(Propagation.SUPPORTS)은 상위 트랜잭션(register)이 존재하면 그 트랜잭션에 참여
        if (usernameExists(request.username())) {
            throw new DuplicateResourceException("Username already exists");
        }

        // Check for duplicate email
        if (emailExists(request.email())) {
            throw new DuplicateResourceException("Email already exists");
        }

        var user = User.builder()
                .username(request.username())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.USER) // default role
                .build();
        repository.save(user); // var savedUser =
//        var jwtToken = jwtService.generateToken(user);
//        var refreshToken = jwtService.generateRefreshToken(user);
//        return AuthenticationResponse.builder()
//                .accessToken(jwtToken)
//                .refreshToken(refreshToken)
//                .build();
    }

    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.username(),
                        request.password()
                )
        );
        var user = repository.findByUsername(request.username())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        var jwtToken = jwtService.generateToken(user);
        var refreshToken = jwtService.generateRefreshToken(user);
        return AuthenticationResponse.builder()
                .accessToken(jwtToken)
                .refreshToken(refreshToken)
                .build();
    }

    public AuthenticationResponse refreshToken(
            RefreshTokenRequest request
    ) {
        String userEmail = jwtService.extractUsername(request.refreshToken());
        if (userEmail != null) {
            var user = this.repository.findByUsername(userEmail)
                    .orElseThrow();
            if (jwtService.isTokenValid(request.refreshToken(), user)) {
                var accessToken = jwtService.generateToken(user);
                var newRefreshToken = jwtService.generateRefreshToken(user);
                return AuthenticationResponse.builder()
                        .accessToken(accessToken)
                        .refreshToken(newRefreshToken)
                        .build();
            }
        }
        throw new RuntimeException("Invalid refresh token");
    }

    // 단순 조회 (BEGIN과 COMMIT 명령이 추가로 실행되지 않음)
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public boolean usernameExists(String username) {
        return repository.existsByUsername(username);
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public boolean emailExists(String email) {
        return repository.existsByEmail(email);
    }
}