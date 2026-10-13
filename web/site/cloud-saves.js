// Cloud copies of game saves, for the claude.ai hosted version of the game.
//
// The game keeps its saves in this browser's IndexedDB, which the browser can clear.
// When the page runs on claude.ai with the `db` capability, every save file (and the
// game's settings) is mirrored to the viewer's private area of the artifact's database,
// and copied back down when the page opens with empty browser storage.
//
// This file must load before app.js, so it sees every write the game makes.
(function () {
	"use strict";

	var DB_NAME = "shpd-saves";       // WebApplicationConfiguration.localStoragePrefix
	var STORE = "FILE_DATA";          // the web backend's IndexedDB object store
	var PREF_PREFIX = "pref:shpd_";   // WebApplicationConfiguration.storagePrefix, as stored by WebPreferences
	var PREFS_DOC = "prefs";
	var PART_CHARS = 200000;          // documents are limited to 256 KiB, so large files are split
	var SYNC_DELAY = 3000;
	var RETRY_DELAY = 30000;
	var STARTUP_TIMEOUT = 15000;

	var folder = null;        // the viewer's private collection, once cloud saves are available
	var cloudFiles = {};      // file path -> {id, sum, parts} of what is in the cloud
	var dirty = {};           // file paths written or deleted since the last sync
	var prefsDirty = false;
	var restoring = false;    // true while this script writes to storage itself
	var timer = null;
	var syncing = false;

	function log(msg, e) {
		if (e) console.warn("[cloud saves] " + msg, e); else console.log("[cloud saves] " + msg);
	}

	// ---- watch storage writes ----

	function isSaveStore(store) {
		try { return store.name === STORE && store.transaction.db.name === DB_NAME; } catch (e) { return false; }
	}

	var origPut = IDBObjectStore.prototype.put;
	var origDelete = IDBObjectStore.prototype.delete;
	IDBObjectStore.prototype.put = function (value, key) {
		if (!restoring && isSaveStore(this)) markDirty(key);
		return origPut.apply(this, arguments);
	};
	IDBObjectStore.prototype.delete = function (key) {
		if (!restoring && isSaveStore(this)) markDirty(key);
		return origDelete.apply(this, arguments);
	};

	function isPrefsStorage(storage, key) {
		try { return storage === window.localStorage && String(key).indexOf(PREF_PREFIX) === 0; } catch (e) { return false; }
	}

	var origSetItem = Storage.prototype.setItem;
	var origRemoveItem = Storage.prototype.removeItem;
	Storage.prototype.setItem = function (key) {
		if (!restoring && isPrefsStorage(this, key)) { prefsDirty = true; schedule(SYNC_DELAY); }
		return origSetItem.apply(this, arguments);
	};
	Storage.prototype.removeItem = function (key) {
		if (!restoring && isPrefsStorage(this, key)) { prefsDirty = true; schedule(SYNC_DELAY); }
		return origRemoveItem.apply(this, arguments);
	};

	function markDirty(key) {
		dirty[String(key)] = true;
		schedule(SYNC_DELAY);
	}

	function schedule(delay) {
		if (!folder) return; // kept dirty until cloud saves start
		if (timer) clearTimeout(timer);
		timer = setTimeout(sync, delay);
	}

	// flush as soon as the page is hidden, it may be about to close
	document.addEventListener("visibilitychange", function () {
		if (document.visibilityState === "hidden") schedule(0);
	});

	// ---- IndexedDB helpers ----

	function req(r) {
		return new Promise(function (resolve, reject) {
			r.onsuccess = function () { resolve(r.result); };
			r.onerror = function () { reject(r.error); };
		});
	}

	var idbPromise = null;
	function openIdb() {
		if (!idbPromise) {
			var r = indexedDB.open(DB_NAME, 1);
			// same store the web backend creates, in case this runs first
			r.onupgradeneeded = function () { r.result.createObjectStore(STORE); };
			idbPromise = req(r);
		}
		return idbPromise;
	}

	function store(idb, mode) {
		return idb.transaction(STORE, mode).objectStore(STORE);
	}

	// ---- encoding: gzip + base64 ----

	function bytesOf(contents) {
		if (!contents) return null;
		if (ArrayBuffer.isView(contents)) return new Uint8Array(contents.buffer, contents.byteOffset, contents.byteLength);
		return new Uint8Array(contents);
	}

	function checksum(bytes) {
		// FNV-1a, to skip uploading files that have not changed
		var h = 0x811c9dc5;
		for (var i = 0; i < bytes.length; i++) {
			h ^= bytes[i];
			h = Math.imul(h, 0x01000193);
		}
		return bytes.length + ":" + (h >>> 0).toString(16);
	}

	async function pipe(bytes, transform) {
		var stream = new Blob([bytes]).stream().pipeThrough(transform);
		return new Uint8Array(await new Response(stream).arrayBuffer());
	}

	function toBase64(bytes) {
		var s = "";
		for (var i = 0; i < bytes.length; i += 0x8000) {
			s += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000));
		}
		return btoa(s);
	}

	function fromBase64(text) {
		var s = atob(text);
		var bytes = new Uint8Array(s.length);
		for (var i = 0; i < s.length; i++) bytes[i] = s.charCodeAt(i);
		return bytes;
	}

	// document ids allow only letters, digits and _ - . ~ : @ +
	function docId(path) {
		return "f" + path.replace(/[^A-Za-z0-9_.:@-]/g, function (c) {
			return "~" + c.charCodeAt(0).toString(16) + "~";
		});
	}

	// ---- syncing up ----

	async function uploadFile(path, value) {
		var bytes = bytesOf(value.contents);
		var sum = bytes ? checksum(bytes) : "dir";
		var known = cloudFiles[path];
		if (known && known.sum === sum) return;

		var id = docId(path);
		var parts = [""];
		if (bytes) {
			var text = toBase64(await pipe(bytes, new CompressionStream("gzip")));
			parts = [];
			for (var i = 0; i < text.length; i += PART_CHARS) parts.push(text.substring(i, i + PART_CHARS));
			if (parts.length === 0) parts = [""];
		}
		var date = value.date instanceof Date ? value.date.getTime() : Date.now();

		for (var p = 1; p < parts.length; p++) {
			await folder.doc(id + ".p" + p).set({ data: parts[p] });
		}
		await folder.doc(id).set({ path: path, type: value.type, date: date, sum: sum, parts: parts.length, data: parts[0], file: !!bytes });
		if (known) {
			for (var q = parts.length; q < known.parts; q++) await folder.doc(id + ".p" + q).delete();
		}
		cloudFiles[path] = { id: id, sum: sum, parts: parts.length };
	}

	async function deleteFile(path) {
		var known = cloudFiles[path];
		if (!known) return;
		for (var q = 1; q < known.parts; q++) await folder.doc(known.id + ".p" + q).delete();
		await folder.doc(known.id).delete();
		delete cloudFiles[path];
	}

	function collectPrefs() {
		var items = {};
		for (var i = 0; i < localStorage.length; i++) {
			var key = localStorage.key(i);
			if (key && key.indexOf(PREF_PREFIX) === 0) items[key] = localStorage.getItem(key);
		}
		return items;
	}

	async function sync() {
		timer = null;
		if (!folder) return;
		if (syncing) { schedule(SYNC_DELAY); return; }
		syncing = true;
		var failed = false;
		try {
			var idb = await openIdb();
			var paths = Object.keys(dirty);
			dirty = {};
			for (var i = 0; i < paths.length; i++) {
				var path = paths[i];
				try {
					var value = await req(store(idb, "readonly").get(path));
					if (value) await uploadFile(path, value); else await deleteFile(path);
				} catch (e) {
					dirty[path] = true;
					failed = true;
					log("could not back up " + path, e);
				}
			}
			if (prefsDirty) {
				prefsDirty = false;
				try {
					await folder.doc(PREFS_DOC).set({ items: collectPrefs() });
				} catch (e) {
					prefsDirty = true;
					failed = true;
					log("could not back up settings", e);
				}
			}
		} finally {
			syncing = false;
		}
		if (failed) schedule(RETRY_DELAY);
		else if (prefsDirty || Object.keys(dirty).length) schedule(SYNC_DELAY);
	}

	// ---- startup: restore if needed, then start mirroring ----

	async function readCloud(coll) {
		var snap = await coll.limit(1000).get();
		var docs = {};
		snap.docs.forEach(function (d) { docs[d.id] = d.data(); });
		return docs;
	}

	async function restoreFile(idb, docs, id, body) {
		var value = { type: body.type, date: new Date(body.date || Date.now()) };
		if (body.file) {
			var text = body.data || "";
			for (var p = 1; p < (body.parts || 1); p++) {
				var part = docs[id + ".p" + p];
				if (!part) throw new Error("missing part " + p + " of " + body.path);
				text += part.data;
			}
			var bytes = await pipe(fromBase64(text), new DecompressionStream("gzip"));
			value.contents = new Int8Array(bytes.buffer, bytes.byteOffset, bytes.byteLength);
		}
		await req(store(idb, "readwrite").put(value, body.path));
	}

	async function init(isStarted) {
		if (!window.claude || typeof window.claude.use !== "function") return null;
		var caps = await Promise.all([window.claude.use("db"), window.claude.use("user")]);
		var db = caps[0], user = caps[1];
		if (!db || !user) return null;
		var uid = await user.id();
		if (!uid) return null;

		var coll = db.collection("data/users/" + uid);
		var docs = await readCloud(coll);
		var idb = await openIdb();
		var localKeys = await req(store(idb, "readonly").getAllKeys());
		var hasLocalFiles = false;
		for (var i = 0; i < localKeys.length; i++) {
			var v = await req(store(idb, "readonly").get(localKeys[i]));
			if (v && v.contents) { hasLocalFiles = true; break; }
		}

		var restored = 0;
		if (!hasLocalFiles && isStarted()) {
			// the game already started on empty storage (the cloud answered too late), so mirroring now
			// would delete the cloud copies. Leave them alone, they are restored on the next load.
			log("answered after the game started, cloud copies are kept for the next load");
			return null;
		}
		if (!hasLocalFiles) {
			// this browser has no saves: bring them back from the cloud
			restoring = true;
			try {
				for (var id in docs) {
					var body = docs[id];
					if (!body || !body.path) continue;
					try {
						await restoreFile(idb, docs, id, body);
						if (body.file) restored++;
					} catch (e) {
						log("could not restore " + body.path, e);
					}
				}
				var prefs = docs[PREFS_DOC];
				if (prefs && prefs.items) {
					for (var key in prefs.items) {
						if (localStorage.getItem(key) === null) origSetItem.call(localStorage, key, prefs.items[key]);
					}
				}
			} finally {
				restoring = false;
			}
			if (restored) log("restored " + restored + " files");
		}

		// from here on, this browser's storage is the source of truth: record what the cloud holds,
		// then upload anything that differs and remove cloud files this browser no longer has
		for (var docKey in docs) {
			var b = docs[docKey];
			if (b && b.path) cloudFiles[b.path] = { id: docKey, sum: b.sum, parts: b.parts || 1 };
		}
		localKeys = await req(store(idb, "readonly").getAllKeys());
		localKeys.forEach(function (k) { dirty[String(k)] = true; });
		for (var path in cloudFiles) dirty[path] = true;
		prefsDirty = true;
		folder = coll;
		schedule(SYNC_DELAY);
		return restored;
	}

	function showNote(text) {
		var note = document.createElement("div");
		note.textContent = text;
		note.style.cssText = "position:fixed;left:50%;bottom:24px;transform:translateX(-50%);max-width:calc(100% - 32px);" +
			"background:rgba(20,20,20,0.92);color:#e8e2d0;border:1px solid #555;padding:8px 14px;font:14px/1.4 sans-serif;" +
			"z-index:5;pointer-events:none;transition:opacity 1s;";
		document.body.appendChild(note);
		setTimeout(function () { note.style.opacity = "0"; }, 6000);
		setTimeout(function () { note.remove(); }, 7500);
	}

	// starts the game once saves are restored, or after a timeout so the game never waits forever
	window.spdStartWithCloudSaves = function (start) {
		var started = false;
		function begin() {
			if (started) return;
			started = true;
			start();
		}
		var timeout = setTimeout(function () {
			log("cloud saves did not answer in time, starting without them");
			begin();
		}, STARTUP_TIMEOUT);

		init(function () { return started; }).then(function (restored) {
			clearTimeout(timeout);
			begin();
			if (restored) showNote("Your saves were restored from your claude.ai account.");
		}, function (e) {
			clearTimeout(timeout);
			log("cloud saves are unavailable", e);
			begin();
		});
	};
})();
