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

package com.deathmotion.totemguard.common.features.integration.impl;

import com.deathmotion.totemguard.common.TGPlatform;
import com.deathmotion.totemguard.common.features.integration.Integration;
import com.deathmotion.totemguard.common.player.bedrock.BedrockInfo;
import com.deathmotion.totemguard.common.player.bedrock.BedrockInputMode;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import org.geysermc.floodgate.util.DeviceOs;
import org.geysermc.floodgate.util.InputMode;

import java.util.UUID;

public final class FloodgateIntegration implements Integration {

    private static final String PLUGIN_NAME = "floodgate";

    private boolean enabled;

    @Override
    public String getName() {
        return PLUGIN_NAME;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void enable() {
        try {
            // Touching the API here, inside the guarded registration path, surfaces any
            // LinkageError from a mismatched Floodgate build now instead of at player login.
            FloodgateApi.getInstance();
            enabled = true;
            TGPlatform.getInstance().getLogger().info(PLUGIN_NAME + " detected, Bedrock player detection enabled.");
        } catch (Exception | LinkageError exception) {
            enabled = false;
            TGPlatform.getInstance().getLogger().severe("Failed to enable " + PLUGIN_NAME + " integration: " + exception);
        }
    }

    @Override
    public void disable() {
        enabled = false;
    }

    public BedrockInfo lookup(UUID uuid) {
        if (!enabled) return BedrockInfo.NOT_BEDROCK;

        try {
            FloodgateApi api = FloodgateApi.getInstance();
            if (!api.isFloodgatePlayer(uuid)) return BedrockInfo.NOT_BEDROCK;

            FloodgatePlayer player = api.getPlayer(uuid);
            if (player == null) return BedrockInfo.NOT_BEDROCK;

            return new BedrockInfo(true, deviceOsName(player.getDeviceOs()), inputMode(player.getInputMode()));
        } catch (Exception | LinkageError exception) {
            TGPlatform.getInstance().getLogger().warning("Floodgate lookup failed for " + uuid + ": " + exception);
            return BedrockInfo.NOT_BEDROCK;
        }
    }

    private static String deviceOsName(DeviceOs os) {
        return os == null ? "UNKNOWN" : os.name();
    }

    private static BedrockInputMode inputMode(InputMode mode) {
        if (mode == null) return BedrockInputMode.UNKNOWN;
        return switch (mode) {
            case KEYBOARD_MOUSE -> BedrockInputMode.KEYBOARD_MOUSE;
            case TOUCH -> BedrockInputMode.TOUCH;
            case CONTROLLER -> BedrockInputMode.CONTROLLER;
            case VR -> BedrockInputMode.VR;
            default -> BedrockInputMode.UNKNOWN;
        };
    }
}
