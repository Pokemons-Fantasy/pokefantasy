package com.villu.pokefantasy.response;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LeagueResponse {
    private String id;
    private String name;
    private String createdBy;
    private int memberCount;
    /** Obsoleto: ningún comando lo pasa de SETUP a ACTIVE. La fase de la liga sale de draftStatus. */
    private LeagueStatus status;
    /** Estado del draft vigente (activo o, si no hay, el último); null si la liga no tiene draft. */
    private DraftStatus draftStatus;
}
