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

package com.shatteredpixel.shatteredpixeldungeon;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroSubClass;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.watabou.utils.Bundle;
import com.watabou.utils.DeviceCompat;
import com.watabou.utils.FileUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

public class GamesInProgress {
	
	public static final int MAX_SLOTS = HeroClass.values().length;
	
	//null means we have loaded info and it is empty, no entry means unknown.
	private static HashMap<Integer, Info> slotStates = new HashMap<>();
	public static int curSlot;
	
	public static HeroClass selectedClass;
	public static boolean randomizedClass = false;
	
	private static final String GAME_FOLDER = "game%d";
	private static final String GAME_FILE	= "game.dat";
	private static final String DEPTH_FILE	= "depth%d.dat";
	private static final String DEPTH_BRANCH_FILE	= "depth%d-branch%d.dat";
	
	public static boolean gameExists( int slot ){
		return FileUtils.dirExists(gameFolder(slot))
				&& FileUtils.fileLength(gameFile(slot)) > 1;
	}
	
	public static String gameFolder( int slot ){
		return Messages.format(GAME_FOLDER, slot);
	}
	
	public static String gameFile( int slot ){
		return gameFolder(slot) + "/" + GAME_FILE;
	}
	
	public static String depthFile( int slot, int depth, int branch ) {
		if (branch == 0) {
			return gameFolder(slot) + "/" + Messages.format(DEPTH_FILE, depth);
		} else {
			return gameFolder(slot) + "/" + Messages.format(DEPTH_BRANCH_FILE, depth, branch);
		}
	}
	
	// Backups

	//On web, saves live in browser storage and can be lost if the page closes mid-write or the game crashes,
	// so a copy of each game is kept from the start of the current floor, and restored if the game can't be read.
	private static final String BACKUP_FOLDER = "game%d_backup";

	public static boolean restoredFromBackup = false;

	public static String backupFolder( int slot ){
		return Messages.format(BACKUP_FOLDER, slot);
	}

	public static void backupGame( int slot ){
		if (!DeviceCompat.isWeb() || !gameExists( slot )) return;
		try {
			copyFolder( gameFolder( slot ), backupFolder( slot ) );
		} catch (Exception e){
			ShatteredPixelDungeon.reportException( e );
		}
	}

	//restores the backup over the main save. The backup is removed afterward, so a bad backup can't be restored repeatedly
	public static boolean restoreBackup( int slot ){
		if (!DeviceCompat.isWeb() || !FileUtils.fileExists( backupFolder( slot ) + "/" + GAME_FILE )) return false;
		try {
			copyFolder( backupFolder( slot ), gameFolder( slot ) );
			deleteBackup( slot );
			slotStates.remove( slot );
			restoredFromBackup = true;
			return true;
		} catch (Exception e){
			ShatteredPixelDungeon.reportException( e );
			return false;
		}
	}

	public static void deleteBackup( int slot ){
		for (String file : FileUtils.filesInDir( backupFolder( slot ) )){
			FileUtils.deleteFile( backupFolder( slot ) + "/" + file );
		}
	}

	//replaces the contents of one game folder with another's
	private static void copyFolder( String from, String to ){
		for (String file : FileUtils.filesInDir( to )){
			FileUtils.deleteFile( to + "/" + file );
		}
		for (String file : FileUtils.filesInDir( from )){
			byte[] data = FileUtils.getFileHandle( from + "/" + file ).readBytes();
			FileUtils.getFileHandle( to + "/" + file ).writeBytes( data, false );
		}
	}
	
	public static int firstEmpty(){
		for (int i = 1; i <= MAX_SLOTS; i++){
			if (check(i) == null) return i;
		}
		return -1;
	}
	
	public static ArrayList<Info> checkAll(){
		ArrayList<Info> result = new ArrayList<>();
		for (int i = 1; i <= MAX_SLOTS; i++){
			Info curr = check(i);
			if (curr != null) result.add(curr);
		}
		switch (SPDSettings.gamesInProgressSort()){
			case "level": default:
				Collections.sort(result, levelComparator);
				break;
			case "last_played":
				Collections.sort(result, lastPlayedComparator);
				break;
		}

		return result;
	}
	
	public static Info check( int slot ) {
		
		if (slotStates.containsKey( slot )) {
			
			return slotStates.get( slot );
			
		} else if (!gameExists( slot )) {

			//the main save is missing or damaged, try the backup before giving up on it
			if (restoreBackup( slot )) {
				return check( slot );
			}
			
			slotStates.put(slot, null);
			return null;
			
		} else {
			
			Info info;
			try {
				
				Bundle bundle = FileUtils.bundleFromFile(gameFile(slot));

				if (bundle.getInt( "version" ) < ShatteredPixelDungeon.v3_1_1) {
					info = null;
				} else {

					info = new Info();
					info.slot = slot;
					Dungeon.preview(info, bundle);
				}

			} catch (IOException e) {
				info = null;
			} catch (Exception e){
				ShatteredPixelDungeon.reportException( e );
				info = null;
			}

			//the main save couldn't be read, try the backup before giving up on it
			if (info == null && restoreBackup( slot )) {
				return check( slot );
			}
			
			slotStates.put( slot, info );
			return info;
			
		}
	}

	public static void set(int slot) {
		Info info = new Info();
		info.slot = slot;

		info.lastPlayed = Dungeon.lastPlayed;
		
		info.depth = Dungeon.depth;
		info.challenges = Dungeon.challenges;

		info.seed = Dungeon.seed;
		info.customSeed = Dungeon.customSeedText;
		info.daily = Dungeon.daily;
		info.dailyReplay = Dungeon.dailyReplay;
		
		info.level = Dungeon.hero.lvl;
		info.str = Dungeon.hero.STR;
		info.strBonus = Dungeon.hero.STR() - Dungeon.hero.STR;
		info.exp = Dungeon.hero.exp;
		info.hp = Dungeon.hero.HP;
		info.ht = Dungeon.hero.HT;
		info.shld = Dungeon.hero.shielding();
		info.heroClass = Dungeon.hero.heroClass;
		info.subClass = Dungeon.hero.subClass;
		info.armorTier = Dungeon.hero.tier();
		
		info.goldCollected = Statistics.goldCollected;
		info.maxDepth = Statistics.deepestFloor;

		slotStates.put( slot, info );
	}
	
	public static void setUnknown( int slot ) {
		slotStates.remove( slot );
	}
	
	public static void delete( int slot ) {
		slotStates.put( slot, null );
	}
	
	public static class Info {
		public int slot;

		public int depth;
		public int version;
		public int challenges;

		public long seed;
		public String customSeed;
		public boolean daily;
		public boolean dailyReplay;
		public long lastPlayed;

		public int level;
		public int str;
		public int strBonus;
		public int exp;
		public int hp;
		public int ht;
		public int shld;
		public HeroClass heroClass;
		public HeroSubClass subClass;
		public int armorTier;
		
		public int goldCollected;
		public int maxDepth;
	}
	
	public static final Comparator<GamesInProgress.Info> levelComparator = new Comparator<GamesInProgress.Info>() {
		@Override
		public int compare(GamesInProgress.Info lhs, GamesInProgress.Info rhs ) {
			if (rhs.level != lhs.level){
				return (int)Math.signum( rhs.level - lhs.level );
			} else {
				return lastPlayedComparator.compare(lhs, rhs);
			}
		}
	};

	public static final Comparator<GamesInProgress.Info> lastPlayedComparator = new Comparator<GamesInProgress.Info>() {
		@Override
		public int compare(GamesInProgress.Info lhs, GamesInProgress.Info rhs ) {
			return (int)Math.signum( rhs.lastPlayed - lhs.lastPlayed );
		}
	};
}
