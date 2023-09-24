package com.thecolonel63.serversidereplayrecorder.mixin.accessors;

import net.minecraft.server.command.CommandManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CommandManager.RegistrationEnvironment.class)
public interface RegistrationEnvironmentAccessor {
    @Accessor
    boolean isIntegrated();

    @Accessor
    boolean isDedicated();
}
