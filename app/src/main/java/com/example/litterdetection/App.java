package com.example.litterdetection;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import net.gotev.uploadservice.UploadServiceConfig;

public class App extends Application {
    private static final String notificationChannelID = "LitterDetection";

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    notificationChannelID,
                    "Little Detection",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        UploadServiceConfig.initialize(
                 this, notificationChannelID, BuildConfig.DEBUG
        );
    }
}