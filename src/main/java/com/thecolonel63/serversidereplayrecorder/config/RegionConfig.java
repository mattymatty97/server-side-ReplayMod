package com.thecolonel63.serversidereplayrecorder.config;

public record RegionConfig(String name, String world, ChunkCoord min, ChunkCoord max) {

    public record ChunkCoord(int x, int z) {
    }
}
