package sistema.eventos.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import sistema.eventos.exception.NegocioException;
import sistema.eventos.model.Usuario;

import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class PerfilService {

    public static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;

    private final UsuarioService usuarioService;
    private final FileStorageService fileStorageService;

    @Transactional
    public Usuario atualizarFoto(String email, MultipartFile foto) {
        if (foto == null || foto.isEmpty()) {
            throw new NegocioException("Selecione uma imagem para enviar.");
        }
        if (foto.getSize() > MAX_AVATAR_BYTES) {
            throw new NegocioException("A foto de perfil deve ter no máximo 2 MB.");
        }

        Usuario usuario = usuarioService.buscarPorEmail(email);
        String fotoAnterior = usuario.getFotoPerfil();
        String novaFoto = fileStorageService.storeImage(foto, "perfil-" + usuario.getId());

        usuario.setFotoPerfil(novaFoto);
        try {
            Usuario salvo = usuarioService.salvar(usuario);
            removerArquivoComSeguranca(fotoAnterior, novaFoto);
            return salvo;
        } catch (RuntimeException e) {
            removerArquivoComSeguranca(novaFoto, null);
            throw e;
        }
    }

    @Transactional
    public void removerFoto(String email) {
        Usuario usuario = usuarioService.buscarPorEmail(email);
        String fotoAnterior = usuario.getFotoPerfil();
        if (fotoAnterior == null || fotoAnterior.isBlank()) {
            return;
        }

        usuario.setFotoPerfil(null);
        usuarioService.salvar(usuario);
        removerArquivoComSeguranca(fotoAnterior, null);
    }

    private void removerArquivoComSeguranca(String referencia, String referenciaMantida) {
        if (referencia == null || referencia.isBlank() || referencia.equals(referenciaMantida)) {
            return;
        }
        try {
            fileStorageService.deleteStoredImage(referencia);
        } catch (IOException | RuntimeException e) {
            log.warn("Não foi possível remover a foto de perfil antiga do armazenamento.", e);
        }
    }
}
