package com.villu.pokefantasy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Sprites implements Serializable {

    @JsonProperty("front_default")
    private String frontDefault;
}
