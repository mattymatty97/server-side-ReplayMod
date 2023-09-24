package com.thecolonel63.serversidereplayrecorder.mixin.player;

import com.thecolonel63.serversidereplayrecorder.recorder.ReplayRecorder;
import com.thecolonel63.serversidereplayrecorder.util.interfaces.RecorderHolder;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.thecolonel63.serversidereplayrecorder.recorder.PlayerRecorder.playerRecorderMap;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    void constructor(MinecraftServer server, ClientConnection connection, ServerPlayerEntity player, ConnectedClientData clientData, CallbackInfo ci) {
        ReplayRecorder recorder = playerRecorderMap.get(connection);
        if (recorder != null){
            ((RecorderHolder) this).setRecorder(recorder);
        }
    }


    @Inject(method = "onDisconnected", at = @At("HEAD"))
    private void handleDisconnectionOfRecorder(Text reason, CallbackInfo ci) {
        //Tell the recorder to handle a disconnect, if there *is* a recorder
        ReplayRecorder recorder = ((RecorderHolder) this).getRecorder();
        if (recorder != null){
            recorder.handleDisconnect();
        }
    }

}
