/*
 * Pixel Dungeon
 * Copyright (C) 2012-2015 Oleg Dolya
 *
 * Shattered Pixel Dungeon
 * Copyright (C) 2014-2026 Evan Debenham
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package com.shatteredpixel.shatteredpixeldungeon.web;

import com.badlogic.gdx.ApplicationListener;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplication;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplicationConfiguration;
import com.shatteredpixel.browser.CrashOverlay;

//shows crashes on the page, rather than only in the browser console
public class SPDWebApplication extends WebApplication {

	public SPDWebApplication(ApplicationListener listener, WebApplicationConfiguration config) {
		super(listener, config);
	}

	@Override
	protected void onError(Throwable error) {
		super.onError(error);

		StringBuilder details = new StringBuilder();
		Throwable cause = error;
		while (cause != null){
			if (details.length() > 0) details.append("\ncaused by: ");
			details.append(cause.getClass().getName()).append(": ").append(cause.getMessage());
			for (StackTraceElement element : cause.getStackTrace()){
				details.append("\n    at ").append(element);
			}
			cause = cause.getCause();
		}
		CrashOverlay.show(details.toString());
	}
}
