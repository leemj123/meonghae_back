package com.moenghae.apigatewayservice.jwt;

import com.thoughtworks.xstream.security.ForbiddenClassException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.*;

@Slf4j
@Component
@Transactional
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final RedisService redisService;
    private final RestTemplate restTemplate;

    // 키
    @Value("${jwt.secret}")
    private String secretKey;

    // 액세스 토큰 유효시간 | 1h
    @Value("${jwt.accessTokenExpiration}")
    private long accessTokenValidTime;

    // 객체 초기화, secretKey를 Base64로 인코딩한다.
    @PostConstruct // 의존성 주입 후, 초기화를 수행
    protected void init() {
        secretKey = Base64.getEncoder().encodeToString(secretKey.getBytes());
    }

    // Access Token 생성.
    public String createAccessToken(String email, String roles) {
        return this.createToken(email, roles, accessTokenValidTime);
    }

    // Create token
    public String createToken(String email, String roles, long tokenValid) {
        Claims claims = Jwts.claims().setSubject(email); // claims 생성 및 payload 설정
        claims.put("roles", roles); // 권한 설정, key/ value 쌍으로 저장

        Date date = new Date();
        return Jwts.builder()
                .setClaims(claims) // 발행 유저 정보 저장
                .setIssuedAt(date) // 발행 시간 저장
                .setExpiration(new Date(date.getTime() + tokenValid)) // 토큰 유효 시간 저장
                .signWith(SignatureAlgorithm.HS256, secretKey) // 해싱 알고리즘 및 키 설정
                .compact(); // 생성
    }

    public String reissueAccessToken(String refreshToken) {
        Map<String, String> values = redisService.getValues(refreshToken);
        String email = values.get("email");

        if (Objects.isNull(email)) {
            throw new ForbiddenClassException(Exception.class);
        }

        String url = "https://api.meonghae.site/user-service/users/" + email;
        ResponseEntity<String> responseEntity = restTemplate.getForEntity(url, String.class);

        String accessToken = createAccessToken(email, responseEntity.getBody());

        return accessToken;
    }

    // Request의 Header에서 AccessToken 값을 가져옵니다. "Authorization" : "token"
    public String resolveAccessToken(ServerHttpRequest request) {
        List<String> authorizationHeaders = request.getHeaders().get(HttpHeaders.AUTHORIZATION);
        if (authorizationHeaders != null && !authorizationHeaders.isEmpty()) {
            String authorizationHeader = authorizationHeaders.get(0);
            if (authorizationHeader.startsWith("bearer ")) {
                return authorizationHeader.substring(7);
            }
        }
        return null;
    }

    // Request의 Header에서 RefreshToken 값을 가져옵니다. "refreshToken" : "token"
    public String resolveRefreshToken(ServerHttpRequest request) {
        if(request.getHeaders().containsKey("refreshToken"))
            return request.getHeaders().get("refreshToken").get(0).substring(7);
        return null;
    }

    // 토큰의 유효성 + 만료일자 확인
    public boolean validateToken(String jwtToken) {
        try {
            Jws<Claims> claims = Jwts.parser().setSigningKey(secretKey).parseClaimsJws(jwtToken);
            return !claims.getBody().getExpiration().before(new Date());
        } catch (MalformedJwtException e) {
            throw new MalformedJwtException("Invalid JWT token");
        } catch (ExpiredJwtException e) {
            throw new JwtExpiredException("JWT token has expired");
        } catch (UnsupportedJwtException e) {
            throw new UnsupportedJwtException("JWT token is unsupported");
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("JWT claims string is empty");
        } catch (SignatureException e) {
            throw new SignatureException("JWT signature does not match");
        }
    }

    // 어세스 토큰 헤더 설정
    public void setHeaderAccessToken(ServerHttpResponse response, String accessToken) {
        response.getHeaders().remove(HttpHeaders.AUTHORIZATION);
        response.getHeaders().add(HttpHeaders.AUTHORIZATION, "bearer "+ accessToken);
    }
}
