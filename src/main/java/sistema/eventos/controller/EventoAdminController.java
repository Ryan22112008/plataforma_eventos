package sistema.eventos.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import sistema.eventos.dto.EventoConfigDTO;
import sistema.eventos.exception.NegocioException;
import sistema.eventos.model.Disciplina;
import sistema.eventos.model.Evento;
import sistema.eventos.repository.EventoRepository;
import sistema.eventos.service.FileStorageService;

@Slf4j
@RestController
@RequestMapping("/api/admin/eventos")
@RequiredArgsConstructor
public class EventoAdminController {

    private final EventoRepository eventoRepository;
    private final FileStorageService fileStorageService;

    @PostMapping(value = "/{eventoId}/configurar-certificado", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<String> salvarConfiguracaoCertificado(
            @PathVariable Long eventoId,
            @Valid @RequestPart("dados") EventoConfigDTO dadosJson,
            @RequestPart("imagemFrente") MultipartFile imagemFrente,
            @RequestPart("imagemVerso") MultipartFile imagemVerso) {

        Evento evento = eventoRepository.findById(eventoId)
                .orElseThrow(() -> new NegocioException("Evento não encontrado."));
        String frenteAntiga = evento.getCertificadoTemplate();
        String versoAntigo = evento.getCertificadoTemplateVerso();
        String novaFrente = null;
        String novoVerso = null;

        try {
            novaFrente = fileStorageService.storeImage(imagemFrente, "certificado-frente");
            novoVerso = fileStorageService.storeImage(imagemVerso, "certificado-verso");
            evento.setCertificadoTemplate(novaFrente);
            evento.setCertificadoTemplateVerso(novoVerso);
            evento.setTitulo(dadosJson.getTituloEvento().trim());
            evento.setCargaHoraria(dadosJson.getCargaHorariaTotal());

            evento.getDisciplinas().clear();
            if (dadosJson.getDisciplinas() != null) {
                dadosJson.getDisciplinas().stream().filter(dto -> dto != null && dto.getNome() != null && !dto.getNome().isBlank())
                        .forEach(dto -> {
                            Disciplina disciplina = new Disciplina();
                            disciplina.setNome(dto.getNome().trim());
                            disciplina.setCargaHoraria(dto.getCargaHoraria());
                            disciplina.setEvento(evento);
                            evento.getDisciplinas().add(disciplina);
                        });
            }
            eventoRepository.save(evento);

            removeOldImage(frenteAntiga, novaFrente);
            removeOldImage(versoAntigo, novoVerso);
            return ResponseEntity.ok("Configuração do certificado salva.");
        } catch (NegocioException e) {
            removeOldImage(novaFrente, null);
            removeOldImage(novoVerso, null);
            throw e;
        } catch (Exception e) {
            removeOldImage(novaFrente, null);
            removeOldImage(novoVerso, null);
            log.error("Falha ao atualizar configuração de certificado para evento {}", eventoId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Não foi possível salvar a configuração do certificado.");
        }
    }

    private void removeOldImage(String oldName, String currentName) {
        if (oldName == null || oldName.isBlank() || oldName.equals(currentName)) return;
        try {
            fileStorageService.deleteStoredImage(oldName);
        } catch (Exception e) {
            log.warn("Não foi possível remover um arquivo antigo de certificado.", e);
        }
    }
}
