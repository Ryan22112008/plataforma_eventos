package sistema.eventos.config;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class GlobalErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleGlobalErrors(HttpServletRequest request, Model model) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);

        if (status != null) {
            int statusCode = Integer.parseInt(status.toString());

            if (statusCode == 404) {
                model.addAttribute("mensagem", "A página que você está procurando não foi encontrada no servidor.");
                return "erro/404"; // Aponta para templates/erro/404.html
            } else if (statusCode == 403) {
                model.addAttribute("mensagem", "Você não tem permissão para acessar esta área do sistema.");
                return "erro/403"; // Aponta para templates/erro/403.html
            }
        }

        model.addAttribute("mensagem", "Ocorreu um erro interno inesperado no servidor do Even3.");
        return "erro/500"; // Aponta para templates/erro/500.html para qualquer outro erro
    }
}