package sistema.eventos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sistema.eventos.model.Usuario;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

     Optional<Usuario> findByEmail(String email);
    Optional<Usuario> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);

     boolean existsByEmail(String email);

     // --- NOVO: Essencial para a integração com o UserDetailsService do Spring Security ---
     @Query("SELECT u FROM Usuario u WHERE u.email = :email AND u.ativo = true")
     Optional<Usuario> findByEmailEAtivo(@Param("email") String email);
}
