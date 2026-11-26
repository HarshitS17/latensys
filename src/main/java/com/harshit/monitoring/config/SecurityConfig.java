package com.harshit.monitoring.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService administrator(@Value("${security.admin.username}") String username,
                                     @Value("${security.admin.password-hash}") String hash) {
        if (username.isBlank() || !hash.matches("\\$2[aby]\\$\\d{2}\\$.{53}")) {
            throw new IllegalArgumentException("Configure an administrator username and BCrypt password hash");
        }
        return new InMemoryUserDetailsManager(User.withUsername(username).password(hash).roles("ADMIN").build());
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/style.css", "/actuator/health", "/auth/csrf", "/app/**").permitAll()
                .anyRequest().authenticated())
            .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/", true).permitAll())
            .logout(logout -> logout.logoutSuccessUrl("/login?logout").invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID"))
            .csrf(csrf -> csrf.ignoringRequestMatchers(new AntPathRequestMatcher("/app/**")))
            .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, failure) -> {
                String path = request.getRequestURI();
                if (path.startsWith("/monitor/") || path.startsWith("/actuator/")
                        || path.startsWith("/test/")) {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"unauthorized\"}");
                } else {
                    response.sendRedirect(request.getContextPath() + "/login");
                }
            }));
        return http.build();
    }
}
