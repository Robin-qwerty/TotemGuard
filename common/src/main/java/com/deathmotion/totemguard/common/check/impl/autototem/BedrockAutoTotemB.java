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

package com.deathmotion.totemguard.common.check.impl.autototem;

import com.deathmotion.totemguard.api.check.CheckType;
import com.deathmotion.totemguard.common.check.HeuristicCheck;
import com.deathmotion.totemguard.common.check.annotations.CheckData;
import com.deathmotion.totemguard.common.check.type.EventCheck;
import com.deathmotion.totemguard.common.config.view.BedrockView;
import com.deathmotion.totemguard.common.features.bedrock.BedrockDebugLogger;
import com.deathmotion.totemguard.common.player.TGPlayer;
import com.deathmotion.totemguard.common.util.MathUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@CheckData(description = "Suspicious Bedrock totem delay consistency", type = CheckType.AUTO_TOTEM, experimental = true)
public class BedrockAutoTotemB extends HeuristicCheck implements EventCheck {

    public BedrockAutoTotemB(TGPlayer player) {
        super(player);
    }

    @Override
    protected double flagThreshold() {
        return 4.0;
    }

    @Override
    protected double decayPerSecond() {
        return 0;
    }

    @Override
    public void onTotemReplenished(long totemActivatedTimestamp,
                                   long totemReplenishedTimestamp,
                                   @Nullable Long totemPickupTimestamp) {
        if (!player.isBedrockPlayer()) return;

        BedrockView bedrock = platform.getConfigRepository().bedrock();
        if (!bedrock.isEnabled()) return;

        int sampleSize = bedrock.getInt(getName(), "sample-size", 5);
        List<Long> recent = player.getTotemData().getIntervals().getLast(sampleSize);
        if (recent.size() < sampleSize) return;

        double stdDev = MathUtil.getStandardDeviation(recent);
        String stdDevText = String.format("%.2f", stdDev);

        double verySuspicious = bedrock.getDouble(getName(), "very-suspicious-stddev-ms", 3.0);
        double suspicious = bedrock.getDouble(getName(), "suspicious-stddev-ms", 10.0);
        double legitimate = bedrock.getDouble(getName(), "legitimate-stddev-ms", 40.0);

        String outcome;
        if (stdDev < verySuspicious) {
            punish(3.0, "stdDev={0}", stdDevText);
            outcome = "very-suspicious";
        } else if (stdDev < suspicious) {
            punish(2.0, "stdDev={0}", stdDevText);
            outcome = "suspicious";
        } else if (stdDev > legitimate) {
            reward(1.0);
            outcome = "reward";
        } else {
            outcome = "neutral";
        }

        BedrockDebugLogger.logConsistency(player, stdDev, outcome);
    }
}
