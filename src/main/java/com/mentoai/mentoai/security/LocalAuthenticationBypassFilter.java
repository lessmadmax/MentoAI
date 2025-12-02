package com.mentoai.mentoai.security;

import com.mentoai.mentoai.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 로컬 프로파일에서만 동작하는 필터.
 * 별도의 토큰 없이도 기본 사용자로 인증을 채워 넣어 테스트할 수 있도록 한다.
 */
@Component
@Profile("h2")
@RequiredArgsConstructor
@Slf4j
public class LocalAuthenticationBypassFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    @Value("${security.local.default-user-id:1}")
    private Long defaultUserId;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            userRepository.findById(defaultUserId).ifPresentOrElse(user -> {
                UserPrincipal principal = UserPrincipal.from(user);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }, () -> log.warn("[local-auth-bypass] 기본 사용자(ID={})가 존재하지 않습니다.", defaultUserId));
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/swagger-ui.html")
                || path.startsWith("/docs");
    }
}


