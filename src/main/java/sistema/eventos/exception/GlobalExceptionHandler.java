package sistema.eventos.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

@ControllerAdvice
public class GlobalExceptionHandler {

     private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    public String tratarAcessoNegado(AccessDeniedException ex, Model model,
                                     jakarta.servlet.http.HttpServletResponse response) {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        model.addAttribute("mensagem", "Você não tem permissão para acessar este conteúdo.");
        return "erro/403";
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Void> tratarStatusExplicito(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).build();
    }

    @ExceptionHandler({NegocioException.class, IllegalArgumentException.class, IllegalStateException.class})
    public String tratarErrosDeRegraDeNegocio(RuntimeException ex, Model model) {
        logger.warn("Violação de regra de negócio interceptada: {}", ex.getMessage());

        model.addAttribute("mensagemErro", ex.getMessage());
         return "erro/400";
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public String tratarErroNaoEncontrado(NoHandlerFoundException ex, Model model) {
        logger.error("Rota inexistente acessada: {}", ex.getRequestURL());
        return "erro/404";
    }


    @ExceptionHandler(Exception.class)
    public String tratarErrosInesperados(Exception ex, Model model) {
        // Grava o erro completo com o StackTrace apenas no log do servidor (Segurança!)
        logger.error("Erro crítico inesperado interceptado pelo GlobalExceptionHandler: ", ex);

        model.addAttribute("mensagemErro", "Ocorreu um erro interno no servidor. Nossa equipe de engenharia já foi notificada.");
        return "erro/500";
    }
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> tratarRecursoEstaticoNaoEncontrado(NoResourceFoundException ex) {
        // Apenas responde 404 discretamente para o navegador (silencia o log de erro crítico)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
}
