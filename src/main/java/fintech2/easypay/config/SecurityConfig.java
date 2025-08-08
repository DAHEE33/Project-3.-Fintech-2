package fintech2.easypay.config;

import fintech2.easypay.auth.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.ArrayList;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                // 인증 불필요 (Public)
                .requestMatchers("/api/auth/**").permitAll() // API 접두사 포함
                .requestMatchers("/auth/**").permitAll() // 기존 경로도 유지 (호환성)
                .requestMatchers("/h2-console/**").permitAll()
                .requestMatchers("/actuator/health").permitAll() // 헬스체크 엔드포인트 추가
                .requestMatchers("/api/accounts/test-balance/**").permitAll() // 테스트 엔드포인트
                // 정적 리소스 허용
                .requestMatchers("/static/**").permitAll()
                .requestMatchers("/js/**").permitAll()
                .requestMatchers("/css/**").permitAll()
                .requestMatchers("/favicon.ico").permitAll()
                .requestMatchers("/", "/index.html", "/register.html", "/login.html", "/main.html", "/balance.html", "/alarm.html", "/transfer.html", "/payment.html", "/account-management.html").permitAll()
                // 계좌 관련 API (JWT 인증 필요)
                .requestMatchers("/api/accounts/**").authenticated()
                .requestMatchers("/api/user-accounts/**").authenticated()
                .requestMatchers("/api/external-accounts/**").authenticated()
                .requestMatchers("/api/accounts/verify").authenticated()
                // 결제 관련 API (JWT 인증 필요)
                .requestMatchers("/api/payments/**").authenticated()
                // 송금 관련 API (JWT 인증 필요)
                .requestMatchers("/api/transfers/**").authenticated()
                // PIN 관련 API (JWT 인증 필요)
                .requestMatchers("/api/pin/**").authenticated()
                // 알림 관련 API (JWT 인증 필요)
                .requestMatchers("/api/alarms/**").authenticated()
                // 나머지 모든 요청은 인증 필요
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        // H2 콘솔을 위한 헤더 설정
        http.headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()));

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt with strength 12 (2^12 = 4096 rounds)
        // 기본값 10보다 강화된 보안, 하지만 성능과 균형
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        
        // 외부 변경 가능 객체 문제 해결을 위해 불변 객체로 복사
        CorsConfiguration immutableConfiguration = new CorsConfiguration();
        
        // null 체크 추가하여 null pointer dereference 방지
        if (configuration.getAllowedOriginPatterns() != null) {
            immutableConfiguration.setAllowedOriginPatterns(new ArrayList<>(configuration.getAllowedOriginPatterns()));
        }
        if (configuration.getAllowedMethods() != null) {
            immutableConfiguration.setAllowedMethods(new ArrayList<>(configuration.getAllowedMethods()));
        }
        if (configuration.getAllowedHeaders() != null) {
            immutableConfiguration.setAllowedHeaders(new ArrayList<>(configuration.getAllowedHeaders()));
        }
        immutableConfiguration.setAllowCredentials(configuration.getAllowCredentials());
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", immutableConfiguration);
        return source;
    }
} 