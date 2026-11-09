package com.smallbiz.security;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProperties.Cors cors) throws Exception {
        http
            // 纯 API + Bearer 令牌，不使用会话；刷新 Cookie 为 SameSite=Lax 且仅限 /api/v1/auth 路径
            .csrf(csrf -> csrf.disable())
            .cors(c -> c.configurationSource(corsSource(cors)))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST,
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/logout").permitAll()
                .requestMatchers("/error").permitAll()
                // 员工邀请链接预览（令牌为 256 位随机数，按 IP 限速）
                .requestMatchers(HttpMethod.GET, "/api/v1/invitations/preview").permitAll()
                // 健康检查（只暴露 health；Nginx 不对外转发 /actuator）
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                // 客服机器人：未登录访客也可以提问（按 IP 限速）
                .requestMatchers("/api/v1/support/**").permitAll()
                // 支付渠道的付款通知：不登录，靠签名校验
                .requestMatchers(HttpMethod.POST, "/api/v1/payments/webhook/**").permitAll()
                // 商品图片：文件名是不可猜测的随机 UUID，<img> 标签无法携带令牌，因此公开读取
                .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll())
            .oauth2ResourceServer(rs -> rs
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                .authenticationEntryPoint((req, res, ex) ->
                        writeError(res, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "登录已过期，请重新登录"))
                .accessDeniedHandler((req, res, ex) ->
                        writeError(res, HttpStatus.FORBIDDEN, "FORBIDDEN", "没有权限执行此操作")))
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) ->
                        writeError(res, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "请先登录"))
                .accessDeniedHandler((req, res, ex) ->
                        writeError(res, HttpStatus.FORBIDDEN, "FORBIDDEN", "没有权限执行此操作")));
        return http.build();
    }

    private static void writeError(HttpServletResponse res, HttpStatus status, String code, String message)
            throws java.io.IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // code/message 均为固定常量，不含需要转义的字符
        res.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"details\":{}}");
    }

    /** JWT 中的 role → ROLE_OWNER / ROLE_ADMIN / ROLE_STAFF；pa=true → ROLE_PLATFORM_ADMIN。 */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter((Jwt jwt) -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            String role = jwt.getClaimAsString(TokenService.CLAIM_ROLE);
            if (role != null) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
            }
            if (Boolean.TRUE.equals(jwt.getClaimAsBoolean(TokenService.CLAIM_PLATFORM_ADMIN))) {
                authorities.add(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
            }
            return authorities;
        });
        return converter;
    }

    private CorsConfigurationSource corsSource(SecurityProperties.Cors cors) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(cors.allowedOrigins() == null ? List.of() : cors.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private static SecretKey secretKey(SecurityProperties props) {
        return new SecretKeySpec(props.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecurityProperties props) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(props)));
    }

    @Bean
    JwtDecoder jwtDecoder(SecurityProperties props) {
        return NimbusJwtDecoder.withSecretKey(secretKey(props)).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
