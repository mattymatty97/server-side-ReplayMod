package com.thecolonel63.serversidereplayrecorder.config;

public record RegionConfig(String name, String world, int minX, int minZ, int maxX, int maxZ) {
    @Override
    public String name() {
        return name;
    }

    @Override
    public String world() {
        return world;
    }

    @Override
    public int minX() {
        return minX;
    }

    @Override
    public int minZ() {
        return minZ;
    }

    @Override
    public int maxX() {
        return maxX;
    }

    @Override
    public int maxZ() {
        return maxZ;
    }
}
