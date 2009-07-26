/**
 *  Copyright (C) 2009  Rodrigo Zechin Rosauro
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Rodrigo Zechin Rosauro
 * @version 1.0
 */

package com.googlecode.droidwall;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;

public class MainActivity extends Activity {
	/** shell file path (using /sqlite_stmt_journals since it is writable and in-memory) */
	private static File SHELL_FILE = new File("/sqlite_stmt_journals/droidwall.sh");
	
	// Menu options
	private static final int MENU_SHOWRULES = 1;
	private static final int MENU_REFRESH = 2;
	private static final int MENU_PURGE = 3;
	
	// Cached applications
	private static Application applications[] = null;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    }
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
    	super.onCreateContextMenu(menu, v, menuInfo);
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	menu.add(0, MENU_SHOWRULES, 0, "Show rules");
    	menu.add(0, MENU_REFRESH, 0, "Refresh rules");
    	menu.add(0, MENU_PURGE, 0, "Purge rules");
    	return true;
    }
    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
    	switch (item.getItemId()) {
    	case MENU_SHOWRULES:
    		StringBuilder res = new StringBuilder();
    		try {
				int code = runScriptAsRoot("iptables -L\n", res);
	    		alert("Exit code: " + code + "\n" + res);
			} catch (Exception e) {
				alert("error: " + e);
			}
    		return true;
    	case MENU_REFRESH:
    		refreshIptables();
    		return true;
    	case MENU_PURGE:
    		purgeIptables();
    		return true;
    	}
    	return false;
    }
    
    /**
     * Display an alert box
     * @param msg message
     */
    private void alert(CharSequence msg) {
    	new AlertDialog.Builder(MainActivity.this).setMessage(msg).show();
    }
    
    /**
     * Purge and re-add all rules.
     */
    void refreshIptables() {
		if (!purgeIptables()) {
			return;
		}
    	StringBuilder script = new StringBuilder();
		try {
			int code;
			for (Application app : getApps()) {
				if (app.allowed) {
					script.append("iptables -A OUTPUT -o rmnet+ -m owner --uid-owner " + app.uid + " -j ACCEPT\n");
				}
			}
			script.append("iptables -A OUTPUT -o rmnet+ -j REJECT\n");
	    	StringBuilder res = new StringBuilder();
			code = runScriptAsRoot(script.toString(), res);
			if (code != 0) {
				alert("error adding refreshing iptables. Exit code: " + code + "\n" + res);
			}
		} catch (Exception e) {
			alert("error refreshing iptables: " + e);
		}
    }
    
    /**
     * Purge all iptables OUTPUT rules.
     * @return true if the rules were purged
     */
    boolean purgeIptables() {
    	StringBuilder res = new StringBuilder();
		try {
			int code = runScriptAsRoot("iptables -L\n", res);
			if (code != 0) {
				alert("error purging iptables. exit code: " + code + "\n" + res);
			}
			
			int len = res.length();
			int count = 0;
			int index = 0;
			while (index<len) {
				index = (res.indexOf("\n", index) + 1);
				if (index != 0) count++;
			}
			if (count <= 8) {
				return true;
			}
			count -= 8;
	    	StringBuilder script = new StringBuilder();
			while (count-- > 0) {
				script.append("iptables -D OUTPUT 1 || exit 1\n");
			}
			res.setLength(0);
			code = runScriptAsRoot(script.toString(), res);
			if (code != 0) {
				alert("error purging iptables. exit code: " + code + "\n" + res);
			}
			return true;
		} catch (Exception e) {
			alert("error purging iptables: " + e);
			return false;
		}
    }

    /**
     * @return a list of applications
     */
    Application[] getApps() {
		if (applications != null) {
			return applications;
		}
		StringBuilder res = new StringBuilder();
		try {
			// TODO - I bet there is a better way to find applications, but this one works for now
			int exitcode = runScriptAsRoot("ls -Anl /data/data\n", res);
			if (exitcode != 0) {
				alert("error listing /data/data. exitcode: " + exitcode + "\n" + res);
				return null;
			}
			HashMap<Integer, Application> map = new HashMap<Integer, Application>();
			int index = 0;
			int len = res.length();
			int end = 0;
			Integer uid;
			String pkg;
			Application app;
			while (index < len) {
				/*
				 * Output line example
				 * drwxr-xr-x 1 10028 10028 2048 May 14 13:21 com.android.browser
				 * 012345678901234567890123456789012345678901234567890123456789
				 *              16    25                      56
				 */
				end = res.indexOf("\n", index);
				if (end == -1)
					break; // no more lines
				if (res.charAt(index) != 'd') { // must be a directory
					index = end + 1; // next line
					continue;
				}
				try {
					uid = new Integer(res.substring(index + 16, index + 25).trim());
					pkg = res.substring(index + 56, end).trim();
					app = map.get(uid);
					if (app == null) {
						app = new Application();
						app.uid = uid;
						app.pkgs = new String[] { pkg };
						map.put(uid, app);
					} else {
						String newpkgs[] = new String[app.pkgs.length + 1];
						System.arraycopy(app.pkgs, 0, newpkgs, 0, app.pkgs.length);
						newpkgs[app.pkgs.length] = pkg;
						app.pkgs = newpkgs;
					}
					// TODO - read allowed packages from a database
					if (pkg.equals("com.android.browser") || pkg.equals("com.google.android.apps.maps")) {
						app.allowed = true;
					}
				} catch (Exception e) {
					Log.e("droidwall", "Error processing line from /data/data", e);
				}
				index = end + 1; // next line
			}
			applications = new Application[map.size()];
			index = 0;
			for (Application application : map.values()) applications[index++] = application;
			return applications;
		} catch (Exception e) {
			new AlertDialog.Builder(this).setMessage("error: " + e).show();
		}
		return null;
	}
    /**
     * Runs a script as root (multiple commands separated by "\n").
     * The script will be written to a file and then executed as root.
     * This way we bypass the "superuser permission" request for each different command.
     * Without this hack the user would need to authorize each command individually (since they are always different).
     * 
     * @param script the script to be executed
     * @param res the script output response (stdout + stderr)
     * @return the script exit code
     * @throws IOException on any error executing the script, or writing it to disk
     * @throws InterruptedException if the thread is interrupted
     */
    private int runScriptAsRoot(String script, StringBuilder res) throws IOException, InterruptedException {
    	// ensures that the shell file exists
    	if (!SHELL_FILE.exists()) {
    		runAsRoot("touch " + SHELL_FILE.getAbsolutePath() + ";chmod 777 " + SHELL_FILE.getAbsolutePath(), null);
    	}
    	// write the script
    	FileWriter w = new FileWriter(SHELL_FILE);
    	w.write(script);
    	w.close();
    	// run it
    	int code = runAsRoot(SHELL_FILE.getAbsolutePath(), res);
    	// truncate the script (don't waste memory)
    	new FileOutputStream(SHELL_FILE).close();
    	return code;
    }
    /**
     * Run a single command as root.
     * The command will be executed with the "su -c command" syntax.
     * @param command command to be executed
     * @param res commnand output response (stdout + stderrr)
     * @return program exit code
     * @throws IOException on any error executing the command
     * @throws InterruptedException if the thread is interrupted
     */
    private static int runAsRoot(String command, StringBuilder res) throws IOException, InterruptedException {
		char buf[] = new char[1024];
		Process exec = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
		InputStreamReader r = new InputStreamReader(exec.getInputStream());
		int read=0;
		while ((read=r.read(buf)) != -1) {
			if (res != null) res.append(buf, 0, read);
		}
		r = new InputStreamReader(exec.getErrorStream());
		read=0;
		while ((read=r.read(buf)) != -1) {
			if (res != null) res.append(buf, 0, read);
		}
		return exec.waitFor();
    }

    /**
     * Small structure to hold an application info
     */
    static final class Application {
    	int uid;
    	String pkgs[];
    	boolean allowed;
    }
}
