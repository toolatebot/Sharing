package com.ammar.sharing.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.ammar.sharing.R;
import com.ammar.sharing.activities.MainActivity.MainActivity;
import com.ammar.sharing.common.Consts;
import com.ammar.sharing.common.Data;
import com.ammar.sharing.models.Sharable;
import com.ammar.sharing.models.SharableApp;
import com.ammar.sharing.models.User;
import com.ammar.sharing.network.Server;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Locale;

/**
 * @noinspection unused
 */
public class ServerService extends Service {


    private final int FOREGROUND_NOTIFICATION_ID = 1;
    public static final int PORT_NUMBER = 2999;


    private final Server server = new Server(this);
    final Intent serverStatusIntent = new Intent();

    @Override
    public void onCreate() {
        super.onCreate();
        serverStatusIntent.setAction(Consts.ACTION_GET_SERVER_STATUS);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_STICKY;
        }
        String actionReceived = intent.getAction();
        String action = actionReceived != null ? actionReceived : "";
        switch (action) {
            case Consts.ACTION_TOGGLE_SERVER:
                toggleServer();
                break;
            case Consts.ACTION_STOP_SERVICE:
                stopSelf();
            case Consts.ACTION_GET_SERVER_STATUS:
                sendServerStatusToActivity();
                break;
            case Consts.ACTION_UPDATE_NOTIFICATION_TEXT:
                startForeground(FOREGROUND_NOTIFICATION_ID, buildNotification(this));
                break;
            case Consts.ACTION_ADD_DOWNLOADS:
                ArrayList<String> filePaths = intent.getStringArrayListExtra(Consts.EXTRA_FILES_PATH);
                assert filePaths != null;
                for( String i : filePaths ) {
                    Server.sharablesList.add( new Sharable(i));
                }
                Bundle fb = new Bundle();
                fb.putChar("action", 'A');
                Data.filesListNotifier.postValue(fb);
                break;
            case Consts.ACTION_ADD_APPS_DOWNLOADS:
                ArrayList<String> packages_name = intent.getStringArrayListExtra(Consts.EXTRA_APPS_NAMES);
                if( packages_name != null ) {
                    for( String i : packages_name ) {
                        try {
                            Server.sharablesList.add(new SharableApp(this, i));
                        } catch (PackageManager.NameNotFoundException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
                Bundle ab = new Bundle();
                ab.putChar("action", 'A');
                Data.filesListNotifier.postValue(ab);
                break;
            case Consts.ACTION_REMOVE_DOWNLOAD:
                String uuid = intent.getStringExtra(Consts.EXTRA_DOWNLOAD_UUID);

                int index = 0;
                for( Sharable i : Server.sharablesList) {
                    if( i.getUUID().equals(uuid) ) {
                        Server.sharablesList.remove(index);
                        break;
                    }
                    index++;
                }
                Bundle remove_info = new Bundle();
                remove_info.putChar("action", 'R');
                remove_info.putInt("index", index);
                Data.filesListNotifier.forcePostValue(remove_info);
                break;
            case Consts.ACTION_STOP_APP_PROCESS_IF_SERVER_DOWN:
                if( !server.isRunning() ) {
                    Log.d("MYLOG", "Stopping App process");
                    int pid = android.os.Process.myPid();
                    android.os.Process.killProcess(pid);
                }
            default:
                break;
        }

        return START_STICKY;
    }

    private void toggleServer() {
        if (server.isRunning()) {
            User.closeAllSockets();
            server.Stop();
        } else {
            server.Start();
        }
        sendServerStatusToActivity();
    }

    private boolean isRunningFirstTime = true;
    private void sendServerStatusToActivity() {
        boolean isServerRunning = server.isRunning();

        // stop notification or start it
        if (isServerRunning && isRunningFirstTime) {
            startForeground(FOREGROUND_NOTIFICATION_ID, buildNotification(this));
            isRunningFirstTime = false;
        } else {
            stopForegroundAndNotification();
            isRunningFirstTime = true;
        }

        // notify observer.
        Data.serverStatusObserver.postValue(isServerRunning);
    }

    public Notification buildNotification(Context context) {
        // Create a notification manager
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Notification channel ID
        String channelId = "com.ammar.sharing";
        // Notification channel name
        CharSequence channelName = "Server";
        // Notification importance level

        // Check if Android version is Oreo or higher, as notification channels are required from Oreo onwards
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int importance = NotificationManager.IMPORTANCE_HIGH;
            // Create a notification channel
            NotificationChannel channel = new NotificationChannel(channelId, channelName, importance);
            // Configure the notification channel
            channel.setDescription("Your channel description");
            channel.enableLights(true);
            channel.setLightColor(Color.RED);
            // Register the notification channel with the system
            notificationManager.createNotificationChannel(channel);
        }

        String address = ServerService.getIpAddress();
        if (address == null) address = "localhost";
        // Build the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId).setContentTitle(getResources().getString(R.string.svr_running)).setSmallIcon(android.R.drawable.ic_dialog_info);

        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_IMMUTABLE);
        builder.setContentIntent(pendingIntent);

        return new NotificationCompat.BigTextStyle(builder).bigText(getResources().getString(R.string.svr_notification_message, address, String.format(Locale.ENGLISH, "%d", PORT_NUMBER)))
                .build();
    }

    public static String getIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException ex) {
            if (ex.getMessage() == null) return null;
            Log.e("MYLOG", ex.getMessage());
        }
        return null;
    }


    private void stopForegroundAndNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
    }

    public Server getServer() {
        return server;
    }
}
