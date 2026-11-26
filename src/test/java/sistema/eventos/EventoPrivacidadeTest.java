package sistema.eventos;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import sistema.eventos.model.Evento;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventoPrivacidadeTest {

    @Test
    void eventoNovoTemPrivacidadePublicaPorPadrao() {
        Evento evento = new Evento();

        assertFalse(evento.isEventoPrivado());
        assertFalse(evento.getPrivado());
    }

    @Test
    void eventoPrivadoPermanecePrivado() {
        Evento evento = new Evento();
        evento.setPrivado(true);

        assertTrue(evento.isEventoPrivado());
        assertTrue(evento.getPrivado());
    }

    @Test
    void nuloLegadoEhLidoComoPublicoEInicializadoComoPublicoAoCriar() {
        Evento evento = new Evento();
        evento.setPrivado(null);

        assertFalse(evento.isEventoPrivado());
        assertFalse(evento.getPrivado());

        ReflectionTestUtils.invokeMethod(evento, "aoCriar");

        assertFalse(evento.isEventoPrivado());
    }
}
