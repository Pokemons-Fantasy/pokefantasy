package com.villu.pokefantasy.team;

/**
 * Operaciones que cambian equipos después del draft. Cada una se hace en una ventana de la jornada
 * (robos o intercambios) y tiene sus mensajes para cuando el draft no ha acabado o la ventana está cerrada.
 */
public enum TeamOperation {

    STEAL(Window.STEAL,
            "Los robos solo se pueden hacer con el draft completado",
            "La ventana de robos no está abierta."),
    TRADE(Window.SWAP,
            "Los intercambios solo se pueden hacer con el draft completado",
            "La ventana de intercambios no está abierta."),
    SWAP(Window.SWAP,
            "Los intercambios con el banquillo solo se pueden hacer con el draft completado",
            "El intercambio con el banquillo no está permitido en este momento. "
                    + "El plazo cerró o los resultados de la jornada anterior aún no están completos."),
    BUY(Window.SWAP,
            "Las compras del banquillo solo se pueden hacer con el draft completado",
            "La compra de Pokémon del banquillo no está permitida en este momento. "
                    + "El plazo cerró o los resultados de la jornada anterior aún no están completos."),
    RELEASE(Window.SWAP,
            "Solo se pueden liberar Pokémon una vez completado el draft",
            "La ventana de intercambios está cerrada. Solo puedes liberar Pokémon con ella abierta.");

    enum Window { STEAL, SWAP }

    final Window window;
    final String draftNotCompletedMessage;
    final String windowClosedMessage;

    TeamOperation(Window window, String draftNotCompletedMessage, String windowClosedMessage) {
        this.window = window;
        this.draftNotCompletedMessage = draftNotCompletedMessage;
        this.windowClosedMessage = windowClosedMessage;
    }
}
