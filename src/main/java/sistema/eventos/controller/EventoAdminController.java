package sistema.eventos.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import sistema.eventos.dto.EventoConfigDTO;

import sistema.eventos.model.Disciplina;
import sistema.eventos.model.Evento;
import sistema.eventos.repository.EventoRepository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/eventos")
public class EventoAdminController {

    private final EventoRepository eventoRepository;

    // Centralizado com o caminho absoluto/relativo mapeado no CertificadoService
    private final String DIRETORIO_UPLOADS = "uploads/";

    public EventoAdminController(EventoRepository eventoRepository) {
        this.eventoRepository = eventoRepository;

        // Boilerplate de Infraestrutura: Inicializa a pasta caso ela não exista no boot do server
        File diretorio = new File(DIRETORIO_UPLOADS);
        if (!diretorio.exists()) {
            diretorio.mkdirs();
        }
    }

    @PostMapping(value = "/{eventoId}/configurar-certificado",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional // Garante rollback automático se o banco falhar ou o upload cair no meio
    public ResponseEntity<String> salvarConfiguracaoCertificado(
            @PathVariable Long eventoId,
            @RequestPart("dados") EventoConfigDTO dadosJson,
            @RequestPart("imagemFrente") MultipartFile imagemFrente,
            @RequestPart("imagemVerso") MultipartFile imagemVerso) {

        // 1. Fail-fast principal: Valida se o evento de fato existe
        Evento evento = eventoRepository.findById(eventoId)
                .orElseThrow(() -> new RuntimeException("Evento não encontrado com o ID: " + eventoId));

        try {
            // 2. Persiste as imagens físicas no File System com nomes imutáveis
            String nomeUnicoFrente = gravarArquivoNoDisco(imagemFrente);
            String nomeUnicoVerso = gravarArquivoNoDisco(imagemVerso);

            // 3. Atualiza os ponteiros de String do Canva no modelo de domínio
            evento.setCertificadoTemplate(nomeUnicoFrente);       // Arte da Frente
            evento.setCertificadoTemplate(nomeUnicoVerso);   // Arte do Verso

            // 4. Sincroniza metadados do curso
            evento.setTitulo(dadosJson.getTituloEvento());
            evento.setCargaHoraria(dadosJson.getCargaHorariaTotal());

            // 5. Abordagem Sênior: Limpa o histórico antigo mapeado (evita duplicidade)
            evento.getDisciplinas().clear();

            // 6. Converte os DTOs recebidos para entidades de negócio populando a FK
            if (dadosJson.getDisciplinas() != null) {
                dadosJson.getDisciplinas().forEach(dto -> {
                    Disciplina disciplina = new Disciplina();
                    disciplina.setNome(dto.getNome());
                    disciplina.setCargaHoraria(dto.getCargaHoraria());
                    disciplina.setEvento(evento); // Amarra o relacionamento bidirecional obrigatório

                    evento.getDisciplinas().add(disciplina);
                });
            }

            // 7. Consolida o estado completo na base de dados
            eventoRepository.save(evento);

            return ResponseEntity.ok("Mapeamento do curso, imagens do Canva e histórico persistidos com sucesso!");

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Falha crítica de I/O ao gravar os arquivos de mídia: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro na consistência transacional da API: " + e.getMessage());
        }
    }

    /**
     * Encapsula de forma limpa a lógica de manipulação e segurança de escrita em disco.
     */
    private String gravarArquivoNoDisco(MultipartFile arquivo) throws IOException {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new IllegalArgumentException("O arquivo de template do certificado enviado está inválido ou vazio.");
        }

        // Recupera a extensão original de forma defensiva (.png, .jpg...)
        String nomeOriginal = arquivo.getOriginalFilename();
        String extensao = "";
        if (nomeOriginal != null && nomeOriginal.contains(".")) {
            extensao = nomeOriginal.substring(nomeOriginal.lastIndexOf("."));
        } else {
            extensao = ".png"; // Fallback seguro
        }

        // Padrão de Segurança: Renomeia o arquivo para UUID para blindar contra ataques de Directory Traversal
        // e impedir que dois professores que subiram "certificado.png" sobrescrevam o arquivo um do outro.
        String nomeUnico = UUID.randomUUID().toString() + extensao;

        // Resolve o caminho físico e executa a cópia binária
        Path caminhoDestino = Paths.get(DIRETORIO_UPLOADS).resolve(nomeUnico);
        Files.copy(arquivo.getInputStream(), caminhoDestino, StandardCopyOption.REPLACE_EXISTING);

        return nomeUnico;
    }
}