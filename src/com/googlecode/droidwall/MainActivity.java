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

import java.util.Arrays;
import java.util.Comparator;

import android.app.ListActivity;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListAdapter;
import android.widget.CompoundButton.OnCheckedChangeListener;

import com.googlecode.droidwall.Api.DroidApp;

/**
 * Main application activity.
 * This is the screen displayed when you open the application
 */
public class MainActivity extends ListActivity implements OnCheckedChangeListener {
	
	// Menu options
	private static final int MENU_SHOWRULES = 1;
	private static final int MENU_REFRESH = 2;
	private static final int MENU_PURGE = 3;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
    @Override
    protected void onResume() {
    	super.onResume();
        final DroidApp[] apps = Api.getApps(this);
        Arrays.sort(apps, new Comparator<DroidApp>() {
			@Override
			public int compare(DroidApp o1, DroidApp o2) {
				if (o1.allowed == o2.allowed) return 0;
				if (o1.allowed) return -1;
				return 1;
			}
        });
		ListAdapter adapter = new ArrayAdapter<DroidApp>(this,R.layout.listitem,R.id.itemtext,apps) {
        	@Override
        	public View getView(int position, View convertView, ViewGroup parent) {
       			convertView = super.getView(position, convertView, parent);
        		final DroidApp item = this.getItem(position);
        		final CheckBox box = (CheckBox) convertView.findViewById(R.id.itemcheck);
       			box.setTag(item);
				box.setChecked(item.allowed);
       			box.setOnCheckedChangeListener(MainActivity.this);
       			return convertView;
        	}
        };
        
        setListAdapter(adapter);
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
	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		DroidApp app = (DroidApp) buttonView.getTag();
		app.allowed = isChecked;
	}
}
