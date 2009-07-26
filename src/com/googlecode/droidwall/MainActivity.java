/**
 * Main application activity.
 * This is the screen displayed when you open the application
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

import android.app.Activity;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;

/**
 * Main application activity.
 * This is the screen displayed when you open the application
 */
public class MainActivity extends Activity {
	
	// Menu options
	private static final int MENU_SHOWRULES = 1;
	private static final int MENU_REFRESH = 2;
	private static final int MENU_PURGE = 3;
	
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
				int code = Api.runScriptAsRoot("iptables -L\n", res);
				Api.alert(this, "Exit code: " + code + "\n" + res);
			} catch (Exception e) {
				Api.alert(this, "error: " + e);
			}
    		return true;
    	case MENU_REFRESH:
    		Api.refreshIptables(this);
    		return true;
    	case MENU_PURGE:
    		Api.purgeIptables(this);
    		return true;
    	}
    	return false;
    }
}
