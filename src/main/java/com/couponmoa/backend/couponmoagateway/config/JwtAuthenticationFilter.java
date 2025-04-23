package com.couponmoa.backend.couponmoagateway.config;

import com.couponmoa.backend.couponmoagateway.common.exception.ApplicationException;
import com.couponmoa.backend.couponmoagateway.common.exception.ErrorCode;
import com.couponmoa.backend.couponmoagateway.common.service.RedisService;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements WebFilter {

    @Value("${jwt.secret.key}")
    private String secretKey;
    private SecretKey key;

    private final RedisService redisService;
    private static final String BEARER_PREFIX = "Bearer ";

    @PostConstruct
    public void init() {
        log.info(">>> Loaded secret key: {}", secretKey);
        key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        log.debug("요청 경로: {}", path);

        if (isExcludedPath(path)) {
            log.debug("인증 제외 경로: {}", path);
            return chain.filter(exchange);
        }

        String authorizationHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String jwt = substringToken(authorizationHeader);
            try {
                Claims claims = extractClaims(jwt);
                validateTokenType(claims);
                validateRedisToken(claims.getSubject(), jwt);

                // 헤더에 정보 넣기
                String userId = claims.getSubject();
                String role = claims.get("userRole", String.class);

                ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                        .header("X-User-Id", userId)
                        .header("X-User-Role", role)
                        .build();

                ServerWebExchange mutatedExchange = exchange.mutate().request(modifiedRequest).build();
                return chain.filter(mutatedExchange);
            } catch (SecurityException | MalformedJwtException e) {
                throw new ApplicationException(ErrorCode.INVALID_JWT);
            } catch (ExpiredJwtException e) {
                throw new ApplicationException(ErrorCode.EXPIRED_JWT);
            } catch (UnsupportedJwtException e) {
                throw new ApplicationException(ErrorCode.UNSUPPORTED_JWT);
            } catch (ApplicationException e) {
                throw e;
            } catch (Exception e) {
                throw new ApplicationException(ErrorCode.EXCEPTION);
            }
        }
        return chain.filter(exchange);
    }

    private boolean isExcludedPath(String path) {
        return path.startsWith("/api/v1/auth") ||
                path.startsWith("/swagger-ui") ||
                path.equals("/swagger-ui.html") ||
                path.startsWith("/swagger-resources") ||
                path.startsWith("/v3/api-docs") ||
                path.startsWith("/webjars") ||
                path.startsWith("/actuator") ||
                path.equals("/health") ||
                path.equals("/error");
    }

    private String substringToken(String tokenValue) {
        if (StringUtils.hasText(tokenValue) && tokenValue.startsWith(BEARER_PREFIX)) {
            return tokenValue.substring(7);
        }
        throw new ApplicationException(ErrorCode.TOKEN_NOT_FOUND);
    }

    private Claims extractClaims(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        log.info("JWT 검증 완료 - userRole: {}, tokenType: {}", claims.get("userRole"), claims.get("tokenType"));
        return claims;
    }

    private void validateTokenType(Claims claims) {
        String tokenType = claims.get("tokenType", String.class);
        if ("refresh".equals(tokenType)) {
            throw new ApplicationException(ErrorCode.REFRESH_TOKEN_FORBIDDEN);
        }
    }

    private void validateRedisToken(String userId, String jwt) {
        String redisAccessToken = redisService.get("access:" + userId);
        if (redisAccessToken == null || !jwt.equals(substringToken(redisAccessToken))) {
            throw new ApplicationException(ErrorCode.INVALID_JWT);
        }
    }

}