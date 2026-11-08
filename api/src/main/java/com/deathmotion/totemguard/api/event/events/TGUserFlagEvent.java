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

package com.deathmotion.totemguard.api.event.events;

/**
 * Fired when a check is about to flag a {@link com.deathmotion.totemguard.api.user.TGUser}.
 *
 * <p>Dispatched synchronously on the player's packet thread, before
 * {@link com.deathmotion.totemguard.api.check.Check#getViolations()} increases.
 * Cancelling suppresses the violation increment, alert dispatch, and punishment
 * evaluation for this flag only. A later flag fires a new event.</p>
 *
 * <p>Handlers run on the packet thread. Read state you already hold and return.
 * Server API calls and blocking work stall that player's packets.</p>
 */
public interface TGUserFlagEvent extends TGCheckEvent {
}
