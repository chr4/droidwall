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
import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListAdapter;
import android.widget.Toast;
import android.widget.CompoundButton.OnCheckedChangeListener;

import com.googlecode.droidwall.Api.DroidApp;

/**
 * Main application activity.
 * This is the screen displayed when you open the application
 */
public class MainActivity extends ListActivity implements OnCheckedChangeListener {
	
	// Menu options
	private static final int MENU_SHOWRULES	= 1;
	private static final int MENU_APPLY		= 2;
	private static final int MENU_PURGE		= 3;
	private static final int MENU_HELP		= 4;
	
	/** progress dialog instance */
	private ProgressDialog progress = null;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
    @Override
    protected void onResume() {
    	super.onResume();
    	if (Api.applications == null) {
    		// The applications are not cached.. so lets display the progress dialog
    		progress = ProgressDialog.show(this, "Working...", "Reading installed applications", true);
        	final Handler handler = new Handler() {
        		public void handleMessage(Message msg) {
        			if (progress != null) progress.dismiss();
        			showApplications();
        		}
        	};
        	new Thread() {
        		public void run() {
        			Api.getApps(MainActivity.this);
        			handler.sendEmptyMessage(0);
        		}
        	}.start();
    	} else {
    		// the applications are cached, just show the list
        	showApplications();
    	}
    }
    /**
     * Show the list of applications
     */
    private void showApplications() {
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
    public boolean onCreateOptionsMenu(Menu menu) {
    	menu.add(0, MENU_SHOWRULES, 0, R.string.showrules).setIcon(R.drawable.show);
    	menu.add(0, MENU_APPLY, 0, R.string.applyrules).setIcon(R.drawable.apply);
    	menu.add(0, MENU_PURGE, 0, R.string.purgerules).setIcon(R.drawable.purge);
    	menu.add(0, MENU_HELP, 0, R.string.help).setIcon(R.drawable.help);
    	return true;
    }
    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
    	final Handler handler;
    	switch (item.getItemId()) {
    	case MENU_SHOWRULES:
    		progress = ProgressDialog.show(this, "Working...", "Please wait", true);
        	handler = new Handler() {
        		public void handleMessage(Message msg) {
        			if (progress != null) progress.dismiss();
        			Api.showIptablesRules(MainActivity.this);
        		}
        	};
			handler.sendEmptyMessageDelayed(0, 200);
    		return true;
    	case MENU_APPLY:
    		progress = ProgressDialog.show(this, "Working...", "Applying iptables rules.", true);
        	handler = new Handler() {
        		public void handleMessage(Message msg) {
        			if (Api.refreshIptables(MainActivity.this, true)) {
        				Toast.makeText(MainActivity.this, "Rules applied with success", Toast.LENGTH_SHORT).show();
        			}
        			if (progress != null) progress.dismiss();
        		}
        	};
			handler.sendEmptyMessageDelayed(0, 200);
    		return true;
    	case MENU_PURGE:
    		progress = ProgressDialog.show(this, "Working...", "Deleting iptables rules.", true);
        	handler = new Handler() {
        		public void handleMessage(Message msg) {
        			if (Api.purgeIptables(MainActivity.this)) {
        				Toast.makeText(MainActivity.this, "Rules purged with success", Toast.LENGTH_SHORT).show();
        			}
        			if (progress != null) progress.dismiss();
        		}
        	};
			handler.sendEmptyMessageDelayed(0, 200);
    		return true;
    	case MENU_HELP:
    		Api.alert(this, "Droid Wall v" + Api.VERSION + "\n" +
    			"Author: Rodrigo Rosauro\n" +
    			"http://droidwall.googlecode.com/\n\n" +
				"Mark the applications that are ALLOWED to use GPRS/3G then click on \"Apply rules\".\n" +
				"Important: unmarked applications will NOT be able to access your data plan, but they will still be able to access Wifi.\n\n" +
				"Click on \"Show rules\" to display current iptables rules output.\n\n" +
				"Click on \"Purge rules\" to remove any iptables rules TEMPORARILY (until the next \"Apply rules\" or reboot).");
    		return true;
    	}
    	return false;
    }
	/**
	 * Called an application is check/unchecked
	 */
	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		DroidApp app = (DroidApp) buttonView.getTag();
		app.allowed = isChecked;
	}
}
