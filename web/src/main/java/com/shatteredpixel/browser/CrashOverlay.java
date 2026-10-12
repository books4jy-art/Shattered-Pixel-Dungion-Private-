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

package com.shatteredpixel.browser;

import org.teavm.jso.JSBody;

//kept outside the game's packages, as everything in those is exposed to reflection, which JS methods can't be
public class CrashOverlay {

	@JSBody(params = "details", script =
			"var box = document.createElement('div');" +
			"box.style.cssText = 'position:fixed;inset:0;background:rgba(0,0,0,0.85);color:#e8e2d0;font:14px/1.5 sans-serif;" +
				"display:flex;align-items:center;justify-content:center;padding:16px;z-index:10;';" +
			"var panel = document.createElement('div');" +
			"panel.style.cssText = 'max-width:640px;width:100%;';" +
			"var title = document.createElement('h2');" +
			"title.textContent = 'The game has crashed';" +
			"title.style.cssText = 'margin:0 0 8px;font-size:20px;color:#ffcc4d;';" +
			"var text = document.createElement('p');" +
			"text.textContent = 'Your progress is saved up to your last completed turn. Reload the page to keep playing. If this keeps happening, send the error below to get it fixed.';" +
			"var area = document.createElement('textarea');" +
			"area.readOnly = true; area.value = details;" +
			"area.style.cssText = 'width:100%;height:200px;box-sizing:border-box;font:12px monospace;background:#1b1b1b;color:#e8e2d0;border:1px solid #555;padding:8px;';" +
			"var reload = document.createElement('button');" +
			"reload.textContent = 'Reload';" +
			"reload.style.cssText = 'margin-top:12px;padding:8px 20px;font-size:15px;cursor:pointer;';" +
			"reload.onclick = function(){ location.reload(); };" +
			"panel.append(title, text, area, reload); box.append(panel); document.body.append(box);")
	public static native void show(String details);

}
