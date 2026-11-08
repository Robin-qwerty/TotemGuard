/*
 * This file is part of TotemGuard - https://github.com/Bram1903/TotemGuard
 * Copyright (C) 2026 Bram and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.deathmotion.totemguard.common.features.log;

import com.deathmotion.totemguard.api.check.Check;
import com.deathmotion.totemguard.api.check.CheckType;
import com.deathmotion.totemguard.api.punishment.PunishmentType;
import com.deathmotion.totemguard.common.TGPlatform;
import com.deathmotion.totemguard.common.check.CheckImpl;
import com.deathmotion.totemguard.common.player.TGPlayer;
import com.deathmotion.totemguard.common.player.data.ping.PingData;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.world.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class IncidentFileLog {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TGPlatform platform;
    private final Path violationsFile;
    private final Path punishmentsFile;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "totemguard-incident-log");
        thread.setDaemon(true);
        return thread;
    });

    public IncidentFileLog(@NotNull TGPlatform platform) {
        this.platform = platform;
        Path pluginDir = Path.of(platform.getPluginDirectory());
        Path folder = pluginDir.getParent() == null ? pluginDir : pluginDir.getParent();
        this.violationsFile = folder.resolve("violations.txt");
        this.punishmentsFile = folder.resolve("punishment.txt");
    }

    public void logFlag(@NotNull CheckImpl check, int violations, @Nullable String debug) {
        String line = formatFlag(check, violations, debug);
        submit(violationsFile, line);
    }

    public void logPunishment(@NotNull CheckImpl check,
                              @NotNull List<String> commands,
                              @NotNull List<PunishmentType> types,
                              @Nullable String debug) {
        String line = formatPunishment(check, commands, types, debug);
        submit(punishmentsFile, line);
    }

    public void shutdown() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(3, TimeUnit.SECONDS)) {
                writer.shutdownNow();
            }
        } catch (InterruptedException ex) {
            writer.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void submit(Path file, String line) {
        try {
            writer.execute(() -> append(file, line));
        } catch (RejectedExecutionException ex) {
            platform.getLogger().warning("Dropped incident log line because the writer is shut down.");
        }
    }

    private void append(Path file, String line) {
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            boolean fresh = !Files.exists(file) || Files.size(file) == 0;
            String payload = fresh ? header(file) + line + System.lineSeparator() : line + System.lineSeparator();
            Files.writeString(file, payload, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            platform.getLogger().log(Level.WARNING, "Failed to write " + file.getFileName(), ex);
        }
    }

    private static String header(Path file) {
        if (file.getFileName().toString().startsWith("punishment")) {
            return "# time player uuid trigger vl action commands ping version brand gamemode world position look server flags types debug"
                    + System.lineSeparator();
        }
        return "# time player uuid check type vl ping version brand gamemode world position look server debug"
                + System.lineSeparator();
    }

    private String formatFlag(CheckImpl check, int violations, @Nullable String debug) {
        TGPlayer player = check.player;
        return time()
                + " player=" + clean(player.getName())
                + " uuid=" + player.getUuid()
                + " check=" + clean(check.getName())
                + " type=" + check.getType()
                + " vl=" + violations + "/" + check.getMaxViolations()
                + context(player)
                + " debug=" + clean(debug);
    }

    private String formatPunishment(CheckImpl check,
                                    List<String> commands,
                                    List<PunishmentType> types,
                                    @Nullable String debug) {
        TGPlayer player = check.player;
        return time()
                + " player=" + clean(player.getName())
                + " uuid=" + player.getUuid()
                + " trigger=" + clean(check.getName())
                + " vl=" + check.getViolations() + "/" + check.getMaxViolations()
                + " action=" + joinTypes(types)
                + " commands=" + joinCommands(commands)
                + context(player)
                + " " + flagCounts(player)
                + " debug=" + clean(debug);
    }

    private String context(TGPlayer player) {
        Location loc = player.getData().getMovementData().getCurrent();
        GameMode gameMode = player.getData().getGameMode();
        String world = player.getData().getWorldEntityData().getWorldName();
        String server = platform.getConfigRepository().configView().server();
        return " ping=" + ping(player)
                + " version=" + clean(String.valueOf(player.getClientVersion()))
                + " brand=" + clean(player.getClientBrand())
                + " gamemode=" + (gameMode == null ? "-" : gameMode.name())
                + " world=" + clean(world)
                + " pos=" + coord(loc.getX()) + "," + coord(loc.getY()) + "," + coord(loc.getZ())
                + " look=" + coord(loc.getYaw()) + "," + coord(loc.getPitch())
                + " server=" + clean(server);
    }

    private static String flagCounts(TGPlayer player) {
        List<Check> flagged = new ArrayList<>();
        for (Check check : player.getCheckManager().allChecks.values()) {
            if (check.getViolations() > 0) flagged.add(check);
        }
        flagged.sort(Comparator.comparing(Check::getName));

        StringBuilder flags = new StringBuilder("flags=");
        StringBuilder types = new StringBuilder();
        Map<CheckType, Integer> byType = new EnumMap<>(CheckType.class);
        for (int i = 0; i < flagged.size(); i++) {
            Check check = flagged.get(i);
            if (i > 0) flags.append(',');
            flags.append(clean(check.getName())).append('=').append(check.getViolations());
            byType.merge(check.getType(), check.getViolations(), Integer::sum);
        }
        if (flagged.isEmpty()) flags.append('-');

        types.append("types=");
        boolean first = true;
        for (Map.Entry<CheckType, Integer> entry : byType.entrySet()) {
            if (!first) types.append(',');
            first = false;
            types.append(entry.getKey().name()).append('=').append(entry.getValue());
        }
        if (first) types.append('-');
        return flags + " " + types;
    }

    private static String ping(TGPlayer player) {
        PingData ping = player.getPingData();
        int value = ping.getKeepAlivePing();
        if (value < 0) value = ping.getTransactionPing();
        return value < 0 ? "-" : value + "ms";
    }

    private static String joinTypes(List<PunishmentType> types) {
        if (types.isEmpty()) return "-";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < types.size(); i++) {
            if (i > 0) out.append(',');
            out.append(types.get(i).name());
        }
        return out.toString();
    }

    private static String joinCommands(List<String> commands) {
        if (commands.isEmpty()) return "-";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < commands.size(); i++) {
            if (i > 0) out.append(" || ");
            out.append(clean(commands.get(i)));
        }
        return out.toString();
    }

    private static String time() {
        return LocalDateTime.now().format(TIME);
    }

    private static String coord(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static String clean(@Nullable String value) {
        if (value == null || value.isBlank()) return "-";
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }
}
