package com.thecolonel63.serversidereplayrecorder.util.packets;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.*;

@SuppressWarnings({"rawtypes", "unchecked"})
public class FuturePacket extends FutureTask<Packet> implements Packet {
    private static final ThreadFactory packetThreadFactory = new ThreadFactoryBuilder().setNameFormat("Future-Packet-%d").setDaemon(true).build();
    public static final ExecutorService packetExecutor = Executors.newCachedThreadPool(packetThreadFactory);

    private final long start = System.currentTimeMillis();
    private long duration = 500;
    public FuturePacket(@NotNull Callable<Packet> callable) {
        super(callable);
    }

    public FuturePacket(@NotNull Runnable runnable, Packet result) {
        super(runnable, result);
    }

    public long getTimeout() {
        return Math.max(0, System.currentTimeMillis() - (this.start + duration));
    }

    public void setExpectedDuration(long duration) {
        this.duration = duration;
    }

    @Override
    public void write(PacketByteBuf buf) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void apply(PacketListener listener) {
        throw new UnsupportedOperationException();
    }
}
