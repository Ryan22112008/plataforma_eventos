package sistema.eventos.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import sistema.eventos.dto.DisciplinaDTO;
import sistema.eventos.dto.RequestEventoDto;
import sistema.eventos.dto.ResponseEventoDto;
import sistema.eventos.exception.NegocioException;
import sistema.eventos.model.*;
import sistema.eventos.repository.EventoRepository;
import sistema.eventos.repository.SessaoRepository; // 🎯 INJETADO

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
public class EventoService {

    private final EventoRepository eventoRepository;
    private final SessaoRepository sessaoRepository; // 🎯 INJETADO VIA CONSTRUTOR SÊNIOR
    private final FileStorageService fileStorageService;

    // Construtor único atualizado para injeção segura pelo Spring
    public EventoService(EventoRepository eventoRepository, SessaoRepository sessaoRepository,
                         FileStorageService fileStorageService) {
        this.eventoRepository = eventoRepository;
        this.sessaoRepository = sessaoRepository;
        this.fileStorageService = fileStorageService;
    }

    @Transactional(readOnly = true)
    public List<Evento> listarTodos() {
        return eventoRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Evento> listarPorOrganizador(Long organizadorId) {
        return eventoRepository.findByOrganizadorId(organizadorId);
    }

    @Transactional(readOnly = true)
    public Evento buscarPorId(Long id) {
        return eventoRepository.findByIdComOrganizador(id)
                .orElseThrow(() -> new NegocioException("Evento não encontrado com o ID informado: " + id));
    }

    @Transactional
    public void criarEvento(RequestEventoDto dto, Usuario organizador, MultipartFile banner) {
        validarUrlGoogleMaps(dto.getUrlGoogleMaps());
        Evento evento = new Evento();

        evento.setOrganizador(organizador);
        evento.setDataInicio(dto.getDataInicio());
        evento.setDataTermino(dto.getDataTermino());
        evento.setTitulo(dto.getTitulo());
        evento.setDescricao(dto.getDescricao());
        evento.setLocal(dto.getLocal());
        evento.setModalidade(dto.getModalidade());
        if (dto.getNicho() != null && !dto.getNicho().trim().isEmpty()) {
            evento.setNicho(converterNicho(dto.getNicho()));
        }
        evento.setUrlGoogleMaps(dto.getUrlGoogleMaps());
        evento.setLatitude(dto.getLatitude());
        evento.setLongitude(dto.getLongitude());
        evento.setCargaHoraria(dto.getCargaHoraria());

        if (dto.getVagasTotais() != null) {
            evento.setVagasTotais(dto.getVagasTotais());
            evento.setVagasRestantes(dto.getVagasTotais());
        } else {
            evento.setVagasTotais(100);
            evento.setVagasRestantes(100);
        }

        if (banner != null && !banner.isEmpty()) {
            evento.setBannerUrl(fileStorageService.storeImage(banner, "banner"));
        }

        if (dto.getImagemFrente() != null && !dto.getImagemFrente().isEmpty()) {
            evento.setCertificadoTemplate(fileStorageService.storeImage(dto.getImagemFrente(), "certificado-frente"));
        }

        if (dto.getImagemVerso() != null && !dto.getImagemVerso().isEmpty()) {
            evento.setCertificadoTemplateVerso(fileStorageService.storeImage(dto.getImagemVerso(), "certificado-verso"));
        }

        if (dto.getDisciplinas() != null) {
            for (DisciplinaDTO dDto : dto.getDisciplinas()) {
                if (dDto.getNome() != null && !dDto.getNome().trim().isEmpty()) {
                    Disciplina d = new Disciplina();
                    d.setNome(dDto.getNome());
                    d.setCargaHoraria(dDto.getCargaHoraria());
                    d.setEvento(evento);
                    evento.getDisciplinas().add(d);
                }
            }
        }

        // Salva o evento principal primeiro para gerar o ID legítimo
        Evento eventoSalvo = eventoRepository.save(evento);

        // 🎯 GERAÇÃO AUTOMÁTICA DAS MATRIZES DE DIÁRIO DE CLASSE (Passo 2)
        gerarSessoesAutomaticas(eventoSalvo);
    }

    @Transactional
    public void atualizarEvento(Long id, RequestEventoDto dto, MultipartFile banner, MultipartFile imgFrente, MultipartFile imgVerso) {
        validarUrlGoogleMaps(dto.getUrlGoogleMaps());
        Evento eventoOriginal = eventoRepository.findById(id)
                .orElseThrow(() -> new NegocioException("Evento não encontrado."));

        // Guarda as datas antigas para checar se houve mudança de período
        LocalDate dataInicioAntiga = eventoOriginal.getDataInicio();
        LocalDate dataFimAntiga = eventoOriginal.getDataTermino();

        eventoOriginal.setTitulo(dto.getTitulo());
        eventoOriginal.setDescricao(dto.getDescricao());
        eventoOriginal.setDataInicio(dto.getDataInicio());
        eventoOriginal.setDataTermino(dto.getDataTermino());
        eventoOriginal.setModalidade(dto.getModalidade());
        eventoOriginal.setNicho(converterNicho(dto.getNicho()));
        eventoOriginal.setCargaHoraria(dto.getCargaHoraria());
        eventoOriginal.setVagasTotais(dto.getVagasTotais());
        eventoOriginal.setLocal(dto.getLocal());
        eventoOriginal.setLatitude(dto.getLatitude());
        eventoOriginal.setLongitude(dto.getLongitude());

        if (banner != null && !banner.isEmpty()) {
            String novoBanner = fileStorageService.storeImage(banner, "banner");
            eventoOriginal.setBannerUrl(novoBanner);
        }

        if (imgFrente != null && !imgFrente.isEmpty()) {
            String novaFrente = fileStorageService.storeImage(imgFrente, "certificado-frente");
            eventoOriginal.setCertificadoTemplate(novaFrente);
        }

        if (imgVerso != null && !imgVerso.isEmpty()) {
            String novoVerso = fileStorageService.storeImage(imgVerso, "certificado-verso");
            eventoOriginal.setCertificadoTemplateVerso(novoVerso);
        }

        eventoOriginal.getDisciplinas().clear();
        if (dto.getDisciplinas() != null) {
            dto.getDisciplinas().forEach(dDto -> {
                if (dDto != null) {
                    Disciplina disciplina = new Disciplina();
                    disciplina.setNome(dDto.getNome());
                    disciplina.setCargaHoraria(dDto.getCargaHoraria());
                    disciplina.setEvento(eventoOriginal);
                    eventoOriginal.getDisciplinas().add(disciplina);
                }
            });
        }

        eventoRepository.save(eventoOriginal);

        // 🎯 SE AS DATAS MUDARAM NA EDIÇÃO, O DIÁRIO REFAZ AS SESSÕES AUTOMATICAMENTE
        if (!Objects.equals(dataInicioAntiga, dto.getDataInicio())
                || !Objects.equals(dataFimAntiga, dto.getDataTermino())) {
            sessaoRepository.deleteByEventoId(eventoOriginal.getId());
            gerarSessoesAutomaticas(eventoOriginal);
        }
    }

    private NichoEvento converterNicho(String valor) {
        String normalizado = Normalizer.normalize(valor == null ? "" : valor.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");

        if ("TECNOLOGIA_TI".equals(normalizado)) {
            return NichoEvento.TECNOLOGIA;
        }
        try {
            return NichoEvento.valueOf(normalizado);
        } catch (IllegalArgumentException exception) {
            throw new NegocioException("Área inválida. Use Tecnologia, Tecnologia e Inovação, Gestão Pública, Saúde ou Outros.");
        }
    }

    /**
     * 🎯 ENGINE AUTOMÁTICA SÊNIOR: Varre o calendário do evento criando as colunas de Turnos
     */
    private void gerarSessoesAutomaticas(Evento evento) {
        if (evento.getDataInicio() == null || evento.getDataTermino() == null) {
            return;
        }

        LocalDate dataAtual = evento.getDataInicio();
        LocalDate dataFim = evento.getDataTermino();

        while (!dataAtual.isAfter(dataFim)) {
            String diaSemana = traduzirDiaSemana(dataAtual);

            // 🌅 Turno Manhã (08:00 às 12:00)
            Sessao sessaoManha = new Sessao();
            sessaoManha.setEvento(evento);
            sessaoManha.setNome(diaSemana + " - Manhã");
            sessaoManha.setDataHoraInicio(LocalDateTime.of(dataAtual, LocalTime.of(8, 0, 0)));
            sessaoManha.setDataHoraFim(LocalDateTime.of(dataAtual, LocalTime.of(12, 0, 0)));
            sessaoManha.setQrCodeToken("M_" + evento.getId() + "_" + UUID.randomUUID());
            sessaoRepository.save(sessaoManha);

            // 🌇 Turno Tarde (14:00 às 18:00)
            Sessao sessaoTarde = new Sessao();
            sessaoTarde.setEvento(evento);
            sessaoTarde.setNome(diaSemana + " - Tarde");
            sessaoTarde.setDataHoraInicio(LocalDateTime.of(dataAtual, LocalTime.of(14, 0, 0)));
            sessaoTarde.setDataHoraFim(LocalDateTime.of(dataAtual, LocalTime.of(18, 0, 0)));
            sessaoTarde.setQrCodeToken("T_" + evento.getId() + "_" + UUID.randomUUID());
            sessaoRepository.save(sessaoTarde);

            dataAtual = dataAtual.plusDays(1); // Avança o ponteiro do calendário
        }
    }

    private String traduzirDiaSemana(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case MONDAY -> "Seg";
            case TUESDAY -> "Ter";
            case WEDNESDAY -> "Qua";
            case THURSDAY -> "Qui";
            case FRIDAY -> "Sex";
            case SATURDAY -> "Sáb";
            case SUNDAY -> "Dom";
        };
    }

    private void validarUrlGoogleMaps(String value) {
        if (value == null || value.isBlank()) return;
        try {
            URI uri = URI.create(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new NegocioException("O link do mapa precisa ser uma URL HTTPS válida.");
        }
    }

    // --- Mantenha o restante dos métodos privados e auxiliares originais inalterados daqui para baixo ---
    @Transactional
    public void atualizarArteCertificado(Long eventoId, MultipartFile arquivo, String emailOrganizador) {
        Evento evento = buscarPorId(eventoId);
        if (!evento.getOrganizador().getEmail().equalsIgnoreCase(emailOrganizador)) {
            throw new org.springframework.security.access.AccessDeniedException("Você não tem acesso a este evento.");
        }
        if (arquivo == null || arquivo.isEmpty()) {
            throw new NegocioException("Por favor, selecione um arquivo válido de imagem do Canva.");
        }
        String arteAntiga = evento.getCertificadoTemplate();
        String nomeArquivoPersistido = fileStorageService.storeImage(arquivo, "certificado");
        evento.setCertificadoTemplate(nomeArquivoPersistido);
        eventoRepository.save(evento);
        if (arteAntiga != null && !arteAntiga.equals(nomeArquivoPersistido)) {
            try { fileStorageService.deleteStoredImage(arteAntiga); }
            catch (IOException e) { log.warn("Não foi possível remover uma arte antiga de certificado.", e); }
        }
    }

    @Transactional
    public void publicarEvento(Long eventoId) {
        Evento evento = buscarPorId(eventoId);
        if (evento.getStatus() != StatusEvento.RASCUNHO) {
            throw new NegocioException("Apenas eventos em status de RASCUNHO podem ser publicados.");
        }
        evento.setStatus(StatusEvento.PUBLICADO);
    }

    @Transactional(readOnly = true)
    public List<Evento> buscarPorTitulo(String titulo) {
        return eventoRepository.findByTituloContainingIgnoreCase(titulo);
    }

    @Transactional
    public Evento salvarEvento(Evento evento) {
        evento.setStatus(StatusEvento.PUBLICADO);
        if (evento.getVagasRestantes() == null || evento.getVagasRestantes() == 0) {
            evento.setVagasRestantes(evento.getVagasTotais());
        }
        return eventoRepository.save(evento);
    }

    @Transactional
    public void excluirEvento(Long id, String usernameLogado) {
        Evento evento = eventoRepository.findById(id)
                .orElseThrow(() -> new NegocioException("Evento não encontrado com o ID informado."));
        if (!evento.getOrganizador().getEmail().equalsIgnoreCase(usernameLogado)) {
            throw new org.springframework.security.access.AccessDeniedException("Você não tem acesso a este evento.");
        }
        if (evento.getBannerUrl() != null && !evento.getBannerUrl().trim().isEmpty()) {
            try {
                fileStorageService.deleteStoredImage(evento.getBannerUrl());
            } catch (IOException e) {
                log.warn("Não foi possível remover o banner do evento {}.", id, e);
            }
        }
        String template = evento.getCertificadoTemplate();
        if (template != null && !template.trim().isEmpty() && !template.trim().startsWith("<")) {
            try {
                fileStorageService.deleteStoredImage(template);
            } catch (IOException e) {
                log.warn("Não foi possível remover uma arte de certificado do evento {}.", id, e);
            }
        }
        try { fileStorageService.deleteStoredImage(evento.getCertificadoTemplateVerso()); }
        catch (IOException e) { log.warn("Não foi possível remover o verso do certificado do evento {}.", id, e); }
        eventoRepository.delete(evento);
    }

    @Transactional(readOnly = true)
    public RequestEventoDto buscarPorIdParaEdicao(Long id) {
        Evento evento = eventoRepository.findById(id)
                .orElseThrow(() -> new NegocioException("Evento não encontrado."));
        RequestEventoDto dto = new RequestEventoDto();
        dto.setId(evento.getId());
        dto.setTitulo(evento.getTitulo());
        dto.setDescricao(evento.getDescricao());
        dto.setDataInicio(evento.getDataInicio());
        dto.setDataTermino(evento.getDataTermino());
        dto.setModalidade(evento.getModalidade());
        dto.setCargaHoraria(evento.getCargaHoraria());
        dto.setVagasTotais(evento.getVagasTotais());
        dto.setLocal(evento.getLocal());
        dto.setLatitude(evento.getLatitude());
        dto.setLongitude(evento.getLongitude());
        dto.setBannerAtual(evento.getBannerUrl());
        dto.setImagemFrenteAtual(evento.getCertificadoTemplate());
        dto.setImagemVersoAtual(evento.getCertificadoTemplateVerso());
        if (evento.getNicho() != null) {
            dto.setNicho(evento.getNicho().name());
        }
        if (evento.getDisciplinas() != null) {
            evento.getDisciplinas().forEach(disciplina -> {
                DisciplinaDTO dDto = new DisciplinaDTO();
                dDto.setNome(disciplina.getNome());
                dDto.setCargaHoraria(disciplina.getCargaHoraria());
                dto.getDisciplinas().add(dDto);
            });
        }
        return dto;
    }
}
