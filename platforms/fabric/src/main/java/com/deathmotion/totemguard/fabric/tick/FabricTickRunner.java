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

package com.deathmotion.totemguard.fabric.tick;

import com.deathmotion.totemguard.common.TGPlatform;
import com.deathmotion.totemguard.fabric.FabricServerHolder;
import com.deathmotion.totemguard.host.RuntimeDispatch;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.logging.Level;

public final class FabricTickRunner {

    private static volatile FabricTickRunner active;

    private final TGPlatform platform;
    private final ArrayDeque<Delayed> delayed = new ArrayDeque<>();

    public FabricTickRunner(TGPlatform platform) {
        this.platform = platform;
    }

    public static void runLaterTicks(int ticks, @NotNull Runnable task) {
        FabricTickRunner runner = active;
        if (runner == null) return;
        int delay = Math.max(1, ticks);
        MinecraftServer server = FabricServerHolder.server();
        Runnable enqueue = () -> runner.delayed.addLast(new Delayed(delay, task));
        if (server != null && !server.isSameThread()) server.execute(enqueue);
        else enqueue.run();
    }

    public void start() {
        active = this;
        RuntimeDispatch.setServerTickHandler(this::onServerTick);
    }

    public void stop() {
        if (active == this) active = null;
        delayed.clear();
        RuntimeDispatch.clearServerTickHandler();
    }

    private void onServerTick() {
        if (!platform.isEnabled()) return;
        drainDelayed();
        MinecraftServer server = FabricServerHolder.server();
        if (server != null) {
            FabricNetherPortalTracker.markNetherPortalContacts(platform, server);
        }
        platform.tickPlayers();
    }

    private void drainDelayed() {
        int pending = delayed.size();
        for (int i = 0; i < pending; i++) {
            Delayed next = delayed.pollFirst();
            if (next == null) return;
            if (--next.remaining > 0) {
                delayed.addLast(next);
                continue;
            }
            try {
                next.task.run();
            } catch (Throwable t) {
                platform.getLogger().log(Level.WARNING, "Delayed task failed", t);
            }
        }
    }

    private static final class Delayed {
        private int remaining;
        private final Runnable task;

        private Delayed(int remaining, Runnable task) {
            this.remaining = remaining;
            this.task = task;
        }
    }
}
