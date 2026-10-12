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

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.watabou.utils.PlatformSupport;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebPlatformSupport extends PlatformSupport {

	@Override
	public void updateDisplaySize() {
		//the canvas always fills the browser window
	}

	@Override
	public void updateSystemUI() {
		//no system UI to manage in a browser
	}

	@Override
	public boolean connectedToUnmeteredNetwork() {
		return true;
	}

	@Override
	public boolean supportsVibration() {
		return false;
	}

	@Override
	public boolean openURI( String uri ){
		return Gdx.net.openURI(uri);
	}

	/* FONT SUPPORT */

	//custom pixel font, for use with Latin and Cyrillic languages
	private static FreeTypeFontGenerator basicFontGenerator;
	//droid sans fallback, for asian fonts
	private static FreeTypeFontGenerator asianFontGenerator;

	@Override
	public void setupFontGenerators(int pageSize, boolean systemfont) {
		//don't bother doing anything if nothing has changed
		if (fonts != null && this.pageSize == pageSize && this.systemfont == systemfont){
			return;
		}
		this.pageSize = pageSize;
		this.systemfont = systemfont;

		resetGenerators(false);
		fonts = new HashMap<>();

		if (systemfont) {
			basicFontGenerator = asianFontGenerator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/droid_sans.ttf"));
		} else {
			basicFontGenerator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/pixel_font.ttf"));
			asianFontGenerator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/droid_sans.ttf"));
		}

		fonts.put(basicFontGenerator, new HashMap<>());
		fonts.put(asianFontGenerator, new HashMap<>());

		packer = new PixmapPacker(pageSize, pageSize, Pixmap.Format.RGBA8888, 1, false);
	}

	//TeaVM's regex engine doesn't support unicode block names (e.g. \p{InHiragana}), so ranges are used instead
	private static Matcher asianMatcher = Pattern.compile("[\\uAC00-\\uD7AF]|" +
			"[\\u4E00-\\u9FFF]|[\\u3000-\\u303F]|[\\uFF00-\\uFFEF]|" +
			"[\\u3040-\\u309F]|[\\u30A0-\\u30FF]").matcher("");

	@Override
	protected FreeTypeFontGenerator getGeneratorForString( String input ){
		if (asianMatcher.reset(input).find()){
			return asianFontGenerator;
		} else {
			return basicFontGenerator;
		}
	}

	//splits on newline (for layout), chinese/japanese (for font choice), and '_'/'**' (for highlighting)
	private Pattern regularsplitter = Pattern.compile(
			"(?<=\n)|(?=\n)|(?<=_)|(?=_)|(?<=\\*\\*)|(?=\\*\\*)|" +
					"(?<=[\\u3040-\\u309F])|(?=[\\u3040-\\u309F])|" +
					"(?<=[\\u30A0-\\u30FF])|(?=[\\u30A0-\\u30FF])|" +
					"(?<=[\\u4E00-\\u9FFF])|(?=[\\u4E00-\\u9FFF])|" +
					"(?<=[\\u3000-\\u303F])|(?=[\\u3000-\\u303F])");

	//additionally splits on spaces, so that each word can be laid out individually
	private Pattern regularsplitterMultiline = Pattern.compile(
			"(?<= )|(?= )|(?<=\n)|(?=\n)|(?<=_)|(?=_)|(?<=\\*\\*)|(?=\\*\\*)|" +
					"(?<=[\\u3040-\\u309F])|(?=[\\u3040-\\u309F])|" +
					"(?<=[\\u30A0-\\u30FF])|(?=[\\u30A0-\\u30FF])|" +
					"(?<=[\\u4E00-\\u9FFF])|(?=[\\u4E00-\\u9FFF])|" +
					"(?<=[\\u3000-\\u303F])|(?=[\\u3000-\\u303F])");

	@Override
	public String[] splitforTextBlock(String text, boolean multiline) {
		if (multiline) {
			return regularsplitterMultiline.split(text);
		} else {
			return regularsplitter.split(text);
		}
	}
}
