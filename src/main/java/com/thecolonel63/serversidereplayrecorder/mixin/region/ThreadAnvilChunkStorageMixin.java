package com.thecolonel63.serversidereplayrecorder.mixin.region;

import com.llamalad7.mixinextras.sugar.Local;
import com.thecolonel63.serversidereplayrecorder.recorder.RegionRecorder;
import com.thecolonel63.serversidereplayrecorder.util.interfaces.RegionRecorderEntityTracker;
import com.thecolonel63.serversidereplayrecorder.util.interfaces.RegionRecorderStorage;
import com.thecolonel63.serversidereplayrecorder.util.interfaces.RegionRecorderWorld;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.*;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.thread.MessageListener;
import net.minecraft.world.chunk.WorldChunk;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;
import java.util.Set;

@SuppressWarnings("rawtypes")
@Mixin(ThreadedAnvilChunkStorage.class)
public abstract class ThreadAnvilChunkStorageMixin implements RegionRecorderStorage {

    @Shadow
    @Final
    ServerWorld world;

    @Shadow
    protected abstract ServerLightingProvider getLightingProvider();

    @Shadow
    @Final
    private Int2ObjectMap<ThreadedAnvilChunkStorage.EntityTracker> entityTrackers;

    @Shadow
    @Final
    private MessageListener<ChunkTaskPrioritySystem.Task<Runnable>> mainExecutor;

    @Inject(method = "loadEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ThreadedAnvilChunkStorage$EntityTracker;updateTrackedStatus(Ljava/util/List;)V"), locals = LocalCapture.CAPTURE_FAILHARD)
    void handleEntityLoaded(Entity entity, CallbackInfo ci, EntityType entityType, int i, int j, ThreadedAnvilChunkStorage.EntityTracker entityTracker) {
        ((RegionRecorderEntityTracker) entityTracker).updateTrackedStatus(Set.copyOf(((RegionRecorderWorld) this.world).getRegionRecorders()));
    }

    @Inject(method = "tickEntityMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ThreadedAnvilChunkStorage$EntityTracker;updateTrackedStatus(Ljava/util/List;)V", ordinal = 0), locals = LocalCapture.CAPTURE_FAILHARD)
    void handleEntityMovement(CallbackInfo ci, List list, List list2, ObjectIterator var3, ThreadedAnvilChunkStorage.EntityTracker entityTracker, ChunkSectionPos chunkSectionPos, ChunkSectionPos chunkSectionPos2) {
        ((RegionRecorderEntityTracker) entityTracker).updateTrackedStatus(Set.copyOf(((RegionRecorderWorld) this.world).getRegionRecorders()));
    }

    @Inject(method = "updatePosition", at = @At(value = "HEAD"))
    void handlePlayerMovement(ServerPlayerEntity player, CallbackInfo ci) {
        ((RegionRecorderEntityTracker) this.entityTrackers.get(player.getId())).updateTrackedStatus(Set.copyOf(((RegionRecorderWorld) this.world).getRegionRecorders()));
    }

    /**
     * lambda method in {@see net.minecraft.server.world.ThreadedAnvilChunkStorage#makeChunkTickable }
     */
    @Inject(method = "method_18193", at = @At(value = "RETURN"))
    void handleChunkLoaded(ChunkHolder chunkHolder, WorldChunk chunk, CallbackInfo ci, @Local MutableObject<ChunkDataS2CPacket> cachedDataPacket) {
        Set<RegionRecorder> recorders = Set.copyOf(((RegionRecorderWorld) this.world).getRegionRecordersByExpandedChunk().get(chunkHolder.getPos()));
        if (recorders != null) {
            if (cachedDataPacket.getValue() == null) {
                cachedDataPacket.setValue(new ChunkDataS2CPacket(chunk, this.getLightingProvider(), null, null));
            }
            recorders.forEach(r -> r.onPacket(cachedDataPacket.getValue()));
        }
    }


    @Override
    public void registerRecorder(RegionRecorder recorder) {
        this.entityTrackers.forEach(
                (integer, entityTracker) -> ((RegionRecorderEntityTracker) entityTracker).updateTrackedStatus(recorder)
        );
    }
}
