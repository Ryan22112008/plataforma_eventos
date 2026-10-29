package sistema.eventos.controller;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sistema.eventos.dto.InscritoFrequenciaDto;
import sistema.eventos.dto.RequestEventoDto;
import sistema.eventos.exception.NegocioException;
import sistema.eventos.model.Evento;
import sistema.eventos.model.Inscricao;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.SessaoRepository;
import sistema.eventos.service.InscricaoService;
import sistema.eventos.service.EventoService;
import sistema.eventos.service.UsuarioService;
import sistema.eventos.service.CertificadoService;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/organizador")
@AllArgsConstructor
public class EventoController {

    private final EventoService eventoService;
    private final UsuarioService usuarioService;
    private final InscricaoService inscricaoService;
    private final SessaoRepository sessaoRepository;
    private final CertificadoService certificadoService;

    @GetMapping("/painel")
    public String painelOrganizador(Model model, @AuthenticationPrincipal UserDetails userDetails,
                                    @RequestParam(value = "sucesso", required = false) String sucesso) {
        try {
            if (sucesso != null) {
                model.addAttribute("msgSucesso", "Operação realizada com sucesso!");
            }

            Usuario organizadorReal = usuarioService.buscarPorEmail(userDetails.getUsername());
            var eventosDoOrganizador = eventoService.listarPorOrganizador(organizadorReal.getId());
            model.addAttribute("eventos", eventosDoOrganizador);

            return "painel-organizador";

        } catch (Exception e) {
            log.error("Erro interno ao carregar dados do painel do organizador", e);
            model.addAttribute("erroNegocio", "Não foi possível carregar o painel neste momento.");
            return "painel-organizador";
        }
    }

    @GetMapping("/evento/novo")
    public String telaCriarEvento(Model model) {
        model.addAttribute("eventoDto", new RequestEventoDto());
        return "criar-evento";
    }

    // --- CORRIGIDO SÊNIOR: Mapeamento limpo via ModelAttribute ---
    @PostMapping("/evento/novo")
    public String processarCriacaoEvento(
            @Valid @ModelAttribute("eventoDto") RequestEventoDto dto,
            BindingResult result,
            @RequestParam("banner") MultipartFile banner, // Mantido separado pois o banner não está no DTO
            Model model,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (result.hasErrors()) {
            return "criar-evento";
        }

        try {
            Usuario organizadorReal = usuarioService.buscarPorEmail(userDetails.getUsername());

            // CORREÇÃO: O service agora recebe o pacote completo 'dto' (contendo as imagens e disciplinas) e o banner
            eventoService.criarEvento(dto, organizadorReal, banner);

            return "redirect:/organizador/painel?sucesso=true";
        } catch (NegocioException e) {
            model.addAttribute("erroNegocio", e.getMessage());
            return "criar-evento";
        }
    }

    @PostMapping("/evento/{id}/excluir")
    public String excluirEvento(@PathVariable("id") Long id,
                                java.security.Principal principal,
                                RedirectAttributes redirectAttributes) {
        try {
            eventoService.excluirEvento(id, principal.getName());
            redirectAttributes.addFlashAttribute("sucesso", "O evento foi permanentemente removido.");
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao excluir o evento {}.", id, e);
            redirectAttributes.addFlashAttribute("erroNegocio", "Não foi possível excluir o evento neste momento.");
        }
        return "redirect:/organizador/painel";
    }

    @PostMapping("/evento/{id}/certificado-template")
    public String salvarTemplateCertificado(@PathVariable("id") Long id,
                                            @RequestParam("certificadoImagem") MultipartFile certificadoImagem,
                                            @AuthenticationPrincipal UserDetails userDetails,
                                            RedirectAttributes redirectAttributes) {
        try {
            eventoService.atualizarArteCertificado(id, certificadoImagem, userDetails.getUsername());
            redirectAttributes.addFlashAttribute("sucesso", "Arte do certificado do Canva updated!");
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao salvar imagem do certificado para o evento ID: " + id, e);
            redirectAttributes.addFlashAttribute("erroNegocio", "Não foi possível salvar a arte do certificado.");
        }

        return "redirect:/organizador/painel";
    }

    @GetMapping("/eventos/{id}/inscritos")
    public String listarInscritosDoEvento(@PathVariable("id") Long id,
                                          Model model,
                                          RedirectAttributes redirectAttributes) {
        try {
            Evento evento = exigirAcessoAoEvento(id, org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
            List<InscritoFrequenciaDto> listaInscritos = inscricaoService.listarInscritosComFrequencia(id);

            model.addAttribute("evento", evento);
            model.addAttribute("inscritos", listaInscritos);

            // 🎯 ADICIONE ESTA LINHA AQUI PARA ENVIAR O ESTADO PARA O HTML:
            model.addAttribute("paginaAtiva", "inscritos");

            return "inscritos";

        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao tentar carregar inscritos do evento ID: " + id, e);
            redirectAttributes.addFlashAttribute("erroNegocio", "Não foi possível acessar a lista de inscritos.");
            return "redirect:/organizador/painel";
        }
    }

    /**
     * Valida a participação de um inscrito do evento. A autorização é feita
     * pelo organizador autenticado (ou SUPER_ADMIN) dono do evento e pelo
     * vínculo real entre inscrição, evento e participante no banco.
     * Nenhum percentual ou status é aceito do navegador.
     */
    @PreAuthorize("hasAnyRole('ORGANIZADOR', 'SUPER_ADMIN')")
    @PostMapping("/eventos/{eventoId}/inscricoes/{inscricaoId}/validar-participacao")
    public String validarParticipacaoDoInscrito(@PathVariable("eventoId") Long eventoId,
                                                @PathVariable("inscricaoId") Long inscricaoId,
                                                Authentication authentication,
                                                RedirectAttributes redirectAttributes) {
        try {
            exigirAcessoAoEvento(eventoId, authentication);
            inscricaoService.validarParticipacaoDoParticipante(eventoId, inscricaoId);
            redirectAttributes.addFlashAttribute("sucesso",
                    "Participação validada! O certificado do participante foi liberado.");
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (NegocioException e) {
            redirectAttributes.addFlashAttribute("erroNegocio", e.getMessage());
        } catch (Exception e) {
            log.error("Erro ao validar a participação da inscrição {} no evento {}.",
                    inscricaoId, eventoId, e);
            redirectAttributes.addFlashAttribute("erroNegocio",
                    "Não foi possível validar a participação neste momento.");
        }
        return "redirect:/organizador/eventos/" + eventoId + "/inscritos";
    }

    /**
     * Valida em lote apenas as inscrições selecionadas na tela de inscritos.
     * O vínculo com o evento é conferido no service para cada ID recebido.
     */
    @PreAuthorize("hasAnyRole('ORGANIZADOR', 'SUPER_ADMIN')")
    @PostMapping("/eventos/{eventoId}/inscricoes/validar-participacao-lote")
    public String validarParticipacaoEmLote(@PathVariable("eventoId") Long eventoId,
                                            @RequestParam(value = "inscricoesSelecionadas", required = false)
                                            List<Long> inscricoesSelecionadas,
                                            Authentication authentication,
                                            RedirectAttributes redirectAttributes) {
        try {
            exigirAcessoAoEvento(eventoId, authentication);
            int validadas = inscricaoService.validarParticipacaoEmLote(eventoId, inscricoesSelecionadas);
            if (validadas > 0) {
                redirectAttributes.addFlashAttribute("sucesso",
                        "Participação validada em lote! Os certificados dos participantes selecionados foram liberados.");
            } else {
                redirectAttributes.addFlashAttribute("sucesso",
                        "As participações selecionadas já estavam validadas.");
            }
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (NegocioException e) {
            redirectAttributes.addFlashAttribute("erroNegocio", e.getMessage());
        } catch (Exception e) {
            log.error("Erro ao validar em lote as inscrições do evento {}.", eventoId, e);
            redirectAttributes.addFlashAttribute("erroNegocio",
                    "Não foi possível validar as participações selecionadas neste momento.");
        }
        return "redirect:/organizador/eventos/" + eventoId + "/inscritos";
    }

    @PreAuthorize("hasAnyRole('ORGANIZADOR', 'SUPER_ADMIN')")
    @PostMapping("/eventos/{eventoId}/inscricoes/validar-participacoes")
    public String validarParticipacoesEmLote(@PathVariable Long eventoId,
                                             @RequestParam(value = "inscricaoIds", required = false) List<Long> inscricaoIds,
                                             Authentication authentication,
                                             RedirectAttributes redirectAttributes) {
        exigirAcessoAoEvento(eventoId, authentication);
        if (inscricaoIds == null || inscricaoIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("erroNegocio", "Selecione ao menos um participante para validar.");
        } else {
            int validadas = inscricaoService.validarParticipacoesEmLote(eventoId, inscricaoIds);
            redirectAttributes.addFlashAttribute("sucesso", validadas + " participante(s) validado(s). Certificados liberados.");
        }
        return "redirect:/organizador/eventos/" + eventoId + "/inscritos";
    }

    @PostMapping("/inscricoes/{inscricaoId}/checkin")
    public String confirmarPresencaDoAluno(@PathVariable("inscricaoId") Long inscricaoId,
                                           @RequestParam("eventoId") Long eventoId,
                                           RedirectAttributes redirectAttributes,
                                           Authentication authentication) {
        try {
            exigirAcessoAoEvento(eventoId, authentication);
            Inscricao inscricao = inscricaoService.buscarPorId(inscricaoId);
            if (!inscricao.getEvento().getId().equals(eventoId)) {
                throw new org.springframework.security.access.AccessDeniedException("Inscrição não pertence a este evento.");
            }
            inscricaoService.confirmarPresenca(inscricaoId);
            redirectAttributes.addFlashAttribute("sucesso", "Presença confirmada com sucesso! Certificado liberado.");
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao confirmar presença da inscrição {} no evento {}.", inscricaoId, eventoId, e);
            redirectAttributes.addFlashAttribute("erroNegocio", "Não foi possível confirmar a presença.");
        }
        return "redirect:/organizador/eventos/" + eventoId + "/inscritos";
    }
    // 🎯 ADICIONE ESTE MÉTODO DENTRO DO SEU EVENTOCONTROLLER
    @GetMapping("/evento/editar/{id}")
    public String exibirFormularioEditar(@PathVariable("id") Long id, Model model, Authentication authentication) {
        try {
            exigirAcessoAoEvento(id, authentication);
            // Busca o DTO populado com os dados e disciplinas do banco
            RequestEventoDto eventoDto = eventoService.buscarPorIdParaEdicao(id);

            // Injeta o DTO no model para o Thymeleaf preencher os inputs automaticamente
            model.addAttribute("eventoDto", eventoDto);

            // Abre o MESMO html de cadastro, que agora vai alternar para modo edição
            return "criar-evento";

        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Erro ao carregar o formulário de edição do evento {}.", id, e);
            model.addAttribute("erroNegocio", "Não foi possível carregar os dados do evento.");
            return "redirect:/organizador/painel";
        }
    }
    @PostMapping("/evento/editar/{id}")
    public String processarEdicaoEvento(
            @PathVariable("id") Long id,
            @Valid @ModelAttribute("eventoDto") RequestEventoDto dto,
            BindingResult result,
            @RequestParam(value = "banner", required = false) MultipartFile banner,
            @RequestParam(value = "imagemFrente", required = false) MultipartFile imagemFrente,
            @RequestParam(value = "imagemVerso", required = false) MultipartFile imagemVerso,
            Model model,
            Authentication authentication) {

        exigirAcessoAoEvento(id, authentication);
        // A URL identifica a edição; preserva o ID se o formulário precisar ser exibido novamente.
        dto.setId(id);

        if (result.hasErrors()) {
            preservarArtesAtuais(dto, id);
            model.addAttribute("erroNegocio", result.getFieldErrors().stream()
                    .map(error -> error.getDefaultMessage())
                    .collect(Collectors.joining(" ")));
            return "criar-evento";
        }

        try {
            // Chama o método sênior do service que limpa os órfãos e checa uploads vazios
            eventoService.atualizarEvento(id, dto, banner, imagemFrente, imagemVerso);
            return "redirect:/organizador/painel?sucesso=true";
        } catch (NegocioException e) {
            preservarArtesAtuais(dto, id);
            model.addAttribute("erroNegocio", e.getMessage());
            return "criar-evento";
        }
    }

    private void preservarArtesAtuais(RequestEventoDto dto, Long id) {
        RequestEventoDto atual = eventoService.buscarPorIdParaEdicao(id);
        dto.setBannerAtual(atual.getBannerAtual());
        dto.setImagemFrenteAtual(atual.getImagemFrenteAtual());
        dto.setImagemVersoAtual(atual.getImagemVersoAtual());
    }

    @PostMapping("/eventos/{id}/consolidar-presencas")
    public String consolidarDiarioPresencas(
            @PathVariable("id") Long id,
            @RequestParam(value = "inscricoesPresentes", required = false) List<Long> idsInscricoesPresentes,
            RedirectAttributes redirectAttributes,
            Authentication authentication) {
        try {
            exigirAcessoAoEvento(id, authentication);
            // Invoca o método que criamos no InscricaoService para processar o lote
            inscricaoService.fecharDiarioClasse(id, idsInscricoesPresentes);

            redirectAttributes.addFlashAttribute("sucesso", "Diário de classe homologado! Certificados liberados para os alunos presentes.");
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao consolidar presenças do evento {}.", id, e);
            redirectAttributes.addFlashAttribute("erroNegocio", "Não foi possível consolidar as presenças.");
        }

        // Redireciona de volta para a pauta de chamada atualizada
        return "redirect:/organizador/eventos/" + id + "/inscritos";
    }
    @GetMapping("/certificado-preview/{id}")
    public String preVisualizarTemplateCertificado(@PathVariable("id") Long id, Model model, Authentication authentication) throws Exception {
        // 1. Busca os dados originais do evento
        Evento evento = exigirAcessoAoEvento(id, authentication);
        model.addAttribute("evento", evento);

        // 2. Captura os nomes dos arquivos salvos no banco
        String frenteNome = evento.getCertificadoTemplate();
        String versoNome = evento.getCertificadoTemplateVerso();

        // 3. Converte os caminhos para o formato web público (/imagens-eventos/...)
        String frenteUrl = urlPublicaImagem(frenteNome);
        String versoUrl = urlPublicaImagem(versoNome);

        // 4. 🎯 CORREÇÃO CRÍTICA: injeta as variáveis EXATAS que o seu HTML espera
        // Mesmo que o nome termine em "Base64", passar a URL pública funciona perfeitamente no navegador!
        model.addAttribute("imagemFundoBase64", frenteUrl);
        model.addAttribute("imagemVersoBase64", versoUrl);

        String codigoPreview = "EV3-PREVIEW-ONLY";
        String urlPreview = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/certificados/validar").queryParam("codigo", codigoPreview).build().encode().toUriString();
        model.addAttribute("qrCodeBase64", gerarQrCodeBase64(urlPreview));

        // 5. Mocks de segurança para o restante do documento (Evita os erros de campos nulos)
        java.util.Map<String, String> usuarioFake = new java.util.HashMap<>();
        usuarioFake.put("nome", "Nome do participante");
        model.addAttribute("usuario", usuarioFake);

        java.util.Map<String, Object> inscricaoFake = new java.util.HashMap<>();
        inscricaoFake.put("tokenIngresso", "PREVIEW-ONLY");
        inscricaoFake.put("status", "UTILIZADO");
        model.addAttribute("inscricao", inscricaoFake);

        model.addAttribute("isPreview", true);

        return "certificado";
    }

    private String urlPublicaImagem(String reference) {
        if (reference == null || reference.isBlank()) return reference;
        if (reference.startsWith("https://res.cloudinary.com/")) return reference;
        return reference.startsWith("/") ? reference : "/imagens-eventos/" + reference;
    }

    @GetMapping("/inscricoes/{inscricaoId}/certificado")
    public void baixarCertificadoComoProfessor(@PathVariable("inscricaoId") Long inscricaoId,
                                               Authentication authentication,
                                               HttpServletResponse response) {
        try {
            Inscricao inscricao = inscricaoService.buscarPorId(inscricaoId);
            exigirAcessoAoEvento(inscricao.getEvento().getId(), authentication);
            byte[] pdfBytes = certificadoService.gerarCertificadoPdf(inscricao);
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment; filename=\"certificado-evento-" + inscricaoId + ".pdf\"");
            response.setHeader("Cache-Control", "private, no-store, max-age=0");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setContentLength(pdfBytes.length);
            response.getOutputStream().write(pdfBytes);
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Falha ao gerar certificado da inscrição {}", inscricaoId, e);
            throw new NegocioException("Não foi possível gerar o certificado no momento.");
        }
    }

    /**
     * 🎯 MÉTODO AUXILIAR SÊNIOR: Gera o QR Code verdadeiro localmente usando ZXing
     */
    private String gerarQrCodeBase64(String texto) throws Exception {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        // Cria a matriz de pixels do QR Code (200x200 px)
        BitMatrix bitMatrix = qrCodeWriter.encode(texto, BarcodeFormat.QR_CODE, 200, 200);

        // Converte a matriz de pixels em uma imagem PNG em memória
        ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
        byte[] pngData = pngOutputStream.toByteArray();

        // Transforma os bytes do PNG em uma String Base64 compatível com a tag <img> do HTML
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(pngData);
    }
    @PostMapping("/inscricoes/{id}/editar-nome")
    public String editarNomeParticipante(
            @PathVariable("id") Long inscricaoId,
            @RequestParam("novoNome") String novoNome,
            RedirectAttributes attributes,
            Authentication authentication) {
        try {
            // 1. Busca a inscrição para localizar o usuário correto
            Inscricao inscricao = inscricaoService.buscarPorId(inscricaoId);
            exigirAcessoAoEvento(inscricao.getEvento().getId(), authentication);
            if (novoNome == null || novoNome.isBlank() || novoNome.trim().length() > 100) {
                throw new NegocioException("Informe um nome válido com até 100 caracteres.");
            }
            Usuario participante = inscricao.getParticipante();

            // 2. Atualiza o nome do participante com o valor corrigido na memória
            participante.setNome(novoNome);

            // 3. Salva a alteração no banco através do método que criamos no seu Service
            usuarioService.salvar(participante);

            attributes.addFlashAttribute("mensagemSucesso", "Nome do participante corrigido com sucesso!");

            // 4. Redireciona de volta para a lista de inscritos do mesmo evento
            return "redirect:/organizador/eventos/" + inscricao.getEvento().getId() + "/inscritos";

        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Falha ao atualizar nome na inscrição {}", inscricaoId, e);
            attributes.addFlashAttribute("mensagemErro", "Não foi possível corrigir o nome do participante.");
            return "redirect:/organizador/painel";
        }
    }
    // 🎯 1. ROTA DO PROFESSOR: Exibe a tela com o QR Code gigante para projetar no telão
    @GetMapping("/eventos/{id}/projetar-presenca")
    public String projetarQrCodePresenca(@PathVariable("id") Long eventoId,
                                         Model model,
                                         @AuthenticationPrincipal UserDetails userDetails,
                                         jakarta.servlet.http.HttpServletResponse response) {
        try {
            Evento evento = eventoService.buscarPorId(eventoId);
            exigirAcessoAoEvento(eventoId, org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            response.setHeader("Referrer-Policy", "no-referrer");
            var codigosSessoes = new java.util.ArrayList<java.util.Map<String, Object>>();
            for (var sessao : sessaoRepository.findByEventoIdOrderByDataHoraInicioAsc(eventoId)) {
                String urlCheckin = ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/participante/presencas/checkin")
                        .queryParam("token", sessao.getQrCodeToken())
                        .build().toUriString();
                codigosSessoes.add(java.util.Map.of(
                        "sessao", sessao,
                        "url", urlCheckin,
                        "qrCodeBase64", gerarQrCodeBase64(urlCheckin)));
            }
            model.addAttribute("evento", evento);
            model.addAttribute("codigosSessoes", codigosSessoes);

            return "qr-presenca";
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Falha ao projetar QR codes de presença para o evento {}", eventoId, e);
            return "redirect:/organizador/painel";
        }
    }
    @PostMapping("/inscricoes/{id}/alternar")
    public String alternarPresenca(@PathVariable Long id, @RequestParam Long eventoId, Authentication authentication) {
        exigirAcessoAoEvento(eventoId, authentication);
        Inscricao inscricao = inscricaoService.buscarPorId(id);
        if (!inscricao.getEvento().getId().equals(eventoId)) {
            throw new org.springframework.security.access.AccessDeniedException("Inscrição não pertence a este evento.");
        }
        inscricaoService.alternarPresenca(id);
        return "redirect:/organizador/eventos/" + eventoId + "/inscritos";
    }

    private Evento exigirAcessoAoEvento(Long eventoId, Authentication authentication) {
        Evento evento = eventoService.buscarPorId(eventoId);
        boolean admin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_SUPER_ADMIN".equals(authority.getAuthority()));
        if (admin) return evento;
        if (authentication == null || evento.getOrganizador() == null
                || !evento.getOrganizador().getEmail().equalsIgnoreCase(authentication.getName())) {
            throw new org.springframework.security.access.AccessDeniedException("Você não tem acesso a este evento.");
        }
        return evento;
    }
}
