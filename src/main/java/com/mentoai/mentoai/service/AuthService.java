package com.mentoai.mentoai.service;

import com.mentoai.mentoai.controller.dto.AuthResult;
import com.mentoai.mentoai.controller.dto.AuthTokens;
import com.mentoai.mentoai.controller.dto.GoogleTokenResponse;
import com.mentoai.mentoai.controller.dto.GoogleUserInfo;
import com.mentoai.mentoai.entity.RefreshTokenEntity;
import com.mentoai.mentoai.entity.UserEntity;
import com.mentoai.mentoai.exception.UnauthorizedException;
import com.mentoai.mentoai.repository.RefreshTokenRepository;
import com.mentoai.mentoai.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final String OAUTH_STATE_SESSION_KEY = "OAUTH_STATE";
    private static final String OAUTH_FRONTEND_REDIRECT_SESSION_KEY = "OAUTH_FRONTEND_REDIRECT";

    private final GoogleOAuthClient googleOAuthClient;
    private final UserService userService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;

    private final SecureRandom secureRandom = new SecureRandom();

    public void rememberFrontendRedirect(HttpServletRequest request, String redirectUri) {
        if (!StringUtils.hasText(redirectUri)) {
            return;
        }
        request.getSession(true).setAttribute(OAUTH_FRONTEND_REDIRECT_SESSION_KEY, redirectUri);
    }

    public URI buildAuthorizationRedirect(HttpServletRequest request) {
        String state = generateState();
        request.getSession(true).setAttribute(OAUTH_STATE_SESSION_KEY, state);
        boolean useLocal = isLocalRequest(request);
        return googleOAuthClient.buildAuthorizationUri(state, useLocal);
    }

    @Transactional
    public ResponseEntity<?> handleCallback(
            String code,
            String state,
            String mode,
            String redirectOverride,
            HttpServletRequest request
    ) {
        System.out.println(">>> [AuthService] handleCallback started");
        System.out.println(">>> mode: " + mode);
        System.out.println(">>> redirectOverride: " + redirectOverride);        HttpSession session = request.getSession(false);
        validateState(state, session);
        boolean useLocal = isLocalRequest(request);

        GoogleTokenResponse tokenResponse = googleOAuthClient.exchangeCodeForToken(code, useLocal);
        GoogleUserInfo userInfo = googleOAuthClient.fetchUserInfo(tokenResponse.accessToken());

        UserEntity user = userService.upsertOAuthUser(
                UserEntity.AuthProvider.GOOGLE,
                userInfo.id(),
                userInfo.email(),
                userInfo.name(),
                null,
                userInfo.picture()
        );

        AuthTokens tokens = issueTokens(user);
        clearState(session);
        String frontendRedirect = consumeFrontendRedirect(session, redirectOverride);

        System.out.println(">>> frontendRedirect: " + frontendRedirect);
        System.out.println(">>> tokens issued");


        if ("json".equalsIgnoreCase(mode)) {
            return ResponseEntity.ok(AuthResult.of(user, tokens));
        }
        try {
        URI redirectUri = buildFrontendRedirect(tokens, frontendRedirect);
        System.out.println(">>> redirecting to: " + redirectUri);
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(redirectUri);
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
        } catch (Exception e) {
        System.err.println(">>> Failed to build redirect URI: " + e.getMessage());
        e.printStackTrace();
        throw e;
        }
    }

    private AuthTokens issueTokens(UserEntity user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken();

        OffsetDateTime refreshExpiry = jwtTokenProvider.calculateRefreshTokenExpiry();

        refreshTokenRepository.deleteByUser(user);
        refreshTokenRepository.save(RefreshTokenEntity.builder()
                .user(user)
                .token(refreshToken)
                .expiresAt(refreshExpiry)
                .build());

        return AuthTokens.bearer(accessToken, refreshToken, jwtTokenProvider.getAccessTokenExpirySeconds());
    }

    public Optional<RefreshTokenEntity> findValidRefreshToken(String token) {
        return refreshTokenRepository.findByToken(token)
                .filter(rt -> rt.getExpiresAt().isAfter(OffsetDateTime.now()));
    }

    @Transactional
    public AuthTokens refresh(String refreshToken) {
        RefreshTokenEntity refreshTokenEntity = findValidRefreshToken(refreshToken)
                .orElseThrow(() -> new UnauthorizedException("유효하지 않은 리프레시 토큰입니다."));

        UserEntity user = refreshTokenEntity.getUser();
        return issueTokens(user);
    }

    @Transactional
    public void logout(UserEntity user) {
        refreshTokenRepository.deleteByUser(user);
    }

    private String generateState() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void validateState(String state, HttpSession session) {
        if (session == null) {
            throw new IllegalArgumentException("잘못된 인증 상태입니다.");
        }
        String expected = (String) session.getAttribute(OAUTH_STATE_SESSION_KEY);
        if (!StringUtils.hasText(state) || expected == null || !expected.equals(state)) {
            throw new IllegalArgumentException("잘못된 state 값입니다.");
        }
    }

    private void clearState(HttpSession session) {
        if (session != null) {
            session.removeAttribute(OAUTH_STATE_SESSION_KEY);
        }
    }

    private boolean isLocalRequest(HttpServletRequest request) {
        String host = request.getHeader("host");
        if (!StringUtils.hasText(host)) {
            host = request.getServerName();
        }
        return host != null && host.contains("localhost");
    }

    private URI buildFrontendRedirect(AuthTokens tokens, String frontendCallback) {
        return UriComponentsBuilder.fromUriString(frontendCallback)
                .fragment(String.format("accessToken=%s&refreshToken=%s&tokenType=%s&expiresIn=%d",
                        tokens.accessToken(),
                        tokens.refreshToken(),
                        tokens.tokenType(),
                        tokens.expiresIn()))
                .build(true)
                .toUri();
    }

    private String consumeFrontendRedirect(HttpSession session, String redirectOverride) {
        String redirect = StringUtils.hasText(redirectOverride) ? redirectOverride : null;
        System.out.println(">>> redirectOverride value: " + redirect);
        if (!StringUtils.hasText(redirect) && session != null) {
            Object value = session.getAttribute(OAUTH_FRONTEND_REDIRECT_SESSION_KEY);
            System.out.println(">>> redirect from session: " + value);
            if (value instanceof String saved) {
                redirect = saved;
            }
        }
        if (session != null) {
            session.removeAttribute(OAUTH_FRONTEND_REDIRECT_SESSION_KEY);
        }
        if (!StringUtils.hasText(redirect)) {
            redirect = googleOAuthClient.getFrontendCallbackUri();
            System.out.println(">>> using default frontendCallbackUri: " + redirect);
        }
        return redirect;
    }
}
