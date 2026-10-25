package com.villu.pokefantasy.request.schedule;

import lombok.Data;

import java.util.List;

@Data
public class ImportSeriesRequest {
    /** Ids de los replays de Showdown de la serie (1 en Bo1; 2 o 3 en Bo3). */
    private List<String> replayIds;
}
