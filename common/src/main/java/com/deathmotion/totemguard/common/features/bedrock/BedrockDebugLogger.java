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

package com.deathmotion.totemguard.common.features.bedrock;

import com.deathmotion.totemguard.common.TGPlatform;
import com.deathmotion.totemguard.common.player.TGPlayer;
import lombok.experimental.UtilityClass;

@UtilityClass
public class BedrockDebugLogger {

    public void logClickTiming(TGPlayer player, long clickDiff, long useDiff, boolean flagged) {
        if (!enabled()) return;
        TGPlatform.getInstance().getLogger().info(String.format(
                "[TotemGuard-Bedrock] player=%s device=%s input=%s clickDiff=%dms useDiff=%dms flagged=%s",
                player.getName(), player.getBedrockDeviceOs(), player.getBedrockInputMode(), clickDiff, useDiff, flagged));
    }

    public void logConsistency(TGPlayer player, double stdDev, String outcome) {
        if (!enabled()) return;
        TGPlatform.getInstance().getLogger().info(String.format(
                "[TotemGuard-Bedrock] player=%s device=%s input=%s stdDev=%.2f outcome=%s",
                player.getName(), player.getBedrockDeviceOs(), player.getBedrockInputMode(), stdDev, outcome));
    }

    public void logDetection(TGPlayer player, String source) {
        if (!enabled()) return;
        TGPlatform.getInstance().getLogger().info(String.format(
                "[TotemGuard-Bedrock] player=%s detected as Bedrock via %s (device=%s input=%s)",
                player.getName(), source, player.getBedrockDeviceOs(), player.getBedrockInputMode()));
    }

    public void logNotDetected(TGPlayer player, String reason) {
        if (!enabled()) return;
        TGPlatform.getInstance().getLogger().info(String.format(
                "[TotemGuard-Bedrock] player=%s not detected as Bedrock (%s)",
                player.getName(), reason));
    }

    private boolean enabled() {
        return TGPlatform.getInstance().getConfigRepository().bedrock().isDebugLoggingEnabled();
    }
}
