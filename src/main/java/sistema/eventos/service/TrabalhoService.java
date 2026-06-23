package sistema.eventos.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import sistema.eventos.dto.RequestTrabalhoDto;
import sistema.eventos.exception.NegocioException;
import sistema.eventos.model.Evento;
import sistema.eventos.model.Inscricao;
import sistema.eventos.model.StatusTrabalho;
import sistema.eventos.model.TrabalhoCientifico;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.EventoRepository;
import sistema.eventos.repository.TrabalhoCientificoRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
public class TrabalhoService {

    private static final Logger logger = LoggerFactory.getLogger(TrabalhoService.class);

    private final TrabalhoCientificoRepository trabalhoRepository;
    private final EventoRepository eventoRepository;

    // Diretório base centralizado usando a estrutura correta do seu projeto
    private final Path diretorioUploads = Paths.get(System.getProperty("user.home"), "even3_uploads", "trabalhos");

    public TrabalhoService(TrabalhoCientificoRepository trabalhoRepository, EventoRepository eventoRepository) {
        this.trabalhoRepository = trabalhoRepository;
        this.eventoRepository = eventoRepository;
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
        if (arquivo == null || arquivo.isEmpty() || !"application/pdf".equals(arquivo.getContentType())) {
            throw new NegocioException("É obrigatório o envio de um arquivo válido no formato PDF.");
        }

        Evento evento = eventoRepository.findById(dto.getEventoId())
                .orElseThrow(() -> new NegocioException("O evento associado a este trabalho não foi encontrado."));

        Path caminhoCompleto = null;

        try {
            // Cria a árvore de diretórios se não existir de forma segura
            if (!Files.exists(diretorioUploads)) {
                Files.createDirectories(diretorioUploads);
            }

            // --- 2. SEGURANÇA: Higienização contra Path Traversal ---
            String nomeOriginalLimpo = Paths.get(arquivo.getOriginalFilename()).getFileName().toString();
            String nomeArquivoUnico = UUID.randomUUID().toString() + "_" + nomeOriginalLimpo;

            // Resolve o caminho de forma estrita dentro da pasta alvo para gravação física
            caminhoCompleto = diretorioUploads.resolve(nomeArquivoUnico).normalize();

            // --- 3. PERSISTÊNCIA DOS DADOS ---
            // Sênior: Gravamos apenas o 'nomeArquivoUnico' no banco. Isso desvincula a infraestrutura física de arquivos
            // do banco de dados, facilitando migrações de servidores e o funcionamento do WebConfig.
            TrabalhoCientifico trabalho = TrabalhoCientifico.builder()
                    .titulo(dto.getTitulo())
                    .resumo(dto.getResumo())
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