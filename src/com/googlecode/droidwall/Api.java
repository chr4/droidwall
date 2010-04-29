/**
 * Contains shared programming interfaces.
 * All iptables "communication" is handled by this class.
 * 
 * Copyright (C) 2009-2010  Rodrigo Zechin Rosauro
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
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
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
	public static final String VERSION = "1.3.8-dev";
	
	// Preferences
	public static final String PREFS_NAME 		= "DroidWallPrefs";
	public static final String PREF_3G_UIDS		= "AllowedUids3G";
	public static final String PREF_WIFI_UIDS	= "AllowedUidsWifi";
	public static final String PREF_PASSWORD 	= "Password";
	public static final String PREF_MODE 		= "BlockMode";
	public static final String PREF_ENABLED		= "Enabled";
	// Modes
	public static final String MODE_WHITELIST = "whitelist";
	public static final String MODE_BLACKLIST = "blacklist";
	// Messages
	public static final String STATUS_CHANGED_MSG 	= "com.googlecode.droidwall.intent.action.STATUS_CHANGED";
	public static final String TOGGLE_REQUEST_MSG	= "com.googlecode.droidwall.intent.action.TOGGLE_REQUEST";
	public static final String STATUS_EXTRA			= "com.googlecode.droidwall.intent.extra.STATUS";
	
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
     * Purge and re-add all rules (internal implementation).
     * @param ctx application context (mandatory)
     * @param uidsWifi list of selected UIDs for WIFI to allow or disallow (depending on the working mode)
     * @param uids3g list of selected UIDs for 2G/3G to allow or disallow (depending on the working mode)
     * @param showErrors indicates if errors should be alerted
     */
	private static boolean applyIptablesRulesImpl(Context ctx, List<Integer> uidsWifi, List<Integer> uids3g, boolean showErrors) {
		if (ctx == null) {
			return false;
		}
		final String ITFS_WIFI[] = {"tiwlan+","eth+"};
		final String ITFS_3G[] = {"rmnet+","pdp+","ppp+"};
		final SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, 0);
		final boolean whitelist = prefs.getString(PREF_MODE, MODE_WHITELIST).equals(MODE_WHITELIST);

    	final StringBuilder script = new StringBuilder();
		try {
			int code;
			script.append("iptables -F || exit\n");
			final String targetRule = (whitelist ? "ACCEPT" : "REJECT");
			if (whitelist) {
				// When "white listing" wifi, we need ensure that the dhcp and wifi users are allowed
				int uid = android.os.Process.getUidForName("dhcp");
				if (uid != -1) {
					for (final String itf : ITFS_WIFI) {
						script.append("iptables -A OUTPUT -o ").append(itf).append(" -m owner --uid-owner ").append(uid).append(" -j ACCEPT || exit\n");
					}
				}
				uid = android.os.Process.getUidForName("wifi");
				if (uid != -1) {
					for (final String itf : ITFS_WIFI) {
						script.append("iptables -A OUTPUT -o ").append(itf).append(" -m owner --uid-owner ").append(uid).append(" -j ACCEPT || exit\n");
					}
				}
			}
			for (final Integer uid : uids3g) {
				for (final String itf : ITFS_3G) {
					script.append("iptables -A OUTPUT -o ").append(itf).append(" -m owner --uid-owner ").append(uid).append(" -j ").append(targetRule).append(" || exit\n");
				}
			}
			for (final Integer uid : uidsWifi) {
				for (final String itf : ITFS_WIFI) {
					script.append("iptables -A OUTPUT -o ").append(itf).append(" -m owner --uid-owner ").append(uid).append(" -j ").append(targetRule).append(" || exit\n");
				}
			}
			if (whitelist) {
				for (final String itf : ITFS_3G) {
					script.append("iptables -A OUTPUT -o ").append(itf).append(" -j REJECT || exit\n");
				}
				for (final String itf : ITFS_WIFI) {
					script.append("iptables -A OUTPUT -o ").append(itf).append(" -j REJECT || exit\n");
				}
			}
	    	final StringBuilder res = new StringBuilder();
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
     * Purge and re-add all saved rules (not in-memory ones).
     * This is much faster than just calling "applyIptablesRules", since it don't need to read installed applications.
     * @param ctx application context (mandatory)
     * @param showErrors indicates if errors should be alerted
     */
	public static boolean applySavedIptablesRules(Context ctx, boolean showErrors) {
		if (ctx == null) {
			return false;
		}
		final SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, 0);
		final String savedUids_wifi = prefs.getString(PREF_WIFI_UIDS, "");
		final String savedUids_3g = prefs.getString(PREF_3G_UIDS, "");
		final List<Integer> uids_wifi = new LinkedList<Integer>();
		if (savedUids_wifi.length() > 0) {
			// Check which applications are allowed on wifi
			final StringTokenizer tok = new StringTokenizer(savedUids_wifi, "|");
			while (tok.hasMoreTokens()) {
				final String uid = tok.nextToken();
				if (!uid.equals("")) {
					try {
						uids_wifi.add(Integer.parseInt(uid));
					} catch (Exception ex) {
					}
				}
			}
		}
		final List<Integer> uids_3g = new LinkedList<Integer>();
		if (savedUids_3g.length() > 0) {
			// Check which applications are allowed on 2G/3G
			final StringTokenizer tok = new StringTokenizer(savedUids_3g, "|");
			while (tok.hasMoreTokens()) {
				final String uid = tok.nextToken();
				if (!uid.equals("")) {
					try {
						uids_3g.add(Integer.parseInt(uid));
					} catch (Exception ex) {
					}
				}
			}
		}
		return applyIptablesRulesImpl(ctx, uids_wifi, uids_3g, showErrors);
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
		saveRules(ctx);
		return applySavedIptablesRules(ctx, showErrors);
    }
	
	/**
	 * Save current rules using the preferences storage.
	 * @param ctx application context (mandatory)
	 */
	public static void saveRules(Context ctx) {
		final SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, 0);
		final DroidApp[] apps = getApps(ctx);
		// Builds a pipe-separated list of names
		final StringBuilder newuids_wifi = new StringBuilder();
		final StringBuilder newuids_3g = new StringBuilder();
		for (int i=0; i<apps.length; i++) {
			if (apps[i].selected_wifi) {
				if (newuids_wifi.length() != 0) newuids_wifi.append('|');
				newuids_wifi.append(apps[i].uid);
			}
			if (apps[i].selected_3g) {
				if (newuids_3g.length() != 0) newuids_3g.append('|');
				newuids_3g.append(apps[i].uid);
			}
		}
		// save the new list of UIDs
		final Editor edit = prefs.edit();
		edit.putString(PREF_WIFI_UIDS, newuids_wifi.toString());
		edit.putString(PREF_3G_UIDS, newuids_3g.toString());
		edit.commit();
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
		final SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, 0);
		// allowed application names separated by pipe '|' (persisted)
		final String savedUids_wifi = prefs.getString(PREF_WIFI_UIDS, "");
		final String savedUids_3g = prefs.getString(PREF_3G_UIDS, "");
		int selected_wifi[] = new int[0];
		int selected_3g[] = new int[0];
		if (savedUids_wifi.length() > 0) {
			// Check which applications are allowed
			final StringTokenizer tok = new StringTokenizer(savedUids_wifi, "|");
			selected_wifi = new int[tok.countTokens()];
			for (int i=0; i<selected_wifi.length; i++) {
				final String uid = tok.nextToken();
				if (!uid.equals("")) {
					try {
						selected_wifi[i] = Integer.parseInt(uid);
					} catch (Exception ex) {
						selected_wifi[i] = -1;
					}
				}
			}
			// Sort the array to allow using "Arrays.binarySearch" later
			Arrays.sort(selected_wifi);
		}
		if (savedUids_3g.length() > 0) {
			// Check which applications are allowed
			final StringTokenizer tok = new StringTokenizer(savedUids_3g, "|");
			selected_3g = new int[tok.countTokens()];
			for (int i=0; i<selected_3g.length; i++) {
				final String uid = tok.nextToken();
				if (!uid.equals("")) {
					try {
						selected_3g[i] = Integer.parseInt(uid);
					} catch (Exception ex) {
						selected_3g[i] = -1;
					}
				}
			}
			// Sort the array to allow using "Arrays.binarySearch" later
			Arrays.sort(selected_3g);
		}
		try {
			final PackageManager pkgmanager = ctx.getPackageManager();
			final List<ApplicationInfo> installed = pkgmanager.getInstalledApplications(0);
			final HashMap<Integer, DroidApp> map = new HashMap<Integer, DroidApp>();
			String name;
			DroidApp app;
			for (final ApplicationInfo apinfo : installed) {
				app = map.get(apinfo.uid);
				// filter applications which are not allowed to access the Internet
				if (app == null && PackageManager.PERMISSION_GRANTED != pkgmanager.checkPermission(Manifest.permission.INTERNET, apinfo.packageName)) {
					continue;
				}
				name = pkgmanager.getApplicationLabel(apinfo).toString();
				// Check for the tethering application (which causes conflicts with Droid Wall)
				if (apinfo.packageName.equals("android.tether")) {
					hastether = name;
				}
				if (app == null) {
					app = new DroidApp();
					app.uid = apinfo.uid;
					app.names = new String[] { name };
					map.put(apinfo.uid, app);
				} else {
					final String newnames[] = new String[app.names.length + 1];
					System.arraycopy(app.names, 0, newnames, 0, app.names.length);
					newnames[app.names.length] = name;
					app.names = newnames;
				}
				// check if this application is selected
				if (!app.selected_wifi && Arrays.binarySearch(selected_wifi, app.uid) >= 0) {
					app.selected_wifi = true;
				}
				if (!app.selected_3g && Arrays.binarySearch(selected_3g, app.uid) >= 0) {
					app.selected_3g = true;
				}
			}
			/* add special applications to the list */
			final DroidApp special[] = {
				new DroidApp(android.os.Process.getUidForName("root"), "(Applications running as root)", false, false),
				new DroidApp(android.os.Process.getUidForName("media"), "Media server", false, false),
			};
			for (int i=0; i<special.length; i++) {
				app = special[i];
				if (app.uid != -1 && !map.containsKey(app.uid)) {
					// check if this application is allowed
					if (Arrays.binarySearch(selected_wifi, app.uid) >= 0) {
						app.selected_wifi = true;
					}
					if (Arrays.binarySearch(selected_3g, app.uid) >= 0) {
						app.selected_3g = true;
					}
					map.put(app.uid, app);
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
			if (runScriptAsRoot("exit 0", null, 20000) == 0) {
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
		return runScriptAsRoot(script, res, 15000);
	}

	public static boolean isEnabled(Context ctx) {
		if (ctx == null) return false;
		return ctx.getSharedPreferences(PREFS_NAME, 0).getBoolean(PREF_ENABLED, true);
	}
	
	public static void setEnabled(Context ctx, boolean enabled) {
		if (ctx == null) return;
		final SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, 0);
		if (prefs.getBoolean(PREF_ENABLED, true) == enabled) {
			return;
		}
		final Editor edit = prefs.edit();
		edit.putBoolean(PREF_ENABLED, enabled);
		edit.commit();
		/* notify */
		final Intent message = new Intent(Api.STATUS_CHANGED_MSG);
        message.putExtra(Api.STATUS_EXTRA, enabled);
        ctx.sendBroadcast(message);
	}

    /**
     * Small structure to hold an application info
     */
	public static final class DroidApp {
		/** linux user id */
    	int uid;
    	/** application names belonging to this user id */
    	String names[];
    	/** indicates if this application is selected for wifi */
    	boolean selected_wifi;
    	/** indicates if this application is selected for 3g */
    	boolean selected_3g;
    	/** toString cache */
    	String tostr;
    	
    	public DroidApp() {
    	}
    	public DroidApp(int uid, String name, boolean selected_wifi, boolean selected_3g) {
    		this.uid = uid;
    		this.names = new String[] {name};
    		this.selected_wifi = selected_wifi;
    		this.selected_3g = selected_3g;
    	}
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
