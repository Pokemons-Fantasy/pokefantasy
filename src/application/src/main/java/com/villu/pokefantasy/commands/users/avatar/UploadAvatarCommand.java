package com.villu.pokefantasy.commands.users.avatar;

import com.villu.pokefantasy.mediator.Command;

public record UploadAvatarCommand(String username, byte[] image) implements Command {}
