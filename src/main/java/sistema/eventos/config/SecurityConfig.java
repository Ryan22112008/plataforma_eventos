package sistema.eventos.config;

import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Libera as pastas estáticas padrão (css, js, etc.)
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()

                        // LINHA ATUALIZADA: Liberando explicitamente a pasta das imagens estáticas do sistema
                        .requestMatchers(HttpMethod.POST, "/eventos/*/inscrever").authenticated()
                        .requestMatchers("/", "/login", "/cadastro", "/error", "/erro/**", "/eventos/**", "/imagens-eventos/**", "/certificados/validar").permitAll()
                        .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/presencas/checkin").hasRole("PARTICIPANTE")
                        .requestMatchers("/presencas/**").hasAnyRole("ORGANIZADOR", "ADMIN")
                        .requestMatchers("/participante/**").hasRole("PARTICIPANTE")

                        // Proteção estrita de escopo baseado nas roles
                        .requestMatchers("/organizador/**").hasAnyRole("ORGANIZADOR", "ADMIN")
                        .anyRequest().authenticated()
                )
                .csrf(org.springframework.security.config.Customizer.withDefaults())
                .headers(headers -> headers
                        .contentTypeOptions(org.springframework.security.config.Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicy(permissions -> permissions.policy("camera=(), microphone=(), geolocation=()"))
                        // Spring Security habilita HSTS por padrão; mantém a API compatível com a versão gerenciada pelo Boot.
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .defaultSuccessUrl("/dashboard")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout=true")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .permitAll()
                )
                .exceptionHandling(exception -> exception
                        .accessDeniedPage("/erro/403")
                );

        return http.build();
    }
}
