package sistema.eventos.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import sistema.eventos.model.StatusPresenca;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.EventoRepository;
import sistema.eventos.repository.InscricaoRepository;
import sistema.eventos.repository.PresencaRepository;
import sistema.eventos.repository.SessaoRepository;
import sistema.eventos.service.PresencaService;

import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/presencas")
@RequiredArgsConstructor
public class PresencaController {

    private final PresencaService presencaService;
    private final EventoRepository eventoRepository;
    private final InscricaoRepository inscricaoRepository;
    private final SessaoRepository sessaoRepository;
    private final PresencaRepository presencaRepository;

     @PostMapping("/checkin")
    public ResponseEntity<String> registrarCheckin(@RequestParam String token,
                                                   @AuthenticationPrincipal Usuario usuario) {
        try {
            presencaService.registrarPresenca(usuario.getId(), token);
            return ResponseEntity.ok("Presença registrada com sucesso!");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
    }

    // 2. Edição manual pelo Professor (Endpoint AJAX)
    @PutMapping("/{presencaId}/status")
    public ResponseEntity<Void> atualizarStatus(@PathVariable Long presencaId,
                                                @RequestParam StatusPresenca novoStatus,
                                                @AuthenticationPrincipal Usuario professor) {
        presencaService.atualizarStatusPresenca(presencaId, novoStatus, professor.getNome());
        return ResponseEntity.noContent().build();
    }

    // 3. Visualização da grade para o professor
    @GetMapping("/evento/{eventoId}/grade")
    public String exibirGradePresenca(@PathVariable Long eventoId, Model model) {
        // Aqui você buscaria os dados para o Thymeleaf
        // model.addAttribute("grade", presencaService.buscarGradeCompleta(eventoId));
        return "eventos/grade-presenca";
    }
    @PostMapping("/alterar-status")
    public ResponseEntity<Void> alterarStatusManual(@RequestParam Long alunoId,
                                                    @RequestParam Long sessaoId,
                                                    @RequestParam StatusPresenca novoStatus,
                                                    @AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails userDetails) {

        // 🎯 Proteção Sênior: Se não tiver usuário logado, barra a requisição com 401 Unauthorized
        if (userDetails == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
        }

        // Captura o identificador (e-mail ou username) de quem está fazendo a alteração
        String nomeProfessor = userDetails.getUsername();

        // Invoca o seu service passando a String com o nome/e-mail identificador
        presencaService.atualizarStatusPresencaManual(alunoId, sessaoId, novoStatus, nomeProfessor);

        return ResponseEntity.ok().build();
    }
    @GetMapping("/{eventoId}/frequencia")
    public String exibirGradeFrequencia(@PathVariable Long eventoId, Model model) {
        var evento = eventoRepository.findById(eventoId)
                .orElseThrow(() -> new IllegalArgumentException("Evento não encontrado"));
        var inscritos = inscricaoRepository.findByEventoId(eventoId);
        var sessoes = sessaoRepository.findByEventoIdOrderByDataHoraInicioAsc(eventoId);

        var todasPresencas = presencaRepository.findByEventoId(eventoId);
        Map<String, String> mapaPresencas = todasPresencas.stream()
                .collect(Collectors.toMap(
                        p -> p.getAluno().getId() + "_" + p.getSessao().getId(),
                        p -> p.getStatus().name()
                ));

        long totalPossivel = (long) inscritos.size() * sessoes.size();
        long totalPresentes = todasPresencas.stream()
                .filter(p -> p.getStatus() == StatusPresenca.PRESENTE)
                .count();

        double percentualGlobal = (totalPossivel > 0) ? ((double) totalPresentes / totalPossivel) * 100 : 0;

        model.addAttribute("paginaAtiva", "frequencia");

        model.addAttribute("evento", evento);
        model.addAttribute("inscritos", inscritos);
        model.addAttribute("sessoes", sessoes);
        model.addAttribute("mapaPresencas", mapaPresencas);
        model.addAttribute("percentualGlobal", String.format("%.0f", percentualGlobal));

        return "frequencia-aluno";
    }
}