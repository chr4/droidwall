/**
 * Contains shared programming interfaces.
 * All iptables "communication" is handled by this class.
 * 
 * Copyright (C) 2009  Rodrigo Zechin Rosauro
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
 *
 * @author Rodrigo Zechin Rosauro
 * @version 1.0
 */

package com.googlecode.droidwall;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

/**
 * Contains shared programming interfaces.
 * All iptables "communication" is handled by this class.
 */
public final class Api {
	public static final String VERSION = "1.3.1";
	
	public static final String PREFS_NAME 		= "DroidWallPrefs";
	public static final String PREF_ALLOWEDUIDS = "AllowedUids";
	public static final String PREF_PASSWORD 	= "Password";
	public static final String PREF_MODE 		= "BlockMode";
	public static final String PREF_ITFS 		= "Interfaces";
	
	// Cached applications
	public static DroidApp applications[] = null;
	// Do we have "Wireless Tether for Root Users" installed?
	public static String hastether = null;
	// Do we have root access?
	private static boolean hasroot = false;

    /**
     * Display a simple alert box
     * @param ctx context
     * @param msg message
     */
	public static void alert(Context ctx, CharSequence msg) {
    	if (ctx != null) {
        	new AlertDialog.Builder(ctx)
        	.setNeutralButton(android.R.string.ok, null)
        	.setMessage(msg)
        	.show();
    	}
    }
    
    /**
     * Purge and re-add all rules.
     * @param ctx application context (mandatory)
     * @param showErrors indicates if errors should be alerted
     */
	public static boolean applyIptablesRules(Context ctx, boolean showErrors) {
		if (ctx == null) {
			return false;
		}
		final SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, 0);
		final boolean whitelist = prefs.getString(PREF_MODE, MainActivity.MODE_WHITELIST).equals(MainActivity.MODE_WHITELIST);
		final String itfs = prefs.getString(PREF_ITFS, MainActivity.ITF_3G);
		String itfFilter;
		if (itfs.indexOf("|") != -1) {
			itfFilter = ""; // Block all interfaces
		} else if (itfs.indexOf(MainActivity.ITF_3G) != -1) {
			itfFilter = "-o rmnet+";; // Block all rmnet interfaces
		} else {
			itfFilter = "-o tiwlan+";; // Block all tiwlan interfaces
		}
		final DroidApp[] apps = getApps(ctx);
		// Builds a pipe-separated list of uids
		final StringBuilder newuids = new StringBuilder();
		for (int i=0; i<apps.length; i++) {
			if (apps[i].selected) {
				if (newuids.length() != 0) newuids.append('|');
				newuids.append(apps[i].username);
			}
		}
		// save the new list of uids if necessary
		if (!newuids.toString().equals(prefs.getString(PREF_ALLOWEDUIDS, ""))) {
			Editor edit = prefs.edit();
			edit.putString(PREF_ALLOWEDUIDS, newuids.toString());
			edit.commit();
		}
    	final StringBuilder script = new StringBuilder();
		try {
			int code;
			script.append("iptables -F || exit\n");
			final String targetRule = (whitelist ? "ACCEPT" : "REJECT");
			for (DroidApp app : apps) {
				if (app.selected) {
					script.append("iptables -A OUTPUT " + itfFilter + " -m owner --uid-owner " + app.uid + " -j " + targetRule + " || exit\n");
				}
			}
			if (whitelist) {
				script.append("iptables -A OUTPUT " + itfFilter + " -j REJECT || exit\n");
			}
	    	StringBuilder res = new StringBuilder();
			code = runScriptAsRoot(script.toString(), res);
			if (showErrors && code != 0) {
				String msg = res.toString();
				Log.e("DroidWall", msg);
				// Search for common error messages
				if (msg.indexOf("Couldn't find match `owner'") != -1 || msg.indexOf("no chain/target match") != -1) {
					alert(ctx, "Error applying iptables rules.\nExit code: " + code + "\n\n" +
						"It seems your Linux kernel was not compiled with the netfilter \"owner\" module enabled, which is required for Droid Wall to work properly.\n\n" +
						"You should check if there is an updated version of your Android ROM compiled with this kernel module.");
				} else {
					// Remove unnecessary help message from output
					if (msg.indexOf("\nTry `iptables -h' or 'iptables --help' for more information.") != -1) {
						msg = msg.replace("\nTry `iptables -h' or 'iptables --help' for more information.", "");
					}
					// Try `iptables -h' or 'iptables --help' for more information.
					alert(ctx, "Error applying iptables rules. Exit code: " + code + "\n\n" + msg.trim());
				}
			} else {
				return true;
			}
		} catch (Exception e) {
			if (showErrors) alert(ctx, "error refreshing iptables: " + e);
		}
		return false;
    }
    
    /**
     * Purge all iptables rules.
     * @param ctx context optional context for alert messages
     * @return true if the rules were purged
     */
	public static boolean purgeIptables(Context ctx) {
    	StringBuilder res = new StringBuilder();
		try {
			int code = runScriptAsRoot("iptables -F || exit\n", res);
			if (code != 0) {
				alert(ctx, "error purging iptables. exit code: " + code + "\n" + res);
				return false;
			}
			return true;
		} catch (Exception e) {
			alert(ctx, "error purging iptables: " + e);
			return false;
		}
    }
	
	/**
	 * Display iptables rules output
	 * @param ctx application context
	 */
	public static void showIptablesRules(Context ctx) {
		try {
    		final StringBuilder res = new StringBuilder();
			runScriptAsRoot("iptables -L\n", res);
			alert(ctx, res);
		} catch (Exception e) {
			alert(ctx, "error: " + e);
		}
	}

    /**
     * @param ctx application context (mandatory)
     * @return a list of applications
     */
	public static DroidApp[] getApps(Context ctx) {
		if (applications != null) {
			// return cached instance
			return applications;
		}
		hastether = null;
		// allowed application names separated by pipe '|' (persisted)
		final String savedNames = ctx.getSharedPreferences(PREFS_NAME, 0).getString(PREF_ALLOWEDUIDS, "");
		String allowed[];
		if (savedNames.length() > 0) {
			// Check which applications are allowed
			final StringTokenizer tok = new StringTokenizer(savedNames, "|");
			allowed = new String[tok.countTokens()];
			for (int i=0; i<allowed.length; i++) {
				allowed[i] = tok.nextToken();
			}
			// Sort the array to allow using "Arrays.binarySearch" later
			Arrays.sort(allowed);
		} else {
			allowed = new String[0];
		}
		try {
			final PackageManager pkgmanager = ctx.getPackageManager();
			final List<ApplicationInfo> installed = pkgmanager.getInstalledApplications(0);
			final HashMap<Integer, DroidApp> map = new HashMap<Integer, DroidApp>();
			String name;
			DroidApp app;
			for (final ApplicationInfo apinfo : installed) {
				app = map.get(apinfo.uid);
				name = pkgmanager.getApplicationLabel(apinfo).toString();
				// Check for the tethering application (which causes conflicts with Droid Wall)
				if (apinfo.packageName.equals("android.tether")) {
					hastether = name;
				}
				if (app == null) {
					app = new DroidApp();
					app.uid = apinfo.uid;
					app.username = pkgmanager.getNameForUid(apinfo.uid);
					app.names = new String[] { name };
					map.put(apinfo.uid, app);
				} else {
					final String newnames[] = new String[app.names.length + 1];
					System.arraycopy(app.names, 0, newnames, 0, app.names.length);
					newnames[app.names.length] = name;
					app.names = newnames;
				}
				// check if this application is allowed
				if (!app.selected && Arrays.binarySearch(allowed, app.username) >= 0) {
					app.selected = true;
				}
			}
			applications = new DroidApp[map.size()];
			int index = 0;
			for (DroidApp application : map.values()) applications[index++] = application;
			return applications;
		} catch (Exception e) {
			alert(ctx, "error: " + e);
		}
		return null;
	}
	/**
	 * Check if we have root access
	 * @param ctx optional context to display alert messages
	 * @return boolean true if we have root
	 */
	public static boolean hasRootAccess(Context ctx) {
		if (hasroot) return true;
		try {
			// Run an empty script just to check root access
			if (runScriptAsRoot("", null, 10000) == 0) {
				hasroot = true;
				return true;
			}
		} catch (Exception e) {
		}
		alert(ctx, "Could not acquire root access.\n" +
			"You need a rooted phone to run Droid Wall.\n\n" +
			"If this phone is already rooted, please make sure Droid Wall has enough permissions to execute the \"su\" command.");
		return false;
	}
    /**
     * Runs a script as root (multiple commands separated by "\n").
     * 
     * @param script the script to be executed
     * @param res the script output response (stdout + stderr)
     * @param timeout timeout in milliseconds (-1 for none)
     * @return the script exit code
     */
	public static int runScriptAsRoot(String script, StringBuilder res, final long timeout) {
		final ScriptRunner runner = new ScriptRunner(script, res);
		runner.start();
		try {
			if (timeout > 0) {
				runner.join(timeout);
			} else {
				runner.join();
			}
			if (runner.isAlive()) {
				// Timed-out
				runner.interrupt();
				runner.destroy();
				runner.join(50);
			}
		} catch (InterruptedException ex) {}
		return runner.exitcode;
    }
    /**
     * Runs a script as root (multiple commands separated by "\n") with a default timeout of 5 seconds.
     * 
     * @param script the script to be executed
     * @param res the script output response (stdout + stderr)
     * @param timeout timeout in milliseconds (-1 for none)
     * @return the script exit code
     * @throws IOException on any error executing the script, or writing it to disk
     */
	public static int runScriptAsRoot(String script, StringBuilder res) throws IOException {
		return runScriptAsRoot(script, res, 5000);
	}

    /**
     * Small structure to hold an application info
     */
	public static final class DroidApp {
		/** linux user id */
    	int uid;
    	/** application user name (Android actually uses a package name to identify) */
    	String username;
    	/** application names belonging to this user id */
    	String names[];
    	/** indicates if this application is selected (checked) */
    	boolean selected;
    	/** toString cache */
    	String tostr;
    	
    	/**
    	 * Screen representation of this application
    	 */
    	@Override
    	public String toString() {
    		if (tostr == null) {
        		final StringBuilder s = new StringBuilder(uid + ": ");
        		for (int i=0; i<names.length; i++) {
        			if (i != 0) s.append(", ");
        			s.append(names[i]);
        		}
        		tostr = s.toString();
    		}
    		return tostr;
    	}
    }
	/**
	 * Internal thread used to execute scripts as root.
	 */
	private static final class ScriptRunner extends Thread {
		private final String script;
		private final StringBuilder res;
		public int exitcode = -1;
		private Process exec;
		
		/**
		 * Creates a new script runner.
		 * @param script script to run
		 * @param res response output
		 */
		public ScriptRunner(String script, StringBuilder res) {
			this.script = script;
			this.res = res;
		}
		@Override
		public void run() {
			try {
				// Create the "su" request to run the command
				// note that this will create a shell that we must interact to (using stdin/stdout)
				exec = Runtime.getRuntime().exec("su");
				final OutputStreamWriter out = new OutputStreamWriter(exec.getOutputStream());
				// Write the script to be executed
				out.write(script);
				// Ensure that the last character is an "enter"
				if (!script.endsWith("\n")) out.write("\n");
				out.flush();
				// Terminate the "su" process
				out.write("exit\n");
				out.flush();
				final char buf[] = new char[1024];
				// Consume the "stdout"
				InputStreamReader r = new InputStreamReader(exec.getInputStream());
				int read=0;
				while ((read=r.read(buf)) != -1) {
					if (res != null) res.append(buf, 0, read);
				}
				// Consume the "stderr"
				r = new InputStreamReader(exec.getErrorStream());
				read=0;
				while ((read=r.read(buf)) != -1) {
					if (res != null) res.append(buf, 0, read);
				}
				// get the process exit code
				if (exec != null) this.exitcode = exec.waitFor();
			} catch (InterruptedException ex) {
				if (res != null) res.append("\nOperation timed-out");
			} catch (Exception ex) {
				if (res != null) res.append("\n" + ex);
			} finally {
				destroy();
			}
		}
		/**
		 * Destroy this script runner
		 */
		public synchronized void destroy() {
			if (exec != null) exec.destroy();
			exec = null;
		}
	}
}
