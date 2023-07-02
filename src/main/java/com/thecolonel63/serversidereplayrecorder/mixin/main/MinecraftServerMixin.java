package com.thecolonel63.serversidereplayrecorder.mixin.main;

import com.thecolonel63.serversidereplayrecorder.ServerSideReplayRecorderServer;
import com.thecolonel63.serversidereplayrecorder.recorder.ReplayRecorder;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Iterator;
import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    @Inject(method = "runServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;setupServer()Z"))
    private void onInitServer(CallbackInfo ci) {
        ServerSideReplayRecorderServer.registerServer((MinecraftServer)(Object)this);
    }

    @Inject(method = "shutdown", at = @At(value = "HEAD"))
    private void onStopServer(CallbackInfo ci) {
        Iterator<ReplayRecorder> iterator = ReplayRecorder.active_recorders.iterator();
        //Use iterator to avoid Concurrent Modification Exception
        while(iterator.hasNext()){
            ReplayRecorder recorder = iterator.next();
            iterator.remove();
            recorder.handleDisconnect();
        }
    }

    @Inject(method = "shutdown", at = @At(value = "RETURN"))
    private void onStopServerTail(CallbackInfo ci) {
        ServerSideReplayRecorderServer.LOGGER.warn("Waiting for all recorders to finish saving");
    }

    @Inject(method = "tick", at = @At("RETURN"))
    void onTickEnd(BooleanSupplier shouldKeepTicking, CallbackInfo ci){
        ReplayRecorder.active_recorders.forEach(ReplayRecorder::onServerTick);
        ReplayRecorder.prune_existing_recorders();
    }

}
