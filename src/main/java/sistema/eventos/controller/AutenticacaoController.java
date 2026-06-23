package sistema.eventos.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import sistema.eventos.dto.RequestUsuarioDto;
import sistema.eventos.exception.NegocioException;
import sistema.eventos.model.Perfil;
import sistema.eventos.model.StatusSolicitacao;
import sistema.eventos.repository.SolicitacaoRepository;
import sistema.eventos.repository.UsuarioRepository;
import sistema.eventos.service.UsuarioService;

@Controller
@RequiredArgsConstructor
public class AutenticacaoController {

    private final UsuarioService usuarioService;
    private final SolicitacaoRepository solicitacaoRepository;
    private final UsuarioRepository usuarioRepository;

    @GetMapping("/login")
    public Object telaLogin(@RequestParam(value = "error", required = false) String error,
                            @RequestParam(value = "logout", required = false) String logout,
                            @RequestParam(value = "sucesso", required = false) String sucesso,
                            @RequestParam(value = "aprovar", required = false) Long aprovarId, // 🎯 Captura o ID vindo do console
                            Model model) {

        // 🚀 ALTERAÇÃO: Se houver um ID para aprovar, processa e joga o texto direto na tela
        if (aprovarId != null) {
            try {
                var solicitacao = solicitacaoRepository.findById(aprovarId)
                        .orElseThrow(() -> new RuntimeException("Solicitação não encontrada para o ID: " + aprovarId));

                // Atualiza o workflow da solicitação
                solicitacao.setStatus(sistema.eventos.model.StatusSolicitacao.APROVADO);
                solicitacaoRepository.save(solicitacao);

                // Eleva os privilégios do usuário associado
                var usuario = solicitacao.getUsuario();
                usuario.setPerfil(sistema.eventos.model.Perfil.ROLE_ORGANIZADOR);
                usuarioRepository.save(usuario);

                // Retorna o texto de sucesso puro no navegador. Esquece o HTML, o feedback aparece na hora!
                return org.springframework.http.ResponseEntity.ok()
                        .body("🟢 SUCESSO! O usuário " + usuario.getNome() + " foi promovido a ORGANIZADOR com sucesso.");

            } catch (Exception e) {
                // Se der algum erro (ex: ID inexistente), printa o erro direto na página
                return org.springframework.http.ResponseEntity.badRequest()
                        .body("🔴 ERRO AO APROVAR PERFIL: " + e.getMessage());
            }
        }

        // ===================================================================
        // 🔒 SUAS FUNÇÕES ORIGINAIS PRESERVADAS (100% IGUAIS E INTOCADAS)
        // ===================================================================
        if (error != null) {
            model.addAttribute("erroLogin", "Credenciais inválidas. Verifique seu e-mail e senha.");
        }
        if (logout != null) {
            model.addAttribute("msgLogout", "Você saiu do sistema com sucesso!");
        }
        if (sucesso != null && aprovarId == null) {
            model.addAttribute("msgSucesso", "Cadastro realizado! Faça o login para acessar a plataforma.");
        }

        return "login"; // Abre a sua página HTML padrão da SEPLAG
    }

    @GetMapping("/cadastro")
    public String telaCadastro(Model model) {
        model.addAttribute("usuarioDto", new RequestUsuarioDto());
        return "cadastro";
    }

    @PostMapping("/cadastro")
    public String processarCadastro(@Valid @ModelAttribute("usuarioDto") RequestUsuarioDto dto,
                                    BindingResult result, Model model) {
        if (result.hasErrors()) {
            return "cadastro";
        }

        try {
            usuarioService.cadastrar(dto);
            return "redirect:/login?sucesso=true";

        } catch (NegocioException e) {
            model.addAttribute("erroNegocio", e.getMessage());
            return "cadastro";
        }
    }
}