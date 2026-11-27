package com.villu.pokefantasy.request.schedule;

import lombok.Data;

@Data
public class ShiftScheduleRequest {
    /** Primera jornada que se mueve; las siguientes se mueven con ella. */
    private Integer fromRound;
    /** Semanas que se mueve (1 a 12; en negativo para adelantar). */
    private Integer weeks;
}
