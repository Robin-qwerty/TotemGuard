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

package com.deathmotion.totemguard.common.config.migration;

public final class MessagesV1ToV2 implements ConfigMigration {

    private static final String OLD_DEFAULT = "%tg_teleport_command%";
    private static final String ALERT_TELEPORT = "/tgalertteleport %tg_player%";

    @Override
    public int fromVersion() {
        return 1;
    }

    @Override
    public int toVersion() {
        return 2;
    }

    @Override
    public void apply(MigrationContext ctx) {
        Object current = ctx.get("alerts.command").orElse(null);
        if (!(current instanceof String command)) return;
        if (!OLD_DEFAULT.equals(command.trim())) return;
        ctx.set("alerts.command", ALERT_TELEPORT);
    }
}
