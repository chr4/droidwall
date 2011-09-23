/**
 * Dialog displayed to request a custom script.
 * 
 * Copyright (C) 2009-2011  Rodrigo Zechin Rosauro
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

import android.app.Dialog;
import android.content.Context;
import android.os.Handler.Callback;
import android.os.Message;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Dialog displayed to request a custom script.
 */
public class CustomScriptDialog extends Dialog implements android.view.View.OnClickListener {
	private final EditText script;
	private final Callback callback;

	/**
	 * Creates the dialog
	 * @param context context
	 * @param script string contain previous script
	 * @param callback callback to receive the script entered (null if canceled)
	 */
	CustomScriptDialog(Context contex, String script, Callback callback) {
		super(contex);
		final View view = getLayoutInflater().inflate(R.layout.customscript_dialog, null);
		((Button)view.findViewById(R.id.customscript_ok)).setOnClickListener(this);
		((Button)view.findViewById(R.id.customscript_cancel)).setOnClickListener(this);
		((TextView)view.findViewById(R.id.customscript_link)).setMovementMethod(LinkMovementMethod.getInstance());
		this.script = (EditText) view.findViewById(R.id.customscript);
		this.script.setText(script);
		this.callback = callback;
		setTitle(R.string.set_custom_script);
		setContentView(view);
	}
	
	@Override
	public void onClick(View v) {
		final Message msg = new Message();
		if (v.getId() == R.id.customscript_ok) {
			msg.obj = script.getText().toString();
		}
		dismiss();
		callback.handleMessage(msg);
	}
}
