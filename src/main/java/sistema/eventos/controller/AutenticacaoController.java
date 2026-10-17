package sistema.eventos.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import sistema.eventos.dto.RequestUsuarioDto;
import sistema.eventos.exception.NegocioException;
import sistema.eventos.service.UsuarioService;

@Controller
@RequiredArgsConstructor
public class AutenticacaoController {

    private final UsuarioService usuarioService;

    @GetMapping("/login")
    public String telaLogin(@org.springframework.web.bind.annotation.RequestParam(value = "error", required = false) String error,
                            @org.springframework.web.bind.annotation.RequestParam(value = "logout", required = false) String logout,
                            @org.springframework.web.bind.annotation.RequestParam(value = "sucesso", required = false) String sucesso,
                            Model model) {
        if (error != null) {
            model.addAttribute("erroLogin", "Credenciais inválidas. Verifique seu e-mail e senha.");
        }
        if (logout != null) {
            model.addAttribute("msgLogout", "Você saiu do sistema com sucesso!");
        }
        if (sucesso != null) {
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
