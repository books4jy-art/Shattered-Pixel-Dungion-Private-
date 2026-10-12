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

import com.badlogic.gdx.Files;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplicationConfiguration;
import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.watabou.noosa.Game;
import com.watabou.utils.FileUtils;

public class WebLauncher {

	//args are the version name and version code, passed in by the generated web page
	public static void main (String[] args) {

		Game.version = args.length > 0 ? args[0] : "web";
		try {
			Game.versionCode = Integer.parseInt(args[1]);
		} catch (Exception e) {
			Game.versionCode = 0;
		}

		WebApplicationConfiguration config = new WebApplicationConfiguration("canvas");
		//fill the browser window
		config.width = 0;
		config.height = 0;
		config.usePhysicalPixels = true;

		//saves and settings are kept in the browser's IndexedDB / localStorage
		config.storagePrefix = "shpd_";
		config.localStoragePrefix = "shpd-saves";

		//freetype is compiled to JS separately, and must be loaded before any text is rendered
		config.preloadListener = assetLoader -> assetLoader.loadScript("freetype.js");

		//libGDX local files are persisted to IndexedDB by the web backend
		FileUtils.setDefaultFileProperties( Files.FileType.Local, "" );

		new SPDWebApplication(new ShatteredPixelDungeon(new WebPlatformSupport()), config);
	}
}
