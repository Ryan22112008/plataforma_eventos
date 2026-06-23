package sistema.eventos.controller;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.xhtmlrenderer.pdf.ITextRenderer;
import sistema.eventos.dto.RequestEventoDto;
import sistema.eventos.exception.NegocioException;
import sistema.eventos.model.Evento;
import sistema.eventos.model.Inscricao;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.InscricaoRepository;
import sistema.eventos.service.InscricaoService;
import sistema.eventos.service.EventoService;
import sistema.eventos.service.UsuarioService;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Base64;
import java.util.List;

@Slf4j
@Controller
@RequestMapping("/organizador")
@AllArgsConstructor
public class EventoController {

    private final EventoService eventoService;
    private final UsuarioService usuarioService;
    private final InscricaoService inscricaoService;
    private final InscricaoRepository inscricaoRepository;

    @Autowired
    private SpringTemplateEngine templateEngine;

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
            model.addAttribute("erroNegocio", "Erro interno ao carregar dados do painel: " + e.getMessage());
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

    @GetMapping("/eventos/{id}/trabalhos")
    public String listarTrabalhosParaAvaliar(@PathVariable Long id, Model model) {
        model.addAttribute("evento", eventoService.buscarPorId(id));
        return "trabalhos";
    }

    @PostMapping("/evento/{id}/excluir")
    public String excluirEvento(@PathVariable("id") Long id,
                                java.security.Principal principal,
                                RedirectAttributes redirectAttributes) {
        try {
            eventoService.excluirEvento(id, principal.getName());
            redirectAttributes.addFlashAttribute("sucesso", "O evento foi permanentemente removido.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erroNegocio", e.getMessage());
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
        } catch (Exception e) {
            log.error("Erro ao salvar imagem do certificado para o evento ID: " + id, e);
            redirectAttributes.addFlashAttribute("erroNegocio", e.getMessage());
        }

        return "redirect:/organizador/painel";
    }

    @GetMapping("/eventos/{id}/inscritos")
    public String listarInscritosDoEvento(@PathVariable("id") Long id,
                                          Model model,
                                          RedirectAttributes redirectAttributes) {
        try {
            Evento evento = eventoService.buscarPorId(id);
            List<Inscricao> listaInscritos = inscricaoService.listarPorEvento(id);

            model.addAttribute("evento", evento);
            model.addAttribute("inscritos", listaInscritos);

            // 🎯 ADICIONE ESTA LINHA AQUI PARA ENVIAR O ESTADO PARA O HTML:
            model.addAttribute("paginaAtiva", "inscritos");

            return "inscritos";

        } catch (Exception e) {
            log.error("Erro ao tentar carregar inscritos do evento ID: " + id, e);
            redirectAttributes.addFlashAttribute("erroNegocio", "Não foi possível acessar a lista: " + e.getMessage());
            return "redirect:/organizador/painel";
        }
    }

    @PostMapping("/inscricoes/{inscricaoId}/checkin")
    public String confirmarPresencaDoAluno(@PathVariable("inscricaoId") Long inscricaoId,
                                           @RequestParam("eventoId") Long eventoId,
                                           RedirectAttributes redirectAttributes) {
        try {
            inscricaoService.confirmarPresenca(inscricaoId);
            redirectAttributes.addFlashAttribute("sucesso", "Presença confirmada com sucesso! Certificado liberado.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erroNegocio", e.getMessage());
        }
        return "redirect:/organizador/eventos/" + eventoId + "/inscritos";
    }
    // 🎯 ADICIONE ESTE MÉTODO DENTRO DO SEU EVENTOCONTROLLER
    @GetMapping("/evento/editar/{id}")
    public String exibirFormularioEditar(@PathVariable("id") Long id, Model model) {
        try {
            // Busca o DTO populado com os dados e disciplinas do banco
            RequestEventoDto eventoDto = eventoService.buscarPorIdParaEdicao(id);

            // Injeta o DTO no model para o Thymeleaf preencher os inputs automaticamente
            model.addAttribute("eventoDto", eventoDto);

            // Abre o MESMO html de cadastro, que agora vai alternar para modo edição
            return "criar-evento";

        } catch (RuntimeException e) {
            model.addAttribute("erroNegocio", e.getMessage());
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
            Model model) {

        if (result.hasErrors()) {
            return "criar-evento";
        }

        try {
            // Chama o método sênior do service que limpa os órfãos e checa uploads vazios
            eventoService.atualizarEvento(id, dto, banner, imagemFrente, imagemVerso);
            return "redirect:/organizador/painel?sucesso=true";
        } catch (NegocioException e) {
            model.addAttribute("erroNegocio", e.getMessage());
            return "criar-evento";
        }
    }
    @PostMapping("/eventos/{id}/consolidar-presencas")
    public String consolidarDiarioPresencas(
            @PathVariable("id") Long id,
            @RequestParam(value = "inscricoesPresentes", required = false) List<Long> idsInscricoesPresentes,
            RedirectAttributes redirectAttributes) {
        try {
            // Invoca o método que criamos no InscricaoService para processar o lote
            inscricaoService.fecharDiarioClasse(id, idsInscricoesPresentes);

            redirectAttributes.addFlashAttribute("sucesso", "Diário de classe homologado! Certificados liberados para os alunos presentes.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erroNegocio", e.getMessage());
        }

        // Redireciona de volta para a pauta de chamada atualizada
        return "redirect:/organizador/eventos/" + id + "/inscritos";
    }
    @GetMapping("/certificado-preview/{id}")
    public String preVisualizarTemplateCertificado(@PathVariable("id") Long id, Model model) {
        // 1. Busca os dados originais do evento
        Evento evento = eventoService.buscarPorId(id);
        model.addAttribute("evento", evento);

        // 2. Captura os nomes dos arquivos salvos no banco
        String frenteNome = evento.getCertificadoTemplate();
        String versoNome = evento.getCertificadoTemplateVerso();

        // 3. Converte os caminhos para o formato web público (/imagens-eventos/...)
        String frenteUrl = (frenteNome != null && !frenteNome.startsWith("/")) ? "/imagens-eventos/" + frenteNome : frenteNome;
        String versoUrl = (versoNome != null && !versoNome.startsWith("/")) ? "/imagens-eventos/" + versoNome : versoNome;

        // 4. 🎯 CORREÇÃO CRÍTICA: injeta as variáveis EXATAS que o seu HTML espera
        // Mesmo que o nome termine em "Base64", passar a URL pública funciona perfeitamente no navegador!
        model.addAttribute("imagemFundoBase64", frenteUrl);
        model.addAttribute("imagemVersoBase64", versoUrl);

        // 🎯 MOCK DO QR CODE: Alimenta a variável para o preview não exibir uma imagem quebrada
        model.addAttribute("qrCodeBase64", "https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=EV3-MT-2026-PREVIEW-XYZ999");

        // 5. Mocks de segurança para o restante do documento (Evita os erros de campos nulos)
        java.util.Map<String, String> usuarioFake = new java.util.HashMap<>();
        usuarioFake.put("nome", "NOME DO SERVIDOR (PREVIEW)");
        usuarioFake.put("email", "servidor.preview@seplag.mt.gov.br");
        usuarioFake.put("cpf", "000.000.000-00");
        model.addAttribute("usuario", usuarioFake);

        java.util.Map<String, Object> inscricaoFake = new java.util.HashMap<>();
        inscricaoFake.put("tokenIngresso", "2026-PREVIEW-XYZ999");
        inscricaoFake.put("status", "UTILIZADO");
        model.addAttribute("inscricao", inscricaoFake);

        model.addAttribute("isPreview", true);

        return "certificado";
    }

    @GetMapping("/inscricoes/{inscricaoId}/certificado")
    public void baixarCertificadoComoProfessor(@PathVariable("inscricaoId") Long inscricaoId, HttpServletResponse response) {
        try {
            // 1. Busca os dados reais da inscrição
            Inscricao inscricao = inscricaoService.buscarPorId(inscricaoId);

            if (!inscricao.getStatus().name().equals("UTILIZADO")) {
                throw new NegocioException("Não é possível emitir certificado para participantes ausentes.");
            }

            Evento evento = inscricao.getEvento();

            // 2. Criamos o Context do Thymeleaf
            Context context = new Context();
            context.setVariable("evento", evento);
            context.setVariable("usuario", inscricao.getParticipante());
            context.setVariable("inscricao", inscricao);

            // Mapeamento da pasta física do Windows
            String diretorioUploads = "C:/Users/ryanmendes/Downloads/Eventos/uploads/";
            String frenteNome = evento.getCertificadoTemplate();
            String versoNome = evento.getCertificadoTemplateVerso();

            String frentePath = "file:///" + diretorioUploads + frenteNome;
            String versoPath = "file:///" + diretorioUploads + versoNome;

            context.setVariable("imagemFundoBase64", frentePath);
            context.setVariable("imagemVersoBase64", versoPath);

            // 🎯 AJUSTE DO QR CODE: Gera o QR Code REAL e local via ZXing em Base64
            // Você pode trocar o texto abaixo pela URL real de validação do seu portal se quiser
            String textoValidacao = "EV3-" + inscricao.getTokenIngresso();
            String qrCodeNativoBase64 = gerarQrCodeBase64(textoValidacao);
            context.setVariable("qrCodeBase64", qrCodeNativoBase64);

            context.setVariable("isPreview", false);

            // 3. Renderiza o HTML do Thymeleaf para uma String em memória
            // 3. Renderiza o HTML do Thymeleaf para uma String em memória
            String htmlProcessado = templateEngine.process("certificado", context);

            // 4. 🎯 CORREÇÃO ANTI-CACHE: Adiciona a hora atual em milissegundos no nome do arquivo
            // Isso muda a URL/assinatura do arquivo toda vez, forçando o Chrome a baixar o novo!
            String timestamp = String.valueOf(System.currentTimeMillis());
            String nomeArquivo = "Certificado_" + inscricao.getParticipante().getNome().replace(" ", "_") + "_" + timestamp + ".pdf";

            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + nomeArquivo + "\"");

            // 🎯 DESTRUIÇÃO DE CACHE HTTP: Avisa o navegador para nunca reaproveitar o PDF antigo
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate"); // HTTP 1.1
            response.setHeader("Pragma", "no-cache"); // HTTP 1.0
            response.setDateHeader("Expires", 0); // Proxies

            // 5. Usa o Flying Saucer (ITextRenderer) para transformar a String HTML em PDF real
            OutputStream outputStream = response.getOutputStream();
            ITextRenderer renderer = new ITextRenderer();

            renderer.setDocumentFromString(htmlProcessado);
            renderer.layout();
            renderer.createPDF(outputStream);

            outputStream.flush();
            outputStream.close();

        } catch (Exception e) {
            e.printStackTrace();
            throw new NegocioException("Erro ao gerar o arquivo PDF do certificado: " + e.getMessage());
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
            RedirectAttributes attributes) {
        try {
            // 1. Busca a inscrição para localizar o usuário correto
            Inscricao inscricao = inscricaoService.buscarPorId(inscricaoId);
            Usuario participante = inscricao.getParticipante();

            // 2. Atualiza o nome do participante com o valor corrigido na memória
            participante.setNome(novoNome);

            // 3. Salva a alteração no banco através do método que criamos no seu Service
            usuarioService.salvar(participante);

            attributes.addFlashAttribute("mensagemSucesso", "Nome do participante corrigido com sucesso!");

            // 4. Redireciona de volta para a lista de inscritos do mesmo evento
            return "redirect:/organizador/eventos/" + inscricao.getEvento().getId() + "/inscritos";

        } catch (Exception e) {
            e.printStackTrace();
            attributes.addFlashAttribute("mensagemErro", "Erro ao corrigir o nome: " + e.getMessage());
            return "redirect:/organizador/painel";
        }
    }
    // 🎯 1. ROTA DO PROFESSOR: Exibe a tela com o QR Code gigante para projetar no telão
    @GetMapping("/eventos/{id}/projetar-presenca")
    public String projetarQrCodePresenca(@PathVariable("id") Long eventoId, Model model) {
        try {
            Evento evento = eventoService.buscarPorId(eventoId);

            // 💡 ATENÇÃO: Para testar no celular localmente, substitua 'localhost' pelo IP da sua máquina na rede (ex: 192.168.1.50)
            String urlCheckin = "http://localhost:8080/participante/eventos/" + eventoId + "/checkin";

            // Reaproveita o gerador de QR Code do ZXing que implementamos para o certificado!
            String qrCodeBase64 = gerarQrCodeBase64(urlCheckin);

            model.addAttribute("evento", evento);
            model.addAttribute("qrCodeLink", urlCheckin);
            model.addAttribute("qrCodeBase64", qrCodeBase64);

            return "qr-presenca"; // Nova página que vamos criar
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/organizador/painel";
        }
    }

    // 🎯 2. ROTA DO ALUNO: Executada quando o celular escaneia o telão
// O Spring Security garante que 'Authentication' trará o usuário logado no celular
    @GetMapping("/participante/eventos/{id}/checkin")
    public String realizarCheckinAutonomo(@PathVariable("id") Long eventoId, Authentication authentication, RedirectAttributes attributes) {
        try {
            if (authentication == null) {
                return "redirect:/login";
            }

            // ... dentro da rota do aluno no EventoController ...
            String emailLogado = authentication.getName();
            Usuario aluno = usuarioService.buscarPorEmail(emailLogado);

// 🎯 Chamada perfeita usando o novo método do seu Repository:
            Inscricao inscricao = inscricaoRepository.findByParticipanteIdAndEventoIdComEvento(aluno.getId(), eventoId)
                    .orElseThrow(() -> new NegocioException("Você não possui uma inscrição ativa para este evento."));

// Valida se o diário já foi fechado antes
            if (inscricao.getEvento().isPresencaFinalizada()) {
                attributes.addFlashAttribute("mensagemErro", "A lista de presença deste evento já foi encerrada pelo professor.");
                return "redirect:/dashboard";
            }

// Altera o status para presente e salva
            inscricao.setStatus(sistema.eventos.model.StatusInscricao.UTILIZADO);
            inscricaoRepository.save(inscricao);
// ...

            attributes.addFlashAttribute("mensagemSucesso", "Presença confirmada com sucesso no evento: " + inscricao.getEvento().getTitulo());
            return "redirect:/dashboard";

        } catch (Exception e) {
            attributes.addFlashAttribute("mensagemErro", "Erro ao computar presença: " + e.getMessage());
            return "redirect:/dashboard";
        }
    }
    @PostMapping("/inscricoes/{id}/alternar")
    public String alternarPresenca(@PathVariable Long id, @RequestParam Long eventoId) {
        inscricaoService.alternarPresenca(id);
        return "redirect:/organizador/eventos/" + eventoId + "/inscritos";
    }
}
