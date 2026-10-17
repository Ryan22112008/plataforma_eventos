package sistema.eventos.config;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import java.util.Locale;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.UsuarioRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public CustomUserDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas."));

        return User.builder()
                .username(usuario.getEmail())
                .password(usuario.getSenha())
                .roles(usuario.getPerfil().name().replace("ROLE_", ""))
                // --- ADIÇÃO SÊNIOR: Vincula o estado da conta ao Spring Security ---
                .disabled(!usuario.isAtivo()) // Se ativo for false, disabled vira true e o Spring bloqueia o login
                .accountExpired(false)
                .credentialsExpired(false)
                .accountLocked(false)
                .build();
    }
}
