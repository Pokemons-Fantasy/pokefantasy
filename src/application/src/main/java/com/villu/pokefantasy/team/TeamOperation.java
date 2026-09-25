package com.villu.pokefantasy.team;

/**
 * Operaciones que cambian equipos después del draft. Cada una se hace en una ventana de la jornada
 * (robos o intercambios) y tiene sus mensajes para cuando el draft no ha acabado o la ventana está cerrada.
 */
public enum TeamOperation {

    STEAL(Window.STEAL,
            "Steals are only allowed after the draft is completed",
            "La ventana de robos no está abierta."),
    TRADE(Window.SWAP,
            "Trades are only allowed after the draft is completed",
            "La ventana de intercambios no está abierta. El plazo cerró el viernes a las 16:00."),
    SWAP(Window.SWAP,
            "Swaps are only allowed after the draft is completed",
            "El intercambio con la banca no está permitido en este momento. "
                    + "El plazo cerró o los resultados de la jornada anterior aún no están completos."),
    BUY(Window.SWAP,
            "Bench purchases are only allowed after the draft is completed",
            "La compra de pokémon de la banca no está permitida en este momento. "
                    + "El plazo cerró o los resultados de la jornada anterior aún no están completos."),
    RELEASE(Window.SWAP,
            "Solo se pueden liberar pokémon una vez completado el draft",
            "La ventana de intercambio está cerrada. Solo puedes liberar pokémon en la ventana de swap.");

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
