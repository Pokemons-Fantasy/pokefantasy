package com.villu.pokefantasy.request.draft;

import lombok.Data;

import java.util.List;

@Data
public class StartDraftRequest {
    private List<String> turnOrder;
}
