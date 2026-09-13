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

package com.deathmotion.totemguard.common.config.view;

import com.deathmotion.totemguard.api.config.Config;
import com.deathmotion.totemguard.api.config.ConfigSection;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.Set;

public final class BedrockView {

    private static final boolean DEFAULT_ENABLED = true;
    private static final boolean DEFAULT_FLOODGATE_ENABLED = true;
    private static final boolean DEFAULT_PREFIX_FALLBACK_ENABLED = false;
    private static final String DEFAULT_PREFIX = ".";
    private static final boolean DEFAULT_DEBUG_LOGGING = false;

    private final Config config;
    private final int version;
    private final boolean enabled;
    private final boolean floodgateEnabled;
    private final boolean usernamePrefixFallbackEnabled;
    private final String usernamePrefix;
    private final boolean debugLoggingEnabled;
    private final Set<String> exemptChecks;

    public BedrockView(Config config) {
        this.config = config;
        this.version = config.version();
        this.enabled = config.getBoolean("enabled").orElse(DEFAULT_ENABLED);

        Optional<ConfigSection> detection = config.getSection("detection");
        this.floodgateEnabled = detection.flatMap(s -> s.getSection("floodgate"))
                .flatMap(s -> s.getBoolean("enabled"))
                .orElse(DEFAULT_FLOODGATE_ENABLED);

        Optional<ConfigSection> prefixSection = detection.flatMap(s -> s.getSection("username-prefix-fallback"));
        this.usernamePrefixFallbackEnabled = prefixSection.flatMap(s -> s.getBoolean("enabled")).orElse(DEFAULT_PREFIX_FALLBACK_ENABLED);
        this.usernamePrefix = prefixSection.flatMap(s -> s.getString("prefix")).orElse(DEFAULT_PREFIX);

        this.debugLoggingEnabled = config.getSection("debug-logging")
                .flatMap(s -> s.getBoolean("enabled"))
                .orElse(DEFAULT_DEBUG_LOGGING);

        this.exemptChecks = Set.copyOf(config.getStringList("exempt-checks"));
    }

    public int version() {
        return version;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isFloodgateDetectionEnabled() {
        return floodgateEnabled;
    }

    public boolean isUsernamePrefixFallbackEnabled() {
        return usernamePrefixFallbackEnabled;
    }

    public @NotNull String usernamePrefix() {
        return usernamePrefix;
    }

    public boolean isDebugLoggingEnabled() {
        return debugLoggingEnabled;
    }

    public boolean isCheckExempt(@NotNull String checkName) {
        return exemptChecks.contains(checkName);
    }

    private @NotNull Optional<ConfigSection> checkSection(@NotNull String checkName) {
        return config.getSection("checks").flatMap(s -> s.getSection(checkName));
    }

    public int getInt(@NotNull String checkName, @NotNull String key, int fallback) {
        return checkSection(checkName).flatMap(s -> s.getInt(key)).orElse(fallback);
    }

    public double getDouble(@NotNull String checkName, @NotNull String key, double fallback) {
        return checkSection(checkName)
                .flatMap(s -> s.get(key))
                .filter(v -> v instanceof Number)
                .map(v -> ((Number) v).doubleValue())
                .orElse(fallback);
    }
}
