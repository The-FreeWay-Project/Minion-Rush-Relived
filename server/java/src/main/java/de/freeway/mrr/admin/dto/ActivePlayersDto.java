package de.freeway.mrr.admin.dto;

import java.util.List;

public record ActivePlayersDto(
        int count,
        List<ActivePlayerDto> players
) {
}
