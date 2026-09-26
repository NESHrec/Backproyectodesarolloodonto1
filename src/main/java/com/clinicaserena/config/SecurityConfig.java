package com.clinicaserena.config;

import com.clinicaserena.auth.security.PatientBearerAuthenticationFilter;
import com.clinicaserena.common.response.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * Configuración base de seguridad.
 *
 * El catálogo y health son públicos. La autenticación de pacientes usa tokens
 * Bearer opacos persistidos como hash; la sesión de Spring sigue siendo stateless.
 * CSRF permanece deshabilitado porque no se autentica con cookies.
 */
@Configuration
@EnableWebSecurity
@EnableScheduling
public class SecurityConfig {

    private static final String[] PUBLIC_DOCUMENTATION_PATHS = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            PatientBearerAuthenticationFilter bearerFilter
    ) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(unauthorizedEntryPoint()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/health",
                                "/api/v1/especialidades",
                                "/api/v1/medicos",
                                "/api/v1/medicos/*/disponibilidad"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/auth/me", "/api/v1/auth/logout").hasRole("PACIENTE")
                        .requestMatchers("/api/v1/pacientes/**", "/api/v1/citas/**").hasRole("PACIENTE")
                        .requestMatchers(PUBLIC_DOCUMENTATION_PATHS).permitAll()
                        .anyRequest().denyAll()
                );

        http.addFilterBefore(bearerFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, exception) -> {
            response.setStatus(UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
            response.getWriter().write(new ObjectMapper().writeValueAsString(
                    ApiError.of(UNAUTHORIZED.value(), "UNAUTHENTICATED", "Autenticación requerida")
            ));
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:3000"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public PatientBearerAuthenticationFilter patientBearerAuthenticationFilter(
            ObjectProvider<com.clinicaserena.auth.repository.SesionPacienteRepository> sesionRepository
    ) {
        return new PatientBearerAuthenticationFilter(sesionRepository);
    }

    /**
     * El filtro pertenece a Spring Security y no debe registrarse como filtro
     * Servlet independiente, porque entonces se ejecutaría dos veces.
     */
    @Bean
    public FilterRegistrationBean<PatientBearerAuthenticationFilter> patientBearerAuthenticationFilterRegistration(
            PatientBearerAuthenticationFilter filter
    ) {
        FilterRegistrationBean<PatientBearerAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public UserDetailsService unusedDefaultUserDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("La autenticación usa cuentas de pacientes");
        };
    }
}
