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

package com.watabou.utils;

import com.badlogic.gdx.utils.reflect.ClassReflection;
import com.watabou.noosa.Game;

//wrapper for libGDX reflection
public class Reflection {
	
	public static boolean isMemberClass( Class cls ){
		return ClassReflection.isMemberClass(cls);
	}
	
	public static boolean isStatic( Class cls ){
		if (DeviceCompat.isWeb()){
			//TeaVM doesn't record whether a nested class is static, so it would report none of them as static.
			// Inner (non-static) classes always take their outer instance as a constructor parameter,
			// so a nested class with a no-argument constructor must be static.
			for (java.lang.reflect.Constructor<?> constructor : cls.getDeclaredConstructors()){
				if (constructor.getParameterTypes().length == 0){
					return true;
				}
			}
			return false;
		}
		return ClassReflection.isStaticClass(cls);
	}
	
	public static <T> T newInstance( Class<T> cls ){
		try {
			return ClassReflection.newInstance(cls);
		} catch (Exception e) {
			Game.reportException(e);
			return null;
		}
	}
	
	public static <T> T newInstanceUnhandled( Class<T> cls ) throws Exception {
		return ClassReflection.newInstance(cls);
	}
	
	public static Class forName( String name ){
		try {
			return ClassReflection.forName( name );
		} catch (Exception e) {
			Game.reportException(e);
			return null;
		}
	}
	
	public static Class forNameUnhandled( String name ) throws Exception {
		return ClassReflection.forName( name );
	}
	
}
