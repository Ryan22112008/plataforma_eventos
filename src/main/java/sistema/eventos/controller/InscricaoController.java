package sistema.eventos.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sistema.eventos.exception.NegocioException;
import sistema.eventos.model.Evento;
import sistema.eventos.model.Inscricao;
import sistema.eventos.model.Usuario;
import sistema.eventos.service.EventoService;
import sistema.eventos.service.InscricaoService;
import sistema.eventos.service.UsuarioService;
import sistema.eventos.service.CertificadoService; // ADICIONADO

import java.io.OutputStream;
import java.util.List;

@AllArgsConstructor
@Controller
public class InscricaoController {

    private final InscricaoService inscricaoService;
    private final UsuarioService usuarioService;
    private final EventoService eventoService;
    private final CertificadoService certificadoService; // ADICIONADO SÊNIOR: Injetado via Lombok

    // --- 1. VISÃO DO ORGANIZADOR: Listar Inscritos ---
   // @GetMapping("/organizador/eventos/{eventoId}/inscritos")
   // @Transactional(readOnly = true)
   // public String verInscritos(@PathVariable("eventoId") Long eventoId, Model model) {
     //   Evento evento = eventoService.buscarPorId(eventoId);
       // List<Inscricao> inscritos = inscricaoService.listarInscritosDoEvento(eventoId);

      //  model.addAttribute("evento", evento);
       // model.addAttribute("inscritos", inscritos);
       // model.addAttribute("eventoId", eventoId);

       // return "inscritos";
   // }

    // --- 2. VISÃO DO PARTICIPANTE: Inscrever no Evento ---
    @PostMapping("/eventos/{id}/inscrever")
    public String inscreverNoEvento(@PathVariable("id") Long id,
                                    @AuthenticationPrincipal UserDetails userDetails,
                                    RedirectAttributes redirectAttributes) {
        try {
            Usuario participante = usuarioService.buscarPorEmail(userDetails.getUsername());
            inscricaoService.inscreverUsuario(id, participante);

            redirectAttributes.addFlashAttribute("sucesso", "Sua inscrição foi realizada com sucesso!");
            return "redirect:/eventos/" + id;

        } catch (NegocioException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            return "redirect:/eventos/" + id;
        }
    }

    // --- 3. FLUXO DA PORTARIA: Realizar Check-in via Token/QR Code ---
    @PostMapping("/organizador/checkin")
    public String realizarCheckin(@RequestParam("tokenIngresso") String tokenIngresso,
                                  RedirectAttributes redirectAttributes) {
        try {
            inscricaoService.realizarCheckIn(tokenIngresso);
            redirectAttributes.addFlashAttribute("sucesso", "Check-in realizado com sucesso!");

            return "redirect:/dashboard";
        } catch (NegocioException e) {
            redirectAttributes.addFlashAttribute("erroNegocio", e.getMessage());
            return "redirect:/dashboard";
        }
    }

    // --- 4. FLUXO DE CERTIFICADOS: Download do PDF Gerado em Tempo Real ---
    @GetMapping("/participante/inscricao/{inscricaoId}/certificado")
    public void baixarCertificado(@PathVariable("inscricaoId") Long inscricaoId,
                                  @AuthenticationPrincipal UserDetails userDetails, // ADICIONADO PARA SEGURANÇA
                                  HttpServletResponse response) {
        try {
            // 1. Recupera a inscrição do banco
            Inscricao inscricao = inscricaoService.buscarPorId(inscricaoId);

            // --- VALIDAÇÃO SÊNIOR: O usuário logado é realmente o dono desta inscrição? ---
            if (!inscricao.getParticipante().getEmail().equals(userDetails.getUsername())) {
                throw new org.springframework.security.access.AccessDeniedException("Acesso negado: Este certificado não pertence a você.");
            }

            // 2. Dispara a geração se passar na validação
            byte[] pdfBytes = certificadoService.gerarCertificadoPdf(inscricao);

            // 3. Define os metadados do cabeçalho HTTP
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment; filename=certificado-evento-" + inscricaoId + ".pdf");
            response.setContentLength(pdfBytes.length);

            // 4. Grava o payload binário
            try (OutputStream os = response.getOutputStream()) {
                os.write(pdfBytes);
                os.flush();
            }
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e; // O Spring Security intercepta e gera um erro 403 Forbidden automático
        } catch (Exception e) {
            throw new NegocioException("Não foi possível gerar o arquivo PDF do certificado devido a uma falha interna.");
        }
    }
}