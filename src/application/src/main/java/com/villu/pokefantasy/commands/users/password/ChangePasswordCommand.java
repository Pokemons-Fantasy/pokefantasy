package com.villu.pokefantasy.commands.users.password;

import com.villu.pokefantasy.mediator.Command;

public record ChangePasswordCommand(String username, String currentPassword, String newPassword) implements Command {}
