package com.bank.core.infrastructure.adapter.in.web.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Deux chaînes de filtres distinctes :
 * <ul>
 *   <li>API REST ({@code /api/**}) : stateless, JSON, sans CSRF, avec nos
 *       {@link RestAuthenticationEntryPoint} (401) et {@link RestAccessDeniedHandler} (403).</li>
 *   <li>Vues JSF/PrimeFaces : session, CSRF activé, form login.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurity(HttpSecurity http,
                                           RestAuthenticationEntryPoint entryPoint,
                                           RestAccessDeniedHandler accessDenied) throws Exception {
        http
                .securityMatcher("/api/**")
                .authorizeHttpRequests(reg -> reg
                        .requestMatchers("/api/v1/public/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDenied))
                // Aucun mécanisme d'auth interactif sur l'API : brancher ici votre filtre
                // JWT/OAuth2 Resource Server. Les requêtes non authentifiées tombent
                // directement sur notre AuthenticationEntryPoint (401 JSON).
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain webSecurity(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(reg -> reg
                        .requestMatchers("/login.xhtml", "/error.xhtml",
                                "/jakarta.faces.resource/**", "/javax.faces.resource/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login.xhtml")
                        .permitAll());
        // CSRF reste activé (par défaut) pour le canal JSF.
        return http.build();
    }
}
