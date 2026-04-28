package com.villu.pokefantasy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Ability implements Serializable {

    private AbilityData ability;
    private String description;

}
