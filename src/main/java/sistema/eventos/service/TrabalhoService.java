package sistema.eventos.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import sistema.eventos.dto.RequestTrabalhoDto;
import sistema.eventos.exception.NegocioException;
import sistema.eventos.model.Evento;
import sistema.eventos.model.Inscricao;
import sistema.eventos.model.StatusInscricao;
import sistema.eventos.model.StatusTrabalho;
import sistema.eventos.model.TrabalhoCientifico;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.EventoRepository;
import sistema.eventos.repository.InscricaoRepository;
import sistema.eventos.repository.TrabalhoCientificoRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.io.InputStream;

@Service
public class TrabalhoService {

    private static final Logger logger = LoggerFactory.getLogger(TrabalhoService.class);

    private final TrabalhoCientificoRepository trabalhoRepository;
    private final EventoRepository eventoRepository;
    private final InscricaoRepository inscricaoRepository;

    @Value("${app.private-upload.dir:./private_uploads/trabalhos}")
    private String privateUploadDirectory;

    public TrabalhoService(TrabalhoCientificoRepository trabalhoRepository, EventoRepository eventoRepository,
                           InscricaoRepository inscricaoRepository) {
        this.trabalhoRepository = trabalhoRepository;
        this.eventoRepository = eventoRepository;
        this.inscricaoRepository = inscricaoRepository;
    }

    /**
     * Sobrecarga sênior para atender o fluxo simplificado do ParticipanteController baseado na Inscrição.
     */
    @Transactional
    public void salvarTrabalhoFisico(String titulo, MultipartFile arquivo, Inscricao inscricao) {
        RequestTrabalhoDto dto = new RequestTrabalhoDto();
        dto.setTitulo(titulo);
        dto.setEventoId(inscricao.getEvento().getId());
        dto.setResumo("Submetido via painel de inscrições."); // Valor padrão caso o formulário simplificado não possua resumo

        submeterTrabalho(dto, arquivo, inscricao.getParticipante());
    }

    @Transactional
    public void submeterTrabalho(RequestTrabalhoDto dto, MultipartFile arquivo, Usuario autor) {
        // --- 1. VALIDAÇÃO DE ARQUIVO ---
        if (dto.getTitulo() == null || dto.getTitulo().isBlank() || dto.getTitulo().length() > 200) {
            throw new NegocioException("Informe um título válido com até 200 caracteres.");
        }
        String resumo = dto.getResumo() == null || dto.getResumo().isBlank()
                ? "Submetido pela área do participante." : dto.getResumo().trim();
        if (resumo.length() > 20_000) throw new NegocioException("O resumo excede o tamanho permitido.");
        if (arquivo == null || arquivo.isEmpty() || arquivo.getSize() > 20L * 1024 * 1024) {
            throw new NegocioException("Envie um arquivo PDF de até 20 MB.");
        }
        try (InputStream input = arquivo.getInputStream()) {
            byte[] signature = input.readNBytes(5);
            if (signature.length != 5 || signature[0] != '%' || signature[1] != 'P'
                    || signature[2] != 'D' || signature[3] != 'F' || signature[4] != '-') {
                throw new NegocioException("O conteúdo enviado não é um arquivo PDF válido.");
            }
        } catch (IOException e) {
            throw new NegocioException("Não foi possível ler o arquivo enviado.");
        }

        Evento evento = eventoRepository.findById(dto.getEventoId())
                .orElseThrow(() -> new NegocioException("O evento associado a este trabalho não foi encontrado."));
        if (autor == null || autor.getId() == null || !inscricaoRepository.existsByParticipanteIdAndEventoIdAndStatusNot(
                autor.getId(), evento.getId(), StatusInscricao.CANCELADO)) {
            throw new org.springframework.security.access.AccessDeniedException("É necessário possuir uma inscrição ativa para submeter trabalho neste evento.");
        }

        Path caminhoCompleto = null;

        try {
            // Cria a árvore de diretórios se não existir de forma segura
            Path diretorioUploads = Paths.get(privateUploadDirectory).toAbsolutePath().normalize();
            Files.createDirectories(diretorioUploads);
            String nomeArquivoUnico = UUID.randomUUID() + ".pdf";

            // Resolve o caminho de forma estrita dentro da pasta alvo para gravação física
            caminhoCompleto = diretorioUploads.resolve(nomeArquivoUnico).normalize();
            if (!caminhoCompleto.startsWith(diretorioUploads)) throw new NegocioException("Destino de arquivo inválido.");

            // --- 3. PERSISTÊNCIA DOS DADOS ---
            // Sênior: Gravamos apenas o 'nomeArquivoUnico' no banco. Isso desvincula a infraestrutura física de arquivos
            // do banco de dados, facilitando migrações de servidores e o funcionamento do WebConfig.
            TrabalhoCientifico trabalho = TrabalhoCientifico.builder()
                    .titulo(dto.getTitulo())
                    .resumo(resumo)
                    .caminhoArquivo(nomeArquivoUnico)
                    .status(StatusTrabalho.SUBMETIDO)
                    .autor(autor)
                    .evento(evento)
                    .build();

            // Salva no banco primeiro
            trabalhoRepository.save(trabalho);

            // --- 4. ESCRITA NO DISCO (Última etapa do fluxo feliz) ---
            Files.copy(arquivo.getInputStream(), caminhoCompleto);

        } catch (IOException e) {
            // --- 5. CLEANUP / ROLLBACK FÍSICO ---
            if (caminhoCompleto != null && Files.exists(caminhoCompleto)) {
                try {
                    Files.delete(caminhoCompleto);
                } catch (IOException ioException) {
                    logger.error("Falha ao apagar arquivo órfão após erro de transação: {}", caminhoCompleto, ioException);
                }
            }
            throw new NegocioException("Falha crítica no sistema de armazenamento de arquivos. Tente novamente.", e);
        }
    }

    /**
     * Retorna todos os trabalhos de um evento específico para exibição no painel do Organizador.
     */
    @Transactional(readOnly = true)
    public List<TrabalhoCientifico> listarPorEvento(Evento evento) {
        return trabalhoRepository.findByEvento(evento);
    }
}
