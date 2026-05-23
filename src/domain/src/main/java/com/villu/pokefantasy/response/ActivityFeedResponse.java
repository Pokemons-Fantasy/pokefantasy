package com.villu.pokefantasy.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityFeedResponse {
    private List<ActivityEventResponse> events;
    private int page;
    private int totalPages;
    private boolean hasMore;
}
