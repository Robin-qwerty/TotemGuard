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

package com.deathmotion.totemguard.common.commands.impl;

import com.deathmotion.totemguard.common.TGPlatform;
import com.deathmotion.totemguard.common.commands.AbstractCommand;
import com.deathmotion.totemguard.common.commands.suggestion.TGPlayerSuggestionProvider;
import com.deathmotion.totemguard.common.config.key.MessagesKeys;
import com.deathmotion.totemguard.common.network.NetworkPresenceRepository;
import com.deathmotion.totemguard.common.network.ProxyTopologyService;
import com.deathmotion.totemguard.common.network.RemotePlayerEntry;
import com.deathmotion.totemguard.common.platform.player.PlatformPlayer;
import com.deathmotion.totemguard.common.platform.sender.Sender;
import com.deathmotion.totemguard.common.player.TGPlayer;
import com.deathmotion.totemguard.common.player.data.MovementData;
import com.deathmotion.totemguard.common.redis.broker.packets.impl.SyncTeleportRequestPacket;
import com.github.retrooper.packetevents.protocol.world.Location;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.UUID;

public final class AlertTeleportCommand extends AbstractCommand {

    private static final int DELAY_TICKS = 5;
    private static final long REQUEST_TTL_MILLIS = 30_000L;

    private final TGPlatform platform;

    public AlertTeleportCommand() {
        this.platform = TGPlatform.getInstance();
    }

    @Override
    public void register(@NonNull CommandManager<Sender> manager) {
        manager.command(
                manager.commandBuilder("tgalertteleport")
                        .required(
                                "tg_player",
                                StringParser.stringParser(),
                                TGPlayerSuggestionProvider.suggestionProviderExcludingSelf()
                        )
                        .permission(perm("teleport"))
                        .handler(this::handle)
        );
    }

    private void handle(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        if (!requirePlayer(sender)) return;

        PlatformPlayer senderPlatform = platform.getPlatformPlayerFactory().create(sender.getUniqueId());
        if (senderPlatform == null) {
            sender.sendMessage(platform.getMessageService().getComponent(MessagesKeys.GENERAL_PLAYER_DATA_MISSING));
            return;
        }

        String rawTarget = context.get("tg_player");
        NetworkPresenceRepository presence = platform.getNetworkPresenceRepository();
        RemotePlayerEntry named = presence == null ? null : presence.findByName(rawTarget);
        Resolved resolved = accept(sender, named, rawTarget, sender.getUniqueId());
        if (resolved == null) return;

        UUID staffId = sender.getUniqueId();
        UUID targetId = resolved.target.playerUuid();
        senderPlatform.setSpectator();
        senderPlatform.runLaterTicks(DELAY_TICKS, () -> finish(staffId, targetId, rawTarget));
    }

    private void finish(@NotNull UUID staffId, @NotNull UUID targetId, @NotNull String rawTarget) {
        Sender sender = platform.createSender(staffId);
        PlatformPlayer staff = platform.getPlatformPlayerFactory().create(staffId);
        if (sender == null || staff == null) return;

        NetworkPresenceRepository presence = platform.getNetworkPresenceRepository();
        RemotePlayerEntry target = presence == null ? null : presence.findByUuid(targetId);
        Resolved resolved = accept(sender, target, rawTarget, staffId);
        if (resolved == null) return;

        if (resolved.local) {
            if (resolved.localTarget == null) return;
            applyLocalTeleport(staff, resolved.localTarget);
            sender.sendMessage(platform.getMessageService().getComponent(
                    MessagesKeys.TELEPORT_SAME_SERVER,
                    Map.of("tg_player", resolved.target.playerName())
            ));
            return;
        }

        if (presence == null) return;
        long expiresAt = System.currentTimeMillis() + REQUEST_TTL_MILLIS;
        SyncTeleportRequestPacket.Payload payload = new SyncTeleportRequestPacket.Payload(
                UUID.randomUUID(),
                staffId,
                resolved.target.playerUuid(),
                resolved.target.serverName(),
                resolved.target.serverInstanceId(),
                expiresAt
        );
        presence.publishTeleportRequest(payload);
        platform.getProxyTopologyService().connectToInstance(staffId, resolved.target.serverInstanceId());
        sender.sendMessage(platform.getMessageService().getComponent(
                MessagesKeys.TELEPORT_CROSS_SERVER,
                Map.of("tg_player", resolved.target.playerName(), "tg_server", resolved.target.serverName())
        ));
    }

    private @Nullable Resolved accept(@NotNull Sender sender,
                                      @Nullable RemotePlayerEntry target,
                                      @NotNull String rawTarget,
                                      @NotNull UUID staffId) {
        if (target == null) {
            sender.sendMessage(platform.getMessageService().getComponent(
                    MessagesKeys.TELEPORT_NOT_FOUND,
                    Map.of("tg_input", rawTarget)
            ));
            return null;
        }

        if (target.playerUuid().equals(staffId)) {
            sender.sendMessage(platform.getMessageService().getComponent(MessagesKeys.TELEPORT_SELF));
            return null;
        }

        NetworkPresenceRepository presence = platform.getNetworkPresenceRepository();
        if (presence == null) {
            sender.sendMessage(platform.getMessageService().getComponent(
                    MessagesKeys.TELEPORT_NOT_FOUND,
                    Map.of("tg_input", rawTarget)
            ));
            return null;
        }

        boolean local = presence.isLocal(target.serverInstanceId());
        TGPlayer localTarget = local ? platform.getPlayerRepository().getPlayer(target.playerUuid()) : null;

        if (local && localTarget == null && target.bypassed()) {
            sender.sendMessage(platform.getMessageService().getComponent(
                    MessagesKeys.TELEPORT_TARGET_BYPASSED,
                    Map.of("tg_player", target.playerName(), "tg_server", target.serverName())
            ));
            return null;
        }

        if (local && localTarget == null) {
            sender.sendMessage(platform.getMessageService().getComponent(
                    MessagesKeys.TELEPORT_NOT_FOUND,
                    Map.of("tg_input", rawTarget)
            ));
            return null;
        }

        if (!local && !platform.getRedisRepository().isConnected()) {
            sender.sendMessage(platform.getMessageService().getComponent(MessagesKeys.TELEPORT_NO_REDIS));
            return null;
        }

        if (!local) {
            if (!platform.getProxyTopologyService().bridgeAvailable()) {
                sender.sendMessage(platform.getMessageService().getComponent(MessagesKeys.TELEPORT_NO_BRIDGE));
                return null;
            }
            if (platform.checkRoute(target.serverInstanceId()) == ProxyTopologyService.RouteStatus.NOT_ROUTABLE) {
                sender.sendMessage(platform.getMessageService().getComponent(
                        MessagesKeys.TELEPORT_DIFFERENT_PROXY,
                        Map.of("tg_player", target.playerName(), "tg_server", target.serverName())
                ));
                return null;
            }
        }

        return new Resolved(target, localTarget, local);
    }

    private static void applyLocalTeleport(PlatformPlayer senderPlatform, TGPlayer target) {
        MovementData movement = target.getData().getMovementData();
        Location loc = movement.getCurrent();
        PlatformPlayer targetPlayer = target.getPlatformPlayer();
        String world = targetPlayer != null ? targetPlayer.getWorldName() : null;
        senderPlatform.teleport(world == null ? "" : world,
                loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
    }

    private record Resolved(RemotePlayerEntry target, TGPlayer localTarget, boolean local) {
    }
}
