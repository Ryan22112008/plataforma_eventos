package sistema.eventos.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sistema.eventos.model.Perfil;
import sistema.eventos.model.Usuario;

import java.util.List;
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

     // --- Consulta paginada de usuários para o Super Admin ---
     @Query("""
         SELECT u FROM Usuario u
         WHERE (:nome IS NULL OR :nome = '' OR LOWER(u.nome) LIKE LOWER(CONCAT('%', :nome, '%')))
           AND (:email IS NULL OR :email = '' OR LOWER(u.email) LIKE LOWER(CONCAT('%', :email, '%')))
           AND (:perfil IS NULL OR u.perfil = :perfil)
           AND (:ativo IS NULL OR u.ativo = :ativo)
         ORDER BY u.dataCriacao DESC
         """)
     Page<Usuario> findFiltros(
             @Param("nome") String nome,
             @Param("email") String email,
             @Param("perfil") Perfil perfil,
             @Param("ativo") Boolean ativo,
             Pageable pageable);

     // Consulta sem paginação para exportação (mesmos filtros da listagem)
     @Query("""
         SELECT u FROM Usuario u
         WHERE (:nome IS NULL OR :nome = '' OR LOWER(u.nome) LIKE LOWER(CONCAT('%', :nome, '%')))
           AND (:email IS NULL OR :email = '' OR LOWER(u.email) LIKE LOWER(CONCAT('%', :email, '%')))
           AND (:perfil IS NULL OR u.perfil = :perfil)
           AND (:ativo IS NULL OR u.ativo = :ativo)
         ORDER BY u.dataCriacao DESC
         """)
     List<Usuario> findFiltrosSemPaginacao(
             @Param("nome") String nome,
             @Param("email") String email,
             @Param("perfil") Perfil perfil,
             @Param("ativo") Boolean ativo);

     // Contagens para o resumo da página
     @Query("SELECT COUNT(u) FROM Usuario u WHERE u.perfil = :perfil")
     long countByPerfil(@Param("perfil") Perfil perfil);

     @Query("SELECT COUNT(u) FROM Usuario u WHERE u.ativo = :ativo")
     long countByAtivo(@Param("ativo") boolean ativo);
}
