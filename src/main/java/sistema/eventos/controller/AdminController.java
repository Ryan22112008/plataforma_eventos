package sistema.eventos.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import sistema.eventos.model.StatusSolicitacao;
import sistema.eventos.model.SolicitacaoOrganizador;
import sistema.eventos.model.Perfil;
import sistema.eventos.repository.SolicitacaoRepository;
import sistema.eventos.repository.UsuarioRepository;

@RestController
@RequestMapping("/admin/solicitacoes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") // Apenas o administrador do sistema acessa essa rota
public class AdminController {

    private final SolicitacaoRepository solicitacaoRepository;
    private final UsuarioRepository usuarioRepository;

    @PostMapping("/{id}/aprovar")
    public ResponseEntity<String> aprovarProfessor(@PathVariable Long id) {
        SolicitacaoOrganizador solicitacao = solicitacaoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Solicitação não encontrada"));

        if (solicitacao.getStatus() != StatusSolicitacao.PENDENTE) {
            return ResponseEntity.badRequest().body("Esta solicitação já foi processada.");
        }

        // 1. Atualiza o status da solicitação
        solicitacao.setStatus(StatusSolicitacao.APROVADO);
        solicitacaoRepository.save(solicitacao);

        // 2. 🌟 PROMOVE O USUÁRIO A ORGANIZADOR
        var usuario = solicitacao.getUsuario();
        usuario.setPerfil(Perfil.ROLE_ORGANIZADOR);
        usuarioRepository.save(usuario);

        // (Opcional) Você poderia disparar um e-mail aqui pro aluno avisando: "Parabéns, agora você é um organizador!"

        return ResponseEntity.ok("Usuário promovido a Organizador com sucesso!");
    }
}