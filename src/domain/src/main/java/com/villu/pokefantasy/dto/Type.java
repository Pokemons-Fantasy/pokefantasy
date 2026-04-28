package com.villu.pokefantasy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Type implements Serializable {
    private TypeData type;
    private List<String> doubleDamageFrom;
    private List<String> doubleDamageTo;
    private List<String> halfDamageFrom;
    private List<String> halfDamageTo;
    private List<String> noDamageFrom;
    private List<String> noDamageTo;

}
