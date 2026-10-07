package com.clinicaserena.config;

import com.clinicaserena.auth.security.PatientBearerAuthenticationFilter;
import com.clinicaserena.staff.security.StaffBearerAuthenticationFilter;
import com.clinicaserena.common.response.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.springframework.http.HttpStatus.FORBIDDEN;
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
            "/openapi.yaml",
            "/v3/api-docs/swagger-config",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            PatientBearerAuthenticationFilter bearerFilter,
            StaffBearerAuthenticationFilter staffBearerFilter,
            Environment environment
    ) throws Exception {
        boolean developmentDocumentationEnabled = environment.acceptsProfiles(Profiles.of("dev"));

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(unauthorizedEntryPoint())
                        .accessDeniedHandler(forbiddenHandler()))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.GET,
                                "/api/v1/health",
                                "/api/v1/especialidades",
                                "/api/v1/medicos",
                                "/api/v1/medicos/*/disponibilidad"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/login-unified", "/api/v1/auth/register",
                                "/api/v1/auth/verify-email", "/api/v1/auth/resend-verification", "/api/v1/auth/password-recovery",
                                "/api/v1/auth/password-reset").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/staff/auth/login").permitAll()
                        .requestMatchers("/api/v1/auth/me", "/api/v1/auth/logout").hasRole("PACIENTE")
                        .requestMatchers("/api/v1/pacientes/**", "/api/v1/citas/**").hasRole("PACIENTE")
                        .requestMatchers("/api/v1/staff/auth/me", "/api/v1/staff/auth/logout")
                        .hasAnyRole("ADMIN", "RECEPCION", "MEDICO")
                        .requestMatchers(HttpMethod.POST, "/api/v1/staff/accounts").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/staff/accounts").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/staff/accounts/*/practitioner").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/staff/accounts/*/practitioner").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/staff/especialidades").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/staff/especialidades").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/staff/especialidades/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/staff/medico/horarios").hasRole("MEDICO")
                        .requestMatchers(HttpMethod.POST, "/api/v1/staff/medico/horarios").hasRole("MEDICO")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/staff/medico/horarios/*").hasRole("MEDICO")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/staff/medico/horarios/*").hasRole("MEDICO")
                        .requestMatchers(HttpMethod.GET, "/api/v1/staff/patients").hasAnyRole("ADMIN", "RECEPCION")
                        .requestMatchers(HttpMethod.POST, "/api/v1/staff/patients").hasRole("RECEPCION")
                        .requestMatchers(HttpMethod.POST, "/api/v1/staff/patients/*/link-requests").hasRole("RECEPCION")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/staff/patient-link-requests/*").hasRole("RECEPCION")
                        .requestMatchers(HttpMethod.GET, "/api/v1/staff/audit-events").hasRole("ADMIN")
                        .requestMatchers("/api/v1/medico/**").hasRole("MEDICO")
                        .requestMatchers(HttpMethod.GET, "/api/v1/staff/agenda").hasAnyRole("ADMIN", "RECEPCION")
                        .requestMatchers(HttpMethod.POST, "/api/v1/staff/agenda/*/arrival").hasRole("RECEPCION")
                        .requestMatchers("/api/v1/staff/billing/**").hasRole("RECEPCION");
                    if (developmentDocumentationEnabled) {
                        auth.requestMatchers(HttpMethod.GET, PUBLIC_DOCUMENTATION_PATHS).permitAll();
                    }
                    auth.anyRequest().denyAll();
                });

        http.addFilterBefore(staffBearerFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(bearerFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, exception) -> writeError(response,
                ApiError.of(UNAUTHORIZED.value(), "UNAUTHENTICATED", "Autenticación requerida"));
    }

    /**
     * Escribe el 403 directamente. El manejador por defecto usa sendError y el
     * contenedor reenvía a /error, donde la solicitud llega sin autenticación y
     * se respondía 401 aunque la sesión fuera válida.
     */
    private AccessDeniedHandler forbiddenHandler() {
        return (request, response, exception) -> writeError(response,
                ApiError.of(FORBIDDEN.value(), "FORBIDDEN", "No tienes permiso para esta operación"));
    }

    private static void writeError(HttpServletResponse response, ApiError error) throws java.io.IOException {
        response.setStatus(error.status());
        response.setContentType("application/json");
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        response.getWriter().write(new ObjectMapper().writeValueAsString(error));
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
    public StaffBearerAuthenticationFilter staffBearerAuthenticationFilter(
            ObjectProvider<com.clinicaserena.staff.repository.SesionPersonalRepository> sessionRepository
    ) {
        return new StaffBearerAuthenticationFilter(sessionRepository);
    }

    @Bean
    public FilterRegistrationBean<StaffBearerAuthenticationFilter> staffBearerAuthenticationFilterRegistration(
            StaffBearerAuthenticationFilter filter
    ) {
        FilterRegistrationBean<StaffBearerAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
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
