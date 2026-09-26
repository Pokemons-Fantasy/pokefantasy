package com.villu.pokefantasy.commands.schedule;

/**
 * Marcador de un partido desde el punto de vista del ganador (p. ej. 3–1). Opcional: un resultado sin
 * marcador solo cuenta la victoria.
 */
public record MatchScore(int winner, int loser) {

    static final int MAX = 99;

    public MatchScore {
        if (winner < 0 || loser < 0 || winner > MAX || loser > MAX) {
            throw new IllegalArgumentException("El marcador debe estar entre 0 y " + MAX + ".");
        }
        if (winner <= loser) {
            throw new IllegalArgumentException("En el marcador, el ganador debe tener más que el perdedor.");
        }
    }

    /** {@code null} si no se indica marcador; error si solo viene una de las dos cifras. */
    public static MatchScore of(Integer winner, Integer loser) {
        if (winner == null && loser == null) {
            return null;
        }
        if (winner == null || loser == null) {
            throw new IllegalArgumentException("Indica el marcador de los dos jugadores, o ninguno.");
        }
        return new MatchScore(winner, loser);
    }
}
