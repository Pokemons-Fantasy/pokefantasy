package com.villu.pokefantasy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Move implements Serializable {
    private MoveData move;
//    private String type;
//    private String power;
//    private int pp;
//    private int accuracy;
//    private String category;
}
