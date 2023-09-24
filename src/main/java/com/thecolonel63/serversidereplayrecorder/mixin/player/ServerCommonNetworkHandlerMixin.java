package com.thecolonel63.serversidereplayrecorder.mixin.player;

import com.thecolonel63.serversidereplayrecorder.recorder.ReplayRecorder;
import com.thecolonel63.serversidereplayrecorder.util.interfaces.RecorderHolder;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.ref.WeakReference;

@Mixin(ServerCommonNetworkHandler.class)
public class ServerCommonNetworkHandlerMixin implements RecorderHolder {
    WeakReference<ReplayRecorder> recorder = new WeakReference<>(null);

    @Override
    public void setRecorder(ReplayRecorder recorder) {
        this.recorder = new WeakReference<>(recorder);
    }

    @Override
    public ReplayRecorder getRecorder() {
        return this.recorder.get();
    }

    @Inject(method = "sendPacket", at = @At("TAIL"))
    private void savePacket(Packet<?> packet, CallbackInfo ci) {
        //Get the recorder instance dedicated to this connection and give it the packet to record.
        //If there *is* a recorder.
        ReplayRecorder recorder = this.recorder.get();
        if (recorder != null) {
            recorder.onPacket(packet);
        }
    }
}
