package com.hmsoft.pentaxgallery.service;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class WifiIntentReceiver extends BroadcastReceiver {

    private static WifiIntentReceiver instance = null;

    private static final String TAG = "WifiIntentReceiver";

    private boolean firstTime = true;
    private long lastConnectedTime;

    public static void register(Context context) {
        /*if(instance == null) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
            instance = new WifiIntentReceiver();
            context.registerReceiver(instance, intentFilter);
            if (BuildConfig.DEBUG) Logger.debug(TAG, "Registered");
        }*/
    }

    public static void unregister(Context context) {
        if(instance != null) {
            context.unregisterReceiver(instance);
            instance = null;
        }
    }

    @Override
    public void onReceive(final Context context, Intent intent) {
        /*final String action = intent.getAction();
        if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
            NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);

            if (info != null && info.isConnected()) {
                if (!firstTime && (System.currentTimeMillis() - lastConnectedTime > 1500)) {
                    if (BuildConfig.DEBUG) Logger.debug(TAG, "Wifi Connected!");

                    if(ControllerFactory.DefaultController.connectToCamera()) {
                        ControllerFactory.DefaultController.ping(new CameraController.OnAsyncCommandExecutedListener() {
                            @Override
                            public void onAsyncCommandExecuted(BaseResponse response) {
                                if (response != null && response.success) {
                                    ImageGridActivity.start(context,true);
                                }
                                if (BuildConfig.DEBUG)
                                    Logger.debug(TAG, "Camera connected:" + (response != null && response.success));
                            }
                        });
                    }
                }
                firstTime = false;
                lastConnectedTime = System.currentTimeMillis();
            }
        }*/
    }
}