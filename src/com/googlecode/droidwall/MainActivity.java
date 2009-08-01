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
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListAdapter;
import android.widget.TextView;
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
	private static final int MENU_SETPWD	= 4;
	private static final int MENU_HELP		= 5;
	
	/** progress dialog instance */
	private ProgressDialog progress = null;
	/** have we alerted about incompatible apps already? */
	private boolean alerted = false;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
        	alerted = savedInstanceState.getBoolean("alerted", false);
        }
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
    @Override
    protected void onSaveInstanceState(Bundle outState) {
    	if (alerted) outState.putBoolean("alerted", true);
    }
    /**
     * Check and alert for incompatible apps
     */
    private void checkIncompatibleApps() {
        if (!alerted && Api.hastether != null) {
        	Api.alert(this, "Droid Wall has detected that you have the \"" + Api.hastether + "\" application installed on your system.\n\n" +
        		"Since this application also uses iptables, it will overwrite Droid Wall rules (and vice-versa).\n" +
        		"Please make sure that you re-apply Droid Wall rules every time you use \"" + Api.hastether + "\".");
        	alerted = true;
        }
    }
    /**
     * Show the list of applications
     */
    private void showApplications() {
        final DroidApp[] apps = Api.getApps(this);
        checkIncompatibleApps();
        // Sort applications - selected first, then alphabetically
        Arrays.sort(apps, new Comparator<DroidApp>() {
			@Override
			public int compare(DroidApp o1, DroidApp o2) {
				if (o1.allowed == o2.allowed) return o1.names[0].compareTo(o2.names[0]);
				if (o1.allowed) return -1;
				return 1;
			}
        });
        final LayoutInflater inflater = getLayoutInflater();
		final ListAdapter adapter = new ArrayAdapter<DroidApp>(this,R.layout.listitem,R.id.itemtext,apps) {
        	@Override
        	public View getView(int position, View convertView, ViewGroup parent) {
       			ListEntry entry;
        		if (convertView == null) {
        			// Inflate a new view
        			convertView = inflater.inflate(R.layout.listitem, parent, false);
       				entry = new ListEntry();
       				entry.box = (CheckBox) convertView.findViewById(R.id.itemcheck);
       				entry.text = (TextView) convertView.findViewById(R.id.itemtext);
       				convertView.setTag(entry);
       				entry.box.setOnCheckedChangeListener(MainActivity.this);
        		} else {
        			// Convert an existing view
        			entry = (ListEntry) convertView.getTag();
        		}
        		final DroidApp app = apps[position];
        		entry.text.setText(app.toString());
        		final CheckBox box = entry.box;
        		box.setTag(app);
        		box.setChecked(app.allowed);
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
    	menu.add(0, MENU_SETPWD, 0, R.string.setpwd).setIcon(R.drawable.lock);
    	menu.add(0, MENU_HELP, 0, R.string.help).setIcon(android.R.drawable.ic_menu_help);
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
        			if (!Api.hasRootAccess(MainActivity.this)) return;
           			Api.showIptablesRules(MainActivity.this);
        		}
        	};
			handler.sendEmptyMessageDelayed(0, 100);
    		return true;
    	case MENU_APPLY:
    		progress = ProgressDialog.show(this, "Working...", "Applying iptables rules.", true);
        	handler = new Handler() {
        		public void handleMessage(Message msg) {
        			if (progress != null) progress.dismiss();
        			if (!Api.hasRootAccess(MainActivity.this)) return;
        			if (Api.applyIptablesRules(MainActivity.this, true)) {
        				Toast.makeText(MainActivity.this, "Rules applied with success", Toast.LENGTH_SHORT).show();
        			}
        		}
        	};
			handler.sendEmptyMessageDelayed(0, 100);
    		return true;
    	case MENU_PURGE:
    		progress = ProgressDialog.show(this, "Working...", "Deleting iptables rules.", true);
        	handler = new Handler() {
        		public void handleMessage(Message msg) {
        			if (progress != null) progress.dismiss();
        			if (!Api.hasRootAccess(MainActivity.this)) return;
        			if (Api.purgeIptables(MainActivity.this)) {
        				Toast.makeText(MainActivity.this, "Rules purged with success", Toast.LENGTH_SHORT).show();
        			}
        		}
        	};
			handler.sendEmptyMessageDelayed(0, 100);
    		return true;
    	case MENU_SETPWD:
    		Api.alert(this, "Feature not implemented yet...");
    		return true;
    	case MENU_HELP:
    		new HelpDialog(this).show();
    		return true;
    	}
    	return false;
    }
	/**
	 * Called an application is check/unchecked
	 */
	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		final DroidApp app = (DroidApp) buttonView.getTag();
		if (app != null) {
			app.allowed = isChecked;
		}
	}
	
	private static class ListEntry {
		private CheckBox box;
		private TextView text;
	}
}
