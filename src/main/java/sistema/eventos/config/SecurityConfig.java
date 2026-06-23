package sistema.eventos.config;

import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
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
                        .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()

                        // LINHA ATUALIZADA: Liberando explicitamente a pasta das imagens estáticas do sistema
                        .requestMatchers("/", "/login", "/cadastro", "/eventos/**", "/imagens-eventos/**", "/images/**").permitAll()
                        .requestMatchers(PathRequest.toH2Console()).permitAll()

                        // Proteção estrita de escopo baseado nas roles
                        .requestMatchers("/organizador/**").hasRole("ORGANIZADOR")
                        .anyRequest().authenticated()
                )
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(PathRequest.toH2Console())
                )
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .defaultSuccessUrl("/dashboard", true)
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