package com.villu.pokefantasy.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TierAdjustmentResponse {
    private List<TierChangeDto> changes;
}
