package com.villu.pokefantasy.ports;

/**
 * Eventos en tiempo real (SSE) hacia los clientes, entregados en <b>todas</b> las instancias de la app:
 * el cliente puede tener su conexión SSE abierta en una instancia distinta de la que atendió la acción.
 * Cada instancia recibe los eventos y los entrega a sus conexiones locales ({@link Listener}).
 */
public interface RealtimeEventPort {

    enum Audience {
        /** Todos los que siguen el draft de la liga {@code target}. */
        LEAGUE,
        /** Las conexiones de notificaciones del usuario {@code target}. */
        USER
    }

    /** Evento SSE: {@code name} es el nombre del evento y {@code data} su cuerpo (JSON). */
    record RealtimeEvent(Audience audience, String target, String name, String data) {}

    void publish(RealtimeEvent event);

    /** Entrega local: lo implementan los registros de conexiones SSE de cada instancia. */
    interface Listener {
        void onEvent(RealtimeEvent event);
    }
}
