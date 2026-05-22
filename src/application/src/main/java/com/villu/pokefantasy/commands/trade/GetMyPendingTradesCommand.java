package com.villu.pokefantasy.commands.trade;

import com.villu.pokefantasy.mediator.Command;

public record GetMyPendingTradesCommand(String username) implements Command {}
