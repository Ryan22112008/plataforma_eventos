package sistema.eventos.controller;

import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import sistema.eventos.model.StatusInscricao;
import sistema.eventos.repository.InscricaoRepository;

@Controller
@RequiredArgsConstructor
public class CertificadoValidacaoController {

    private static final String PREFIXO_CODIGO = "EV3-";
    private final InscricaoRepository inscricaoRepository;

    @GetMapping("/certificados/validar")
    @Transactional(readOnly = true)
    public String validar(@RequestParam(name = "codigo", defaultValue = "") String codigo,
                          Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, max-age=0");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Robots-Tag", "noindex, nofollow");
        String codigoNormalizado = codigo == null ? "" : codigo.trim();
        boolean codigoInformado = !codigoNormalizado.isEmpty();
        model.addAttribute("codigo", codigoNormalizado);
        model.addAttribute("codigoInformado", codigoInformado);

        if (!codigoInformado) {
            model.addAttribute("valido", false);
            return "validar-certificado";
        }

        if (codigoNormalizado.length() > 140 || !codigoNormalizado.startsWith(PREFIXO_CODIGO)) {
            model.addAttribute("valido", false);
            return "validar-certificado";
        }

        String token = codigoNormalizado.substring(PREFIXO_CODIGO.length());
        if (!token.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            model.addAttribute("valido", false);
            return "validar-certificado";
        }
        var inscricao = inscricaoRepository.findByTokenIngressoComRelacoes(token);
        boolean valido = inscricao.isPresent() && inscricao.get().getStatus() == StatusInscricao.UTILIZADO;

        model.addAttribute("valido", valido);
        if (valido) {
            var registro = inscricao.orElseThrow();
            model.addAttribute("participante", registro.getParticipante().getNome());
            model.addAttribute("evento", registro.getEvento().getTitulo());
            model.addAttribute("cargaHoraria", registro.getEvento().getCargaHoraria());
            model.addAttribute("dataCheckIn", registro.getDataCheckIn());
        }
        return "validar-certificado";
    }
}
