package com.thecolonel63.serversidereplayrecorder.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.thecolonel63.serversidereplayrecorder.ServerSideReplayRecorderServer;
import com.thecolonel63.serversidereplayrecorder.config.RegionConfig;
import com.thecolonel63.serversidereplayrecorder.mixin.accessors.ServerCommonNetworkHandlerAccessor;
import com.thecolonel63.serversidereplayrecorder.recorder.PlayerRecorder;
import com.thecolonel63.serversidereplayrecorder.recorder.RegionRecorder;
import com.thecolonel63.serversidereplayrecorder.recorder.ReplayRecorder;
import com.thecolonel63.serversidereplayrecorder.util.ChunkBox;
import com.thecolonel63.serversidereplayrecorder.util.FileHandlingUtility;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.ColumnPosArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.ColumnPos;
import net.minecraft.world.World;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ReplayCommand {
    private static LiteralArgumentBuilder<ServerCommandSource> handleFile() {
        return CommandManager.literal("file")
                .then(
                        CommandManager.literal("player")
                                .then(
                                        handleFileType(PlayerRecorder.PLAYER_FOLDER)
                                )
                ).then(
                        CommandManager.literal("region")
                                .then(
                                        handleFileType(RegionRecorder.REGION_FOLDER)
                                )
                );
    }

    private static RequiredArgumentBuilder<ServerCommandSource, String> handleFileType(String subFolder) {
        return CommandManager.argument("name", StringArgumentType.word())
                .suggests(
                        (context, builder) -> {
                            File folder = Paths.get(ServerSideReplayRecorderServer.config.getReplay_folder_name(), subFolder).toFile();
                            Set<String> suggestions = new LinkedHashSet<>();
                            if (folder.exists() && folder.isDirectory()) {
                                for (File file : Objects.requireNonNull(folder.listFiles(File::isDirectory))) {
                                    if (!file.getName().matches(".*\\s.*"))
                                        suggestions.add(file.getName());
                                }
                            }
                            return CommandSource.suggestMatching(
                                    suggestions,
                                    builder
                            );
                        }
                ).then(
                        CommandManager.literal("delete")
                                .then(
                                        CommandManager.argument("filename", StringArgumentType.greedyString())
                                        .suggests(
                                                (context, builder) -> {
                                                    String name = StringArgumentType.getString(context, "name");
                                                    File folder = Paths.get(ServerSideReplayRecorderServer.config.getReplay_folder_name(), subFolder, name).toFile();
                                                    Set<String> suggestions = new LinkedHashSet<>();
                                                    if (folder.exists() && folder.isDirectory()) {
                                                        for (File file : Objects.requireNonNull(folder.listFiles(File::isFile))) {
                                                            if (FilenameUtils.getExtension(file.getName()).equals("mcpr"))
                                                                suggestions.add(file.getName());
                                                        }
                                                    }
                                                    return CommandSource.suggestMatching(
                                                            suggestions,
                                                            builder
                                                    );
                                                }
                                        )
                                        .executes(
                                                context -> {
                                                    String name = StringArgumentType.getString(context, "name");
                                                    String filename = StringArgumentType.getString(context, "filename");
                                                    File file = Paths.get(ServerSideReplayRecorderServer.config.getReplay_folder_name(), subFolder, name, filename).toFile();
                                                    ServerCommandSource source = context.getSource();
                                                    if (file.exists() && file.isFile()) {
                                                        try {
                                                            Files.delete(file.toPath());
                                                            source.sendFeedback(()->Text.literal("File %s deleted".formatted(file.toString())).formatted(Formatting.YELLOW), true);
                                                            if (Objects.requireNonNull(file.getParentFile().list()).length == 0){
                                                                Files.delete(file.getParentFile().toPath());
                                                                source.sendFeedback(()->Text.literal("Folder %s deleted".formatted(file.getParentFile().toString())).formatted(Formatting.YELLOW), true);
                                                            }
                                                        } catch (Throwable t) {
                                                            source.sendError(Text.literal("An Error occurred while deleting File %s".formatted(file.toString())).formatted(Formatting.RED));
                                                            return 1;
                                                        }
                                                        return 0;
                                                    } else {
                                                        source.sendError(Text.literal("File %s does not Exist".formatted(file.toString())).formatted(Formatting.RED));
                                                        return 1;
                                                    }
                                                }
                                        )
                        )
                ).then(
                        CommandManager.literal("upload")
                                .then(
                                        CommandManager.argument("filename", StringArgumentType.greedyString())
                                                .suggests(
                                                        (context, builder) -> {
                                                            String name = StringArgumentType.getString(context, "name");
                                                            File folder = Paths.get(ServerSideReplayRecorderServer.config.getReplay_folder_name(), subFolder, name).toFile();
                                                            Set<String> suggestions = new LinkedHashSet<>();
                                                            if (folder.exists() && folder.isDirectory()) {
                                                                for (File file : Objects.requireNonNull(folder.listFiles(File::isFile))) {
                                                                    if (FilenameUtils.getExtension(file.getName()).equals("mcpr"))
                                                                        suggestions.add(file.getName());
                                                                }
                                                            }
                                                            return CommandSource.suggestMatching(
                                                                    suggestions,
                                                                    builder
                                                            );
                                                        }
                                                )
                                                .executes(
                                                        context -> {
                                                            String name = StringArgumentType.getString(context, "name");
                                                            String filename = StringArgumentType.getString(context, "filename");
                                                            File file = Paths.get(ServerSideReplayRecorderServer.config.getReplay_folder_name(), subFolder, name, filename).toFile();
                                                            ServerCommandSource source = context.getSource();
                                                            if (file.exists() && file.isFile()) {
                                                                Thread thread = new Thread(()-> {
                                                                    try {
                                                                        Text result = FileHandlingUtility.uploadToTemp(file);
                                                                        source.sendFeedback(()->result, true);
                                                                    } catch (Throwable t) {
                                                                        source.sendError(Text.literal("An Error occurred while uploading File %s".formatted(file.toString())).formatted(Formatting.RED));
                                                                        t.printStackTrace();
                                                                    }
                                                                });
                                                                source.sendFeedback(()->Text.literal("Uploading...").formatted(Formatting.YELLOW), false);
                                                                thread.start();
                                                                return 0;
                                                            } else {
                                                                source.sendError(Text.literal("File %s does not Exist".formatted(file.toString())).formatted(Formatting.RED));
                                                                return 1;
                                                            }
                                                        }
                                                )
                                )
                );
    }

    private static LiteralArgumentBuilder<ServerCommandSource> handleRegion() {
        return CommandManager.literal("region")
                .then(
                        CommandManager.argument("regionName", StringArgumentType.word())
                                .suggests(
                                        (context, builder) -> CommandSource.suggestMatching(
                                                ServerSideReplayRecorderServer.config.getRegions().stream().map(RegionConfig::name).collect(Collectors.toSet()),
                                                builder
                                        )
                                ).then(
                                        CommandManager.literal("define")
                                                .then(
                                                        CommandManager.argument("from", ColumnPosArgumentType.columnPos())
                                                                .then(
                                                                        CommandManager.argument("to", ColumnPosArgumentType.columnPos())
                                                                                .executes(
                                                                                        context -> {
                                                                                            ServerCommandSource source = context.getSource();
                                                                                            if (source.isExecutedByPlayer()) {
                                                                                                String regionName = StringArgumentType.getString(context, "regionName");
                                                                                                ColumnPos pos1 = ColumnPosArgumentType.getColumnPos(context, "from");
                                                                                                ColumnPos pos2 = ColumnPosArgumentType.getColumnPos(context, "to");
                                                                                                ChunkPos cpos1 = new ChunkPos(ChunkSectionPos.getSectionCoord(pos1.x()), ChunkSectionPos.getSectionCoord(pos1.z()));
                                                                                                ChunkPos cpos2 = new ChunkPos(ChunkSectionPos.getSectionCoord(pos2.x()), ChunkSectionPos.getSectionCoord(pos2.z()));
                                                                                                if (ServerSideReplayRecorderServer.config.getRegions().stream().anyMatch(r -> r.name().equals(regionName))) {
                                                                                                    context.getSource().sendError(Text.literal("Region %s is already defined".formatted(regionName)).formatted(Formatting.RED));
                                                                                                    return 1;
                                                                                                } else {
                                                                                                    ChunkBox box = new ChunkBox(cpos1, cpos2);
                                                                                                    RegionConfig regionConfig = new RegionConfig(regionName, source.getWorld().getRegistryKey().getValue().toString(), new RegionConfig.ChunkCoord(box.min.x, box.min.z), new RegionConfig.ChunkCoord(box.max.x, box.max.z));
                                                                                                    ServerSideReplayRecorderServer.config.getRegions().add(regionConfig);
                                                                                                    ServerSideReplayRecorderServer.saveConfig();
                                                                                                    source.sendFeedback(() -> Text.literal("Defined region %s, from %d %d to %d %d in world %s".formatted(regionName, box.min.x, box.min.z, box.max.x, box.max.z, regionConfig.world())).formatted(Formatting.YELLOW), true);
                                                                                                    return 0;
                                                                                                }
                                                                                            } else {
                                                                                                source.sendFeedback(() -> Text.literal("Region Definition can only be done by players").formatted(Formatting.YELLOW), true);
                                                                                                return 0;
                                                                                            }
                                                                                        }
                                                                                )
                                                                )
                                                )
                                ).then(
                                        CommandManager.literal("forget")
                                                .executes(
                                                        context -> {
                                                            String regionName = StringArgumentType.getString(context, "regionName");
                                                            RegionConfig cfg = ServerSideReplayRecorderServer.config.getRegions().stream().filter(r -> r.name().equals(regionName)).findFirst().orElse(null);
                                                            if (cfg == null) {
                                                                context.getSource().sendError(Text.literal("Region %s is not defined".formatted(regionName)).formatted(Formatting.RED));
                                                                return 0;
                                                            } else {
                                                                RegionRecorder recorder = RegionRecorder.regionRecorderMap.get(regionName);
                                                                if (recorder == null) {
                                                                    ServerCommandSource source = context.getSource();
                                                                    ServerSideReplayRecorderServer.config.getRegions().remove(cfg);
                                                                    ServerSideReplayRecorderServer.saveConfig();
                                                                    source.sendFeedback(() -> Text.literal("Region %s forgotten".formatted(regionName)).formatted(Formatting.YELLOW), true);
                                                                    return -1;
                                                                } else {
                                                                    context.getSource().sendError(Text.literal("Region %s is still recording".formatted(regionName)).formatted(Formatting.RED));
                                                                    return 0;
                                                                }
                                                            }
                                                        }
                                                )
                                ).then(
                                        CommandManager.literal("start")
                                                .executes(
                                                        context -> {
                                                            String regionName = StringArgumentType.getString(context, "regionName");
                                                            if (ServerSideReplayRecorderServer.config.isRecording_enabled()) {
                                                                RegionRecorder recorder = RegionRecorder.regionRecorderMap.get(regionName);
                                                                if (recorder == null) {
                                                                    RegionConfig cfg = ServerSideReplayRecorderServer.config.getRegions().stream().filter(r -> r.name().equals(regionName)).findFirst().orElse(null);
                                                                    ServerCommandSource source = context.getSource();
                                                                    if (cfg != null) {
                                                                        try {
                                                                            source.sendFeedback(() -> Text.literal("Starting Region %s".formatted(regionName)).formatted(Formatting.YELLOW), false);
                                                                            Identifier identifier = new Identifier(cfg.world());
                                                                            RegistryKey<World> registryKey = RegistryKey.of(RegistryKeys.WORLD, identifier);
                                                                            ServerWorld world = ServerSideReplayRecorderServer.server.getWorld(registryKey);
                                                                            if (world != null) {
                                                                                CompletableFuture<RegionRecorder> future = RegionRecorder.createAsync(regionName, new ChunkPos(cfg.min().x(), cfg.min().z()), new ChunkPos(cfg.max().x(), cfg.max().z()), world);
                                                                                future.thenAcceptAsync(r -> source.sendFeedback(() -> Text.literal("Started Recording Region %s, from %d %d to %d %d in %s".formatted(regionName, r.region.min.x, r.region.min.z, r.region.max.x, r.region.max.z, cfg.world())).formatted(Formatting.YELLOW), true), source.getServer());
                                                                                future.exceptionallyAsync(throwable -> {
                                                                                    throwable.printStackTrace();
                                                                                    context.getSource().sendError(Text.literal("An Exception occurred while starting %s recording".formatted(regionName)).formatted(Formatting.RED));
                                                                                    return null;
                                                                                }, source.getServer());
                                                                                return 1;
                                                                            } else {
                                                                                context.getSource().sendError(Text.literal("Unknown world %s for region %s".formatted(cfg.world(), regionName)).formatted(Formatting.RED));
                                                                                return 0;
                                                                            }
                                                                        } catch (
                                                                                Throwable e) {
                                                                            e.printStackTrace();
                                                                            context.getSource().sendError(Text.literal("An Exception occurred while starting %s recording".formatted(regionName)).formatted(Formatting.RED));
                                                                            return 0;
                                                                        }
                                                                    } else {
                                                                        context.getSource().sendError(Text.literal("Unknown Region %s".formatted(regionName)).formatted(Formatting.RED));
                                                                        return 0;
                                                                    }
                                                                } else {
                                                                    context.getSource().sendError(Text.literal("Region %s already started".formatted(regionName)).formatted(Formatting.RED));
                                                                    return 0;
                                                                }
                                                            } else {
                                                                context.getSource().sendError(Text.literal("Recording is disabled").formatted(Formatting.RED));
                                                                return 0;
                                                            }
                                                        }
                                                )
                                ).then(
                                        CommandManager.literal("stop")
                                                .executes(
                                                        context -> {
                                                            String regionName = StringArgumentType.getString(context, "regionName");
                                                            if (ServerSideReplayRecorderServer.config.getRegions().stream().anyMatch(r -> r.name().equals(regionName))) {
                                                                RegionRecorder recorder = RegionRecorder.regionRecorderMap.get(regionName);
                                                                if (recorder != null) {
                                                                    ServerCommandSource source = context.getSource();
                                                                    recorder.handleDisconnect();
                                                                    source.sendFeedback(() -> Text.literal("Region %s stopped, started saving... (%s)".formatted(regionName, recorder.getFileName())).formatted(Formatting.YELLOW), true);
                                                                    return -1;
                                                                } else {
                                                                    context.getSource().sendError(Text.literal("Region %s is not recording".formatted(regionName)).formatted(Formatting.RED));
                                                                    return 0;
                                                                }
                                                            } else {
                                                                context.getSource().sendError(Text.literal("Unknown Region %s".formatted(regionName)).formatted(Formatting.RED));
                                                                return 0;
                                                            }
                                                        }
                                                )
                                ).then(
                                        CommandManager.literal("status")
                                                .executes(
                                                        context -> {
                                                            String regionName = StringArgumentType.getString(context, "regionName");
                                                            RegionConfig cfg = ServerSideReplayRecorderServer.config.getRegions().stream().filter(r -> r.name().equals(regionName)).findFirst().orElse(null);
                                                            RegionRecorder recorder = RegionRecorder.regionRecorderMap.get(regionName);
                                                            if (cfg != null) {
                                                                ServerCommandSource source = context.getSource();
                                                                source.sendFeedback(() -> Text.literal("Region %s:".formatted(regionName)).formatted(Formatting.YELLOW), true);
                                                                source.sendFeedback(() -> Text.literal("Dimension: %s".formatted(cfg.world())).formatted(Formatting.YELLOW), true);
                                                                source.sendFeedback(() -> Text.literal("Area: %d %d to %d %d".formatted(cfg.min().x(), cfg.min().z(), cfg.max().x(), cfg.max().z())).formatted(Formatting.YELLOW), true);
                                                                if (recorder != null) {
                                                                    source.sendFeedback(() -> Text.literal("Status: %s".formatted(recorder.getStatus().toString())).formatted(Formatting.YELLOW), true);
                                                                    source.sendFeedback(() -> Text.literal("Uptime: %s".formatted(DurationFormatUtils.formatDurationWords(recorder.getUptime().toMillis(), true, true))).formatted(Formatting.YELLOW), true);
                                                                    source.sendFeedback(() -> Text.literal("Size: %s".formatted(FileUtils.byteCountToDisplaySize(recorder.getFileSize()))).formatted(Formatting.YELLOW), true);
                                                                    return 2;
                                                                } else {
                                                                    source.sendFeedback(() -> Text.literal("Status: Defined").formatted(Formatting.YELLOW), true);
                                                                    return 1;
                                                                }
                                                            } else {
                                                                context.getSource().sendError(Text.literal("Unknown Region %s".formatted(regionName)).formatted(Formatting.RED));
                                                                return 0;
                                                            }
                                                        }
                                                )
                                )
                );
    }

    private static LiteralArgumentBuilder<ServerCommandSource> handleStatus() {
        return CommandManager.literal("status")
                .executes(context -> {
                    context.getSource().sendFeedback(() -> Text.literal("Recording " + ((ServerSideReplayRecorderServer.config.isRecording_enabled()) ? "Enabled" : "Disabled")).formatted(Formatting.YELLOW), true);

                    Collection<ReplayRecorder> recorders = ReplayRecorder.get_existing_recorders();

                    if (!recorders.isEmpty()) {
                        context.getSource().sendFeedback(
                                () -> Text.literal("Active Recorders:"), true);
                        for (ReplayRecorder recorder : recorders) {
                            String tag;
                            if (recorder instanceof PlayerRecorder) {
                                tag = "Player";
                            } else if (recorder instanceof RegionRecorder) {
                                tag = "Region";
                            } else {
                                tag = "Recorder";
                            }
                            context.getSource().sendFeedback(
                                    () -> Text.literal("|"), true);
                            context.getSource().sendFeedback(
                                    () -> Text.literal("|_%s %s:"
                                            .formatted(
                                                    tag,
                                                    recorder.getRecordingName()
                                            )), true);
                            context.getSource().sendFeedback(
                                    () -> Text.literal("|   |Status: %s"
                                            .formatted(
                                                    recorder.getStatus().toString()
                                            )), true);
                            context.getSource().sendFeedback(
                                    () -> Text.literal("|   |Uptime: %s"
                                            .formatted(
                                                    DurationFormatUtils.formatDurationWords(recorder.getUptime().toMillis(), true, true)
                                            )), true);
                            context.getSource().sendFeedback(
                                    () -> Text.literal("|   |Size: %s"
                                            .formatted(
                                                    FileUtils.byteCountToDisplaySize(recorder.getFileSize())
                                            )), true);
                            context.getSource().sendFeedback(
                                    () -> Text.literal("|   |Remaining Tasks: %d"
                                            .formatted(
                                                    recorder.getRemainingTasks()
                                            )), true);
                        }
                    }
                    return 0;
                })
                .then(CommandManager.literal("enable").executes(context -> {
                    ServerSideReplayRecorderServer.config.setRecording_enabled(true);
                    context.getSource().sendFeedback(() -> Text.literal("Recording " + ((ServerSideReplayRecorderServer.config.isRecording_enabled()) ? "Enabled" : "Disabled")).formatted(Formatting.YELLOW), true);
                    ServerSideReplayRecorderServer.saveConfig();
                    return 0;
                }))
                .then(CommandManager.literal("disable").executes(context -> {
                    ServerSideReplayRecorderServer.config.setRecording_enabled(false);
                    context.getSource().sendFeedback(() -> Text.literal("Recording " + ((ServerSideReplayRecorderServer.config.isRecording_enabled()) ? "Enabled" : "Disabled")).formatted(Formatting.YELLOW), true);
                    ServerSideReplayRecorderServer.saveConfig();
                    return 0;
                }));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> handlePlayer() {
        return CommandManager.literal("players")
                .then(CommandManager.literal("add")
                        .then(CommandManager.argument("targets", GameProfileArgumentType.gameProfile())
                                .suggests(
                                        (context, builder) -> {
                                            PlayerManager playerManager = context.getSource().getServer().getPlayerManager();
                                            return CommandSource.suggestMatching(
                                                    playerManager.getPlayerList()
                                                            .stream()
                                                            .map(player -> player.getGameProfile().getName())
                                                            .filter(Predicate.not(ServerSideReplayRecorderServer.config.getRecordable_users()::contains)),
                                                    builder
                                            );
                                        }
                                )
                                .executes(context -> {
                                    Collection<GameProfile> profiles = GameProfileArgumentType.getProfileArgument(context, "targets");
                                    List<String> to_add = profiles.stream().map(GameProfile::getName).toList();
                                    ServerCommandSource source = context.getSource();
                                    AtomicInteger count = new AtomicInteger();
                                    to_add.forEach(n -> {
                                        if (ServerSideReplayRecorderServer.config.getRecordable_users().add(n)) {
                                            source.sendFeedback(() -> Text.literal("%s added to replay list".formatted(n)).formatted(Formatting.YELLOW), true);
                                            count.getAndIncrement();
                                        }
                                    });
                                    if (count.get() > 0) {
                                        source.sendFeedback(() -> Text.literal("Added players will start recording next time they join the server").formatted(Formatting.YELLOW), true);
                                        ServerSideReplayRecorderServer.saveConfig();
                                    }
                                    return 0;
                                })
                        )).then(CommandManager.literal("remove")
                        .then(CommandManager.argument("targets", GameProfileArgumentType.gameProfile())
                                .suggests(
                                        (context, builder) -> {
                                            PlayerManager playerManager = context.getSource().getServer().getPlayerManager();
                                            return CommandSource.suggestMatching(
                                                    playerManager.getPlayerList()
                                                            .stream()
                                                            .map(player -> player.getGameProfile().getName())
                                                            .filter(ServerSideReplayRecorderServer.config.getRecordable_users()::contains),
                                                    builder
                                            );
                                        }
                                )
                                .executes(context -> {
                                    Collection<GameProfile> profiles = GameProfileArgumentType.getProfileArgument(context, "targets");
                                    List<String> to_remove = profiles.stream().map(GameProfile::getName).toList();
                                    ServerCommandSource source = context.getSource();
                                    AtomicInteger count = new AtomicInteger();
                                    to_remove.forEach(n -> {
                                        if (ServerSideReplayRecorderServer.config.getRecordable_users().remove(n)) {
                                            source.sendFeedback(() -> Text.literal("%s removed from replay list".formatted(n)).formatted(Formatting.YELLOW), true);
                                            count.getAndIncrement();
                                        }
                                    });
                                    if (count.get() > 0) {
                                        source.sendFeedback(() -> Text.literal("Removed players will stop recording on logout").formatted(Formatting.YELLOW), true);
                                        ServerSideReplayRecorderServer.saveConfig();
                                    }
                                    return 0;
                                })
                        )).then(CommandManager.literal("list")
                        .executes(context -> {
                            ServerCommandSource source = context.getSource();
                            source.sendFeedback(() -> Text.literal("Replay allowed users: %s"
                                            .formatted(String.join(", ", ServerSideReplayRecorderServer.config.getRecordable_users()))).formatted(Formatting.YELLOW),
                                    false);
                            return 0;
                        })
                );
    }

    private static LiteralArgumentBuilder<ServerCommandSource> handleMarker() {
        return CommandManager.literal("marker")
                .then(
                        CommandManager.literal("player")
                                .then(
                                        CommandManager.argument("player", EntityArgumentType.player())
                                                .executes(
                                                        context -> {
                                                            ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
                                                            PlayerRecorder recorder = PlayerRecorder.playerRecorderMap.get(((ServerCommonNetworkHandlerAccessor) player.networkHandler).getConnection());
                                                            if (recorder != null) {
                                                                recorder.addMarker(null);
                                                                context.getSource().sendFeedback(() -> Text.literal("Added a marker on Player %s Recording".formatted(player.getGameProfile().getName())), true);
                                                                return 0;
                                                            } else {
                                                                context.getSource().sendError(Text.literal("Unknown Player %s or Player not Recording".formatted(player.getGameProfile().getName())).formatted(Formatting.RED));
                                                                return 1;
                                                            }
                                                        }
                                                )
                                )
                ).then(
                        CommandManager.literal("region")
                                .then(
                                        CommandManager.argument("regionName", StringArgumentType.word())
                                                .suggests(
                                                        (context, builder) -> CommandSource.suggestMatching(
                                                                RegionRecorder.regionRecorderMap.keySet(),
                                                                builder
                                                        )
                                                )
                                                .executes(
                                                        context -> {
                                                            String regionName = StringArgumentType.getString(context, "regionName");
                                                            RegionRecorder recorder = RegionRecorder.regionRecorderMap.get(regionName);
                                                            if (recorder != null) {
                                                                recorder.addMarker(null);
                                                                context.getSource().sendFeedback(() -> Text.literal("Added a marker on Region %s Recording".formatted(regionName)), true);
                                                                return 0;
                                                            } else {
                                                                context.getSource().sendError(Text.literal("Unknown Region %s".formatted(regionName)).formatted(Formatting.RED));
                                                                return 1;
                                                            }
                                                        }
                                                )
                                )
                );
    }

    public void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("replay")
                .requires(s -> s.hasPermissionLevel(ServerSideReplayRecorderServer.config.getCommand_op_level()))
                .then(
                        handlePlayer()
                ).then(
                        handleStatus()
                ).then(
                        handleRegion()
                ).then(
                        handleFile()
                ).then(
                        handleMarker()
                )
        );
    }
}
