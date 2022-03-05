/*
 * Copyright (C) 2014 Mauricio Rodriguez (ranametal@users.sf.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hmsoft.pentaxgallery.util;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.MacAddress;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.NetworkSpecifier;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.os.PatternMatcher;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.hmsoft.pentaxgallery.BuildConfig;
import com.hmsoft.pentaxgallery.MyApplication;
import com.hmsoft.pentaxgallery.camera.controller.CameraController;

import java.util.List;

public class WifiHelper {
    private static final String TAG = "WifiHelper";
    private WifiHelper() {}

    private static List<ScanResult> sLatestScanResults = null;
    private static boolean sScanResultsAvailable = false;
  
    private static BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
          boolean success = intent.getBooleanExtra(
                             WifiManager.EXTRA_RESULTS_UPDATED, false);
          if (success) {
              updateScanResults(context);
          }
          setScanResultsAvailable(true);
      }
    };  
  
    private static synchronized void setScanResultsAvailable(boolean available) {
        sScanResultsAvailable = available;
    }
  
    private static synchronized boolean areScanResultsAvailable() {
        return sScanResultsAvailable;
    }
  
    public static void waitForScanResultsAvailable(long timeOut) {
        int c = 0;
        final int sleepms = 500;
        
        while(!areScanResultsAvailable() && (c++ * sleepms) <  timeOut) {
            TaskExecutor.sleep(sleepms);
        }
      
        if(BuildConfig.DEBUG) Logger.debug(TAG, "Scan results " + (areScanResultsAvailable() ? "" : "NOT ") + "available" +
                (c > 0 ? " after " + ((c-1)*sleepms) + "ms" : ""));
    }
  
    private static synchronized void updateScanResults(Context context) {
        final WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        
        if(wifiManager == null) {
            return ;
        }
        sLatestScanResults = wifiManager.getScanResults();
    }
  
    public static boolean startWifiScan(Context context) {
       final WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        
        if(wifiManager == null) {
            return false;
        }
      
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        context.registerReceiver(wifiScanReceiver, intentFilter);
        setScanResultsAvailable(false);
        return wifiManager.startScan();
    }
  
    public static synchronized List<ScanResult> getLatestScanResults() {
        if(sLatestScanResults == null) {
            updateScanResults(MyApplication.ApplicationContext);
        }
        return sLatestScanResults;
    }
  
    public static void turnWifiOn(Context context, long waitTime) {
        final WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        
        if(wifiManager == null) {
            return ;
        }
        
        if(!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
            if(waitTime > 0) TaskExecutor.sleep(waitTime);
        }
    }

    public static  boolean disconnect(Context context) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return disconnectQ(context);
        }

        final WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            Logger.error(TAG, "No WiFi manager");
            return false;
        }

        if (!wifiManager.disconnect()) {
            Logger.error(TAG, "WiFi disconnect failed");
            return false;
        }

        return true;
    }

    private static boolean disconnectQ(Context context) {
        if (networkCallback != null) {
            final ConnectivityManager connectivityManager = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);

            connectivityManager.unregisterNetworkCallback(networkCallback);
            networkCallback = null;
            return true;
        }

        return false;
    }

    public static boolean connectToWifi(Context context, final String ssid, String password, CameraController.OnCameraDisconnectedListener onCameraDisconnected) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return connectToWifiQ(context, ssid, password, onCameraDisconnected);
        }

        if (TextUtils.isEmpty(ssid)) {
            return  false;
        }

        int networkId = -1;
        int c;

        final WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            Logger.error(TAG, "No WiFi manager");
            return false;
        }

        List<WifiConfiguration> list;

        if (wifiManager.isWifiEnabled()) {
            list = wifiManager.getConfiguredNetworks();
        } else {
            if (!wifiManager.setWifiEnabled(true)) {
                Logger.error(TAG, "Enable WiFi failed");
                return false;
            }
            c = 0;
            do {
                TaskExecutor.sleep(500L);
                list = wifiManager.getConfiguredNetworks();
            } while (list == null && ++c < 10);
        }

        if (list == null) {
            Logger.error(TAG, "Could not get WiFi network list");
            return false;
        }

        for (WifiConfiguration i : list) {
            if (i.SSID != null && i.SSID.equals("\"" + ssid + "\"")) {
                networkId = i.networkId;
                break;
            }
        }

        WifiInfo info;
        if (networkId < 0) {
            WifiConfiguration conf = new WifiConfiguration();
            conf.SSID = "\"" + ssid + "\"";
            conf.preSharedKey = "\"" + password + "\"";
            networkId = wifiManager.addNetwork(conf);
            if (networkId < 0) {
                Logger.error(TAG, "New WiFi config failed");
                return false;
            }
        } else {
            info = wifiManager.getConnectionInfo();
            if (info != null) {
                if (info.getNetworkId() == networkId) {
                    if(Logger.DEBUG) Logger.debug(TAG, "Already connected to " + ssid);
                    return true;
                }
            }
        }

        if (!wifiManager.disconnect()) {
            Logger.error(TAG, "WiFi disconnect failed");
            return false;
        }

        if (!wifiManager.enableNetwork(networkId, true)) {
            Logger.error(TAG, "Could not enable WiFi.");
            return false;
        }

        if (!wifiManager.reconnect()) {
            Logger.error(TAG, "WiFi reconnect failed");
            return false;
        }

        c = 0;
        do {
            info = wifiManager.getConnectionInfo();
            if (info != null && info.getNetworkId() == networkId &&
                    info.getSupplicantState() == SupplicantState. COMPLETED &&  info.getIpAddress() != 0) {
                if(Logger.DEBUG) Logger.debug(TAG, "Successfully connected to %s %d", ssid, info.getIpAddress());
                return true;
            }
            TaskExecutor.sleep(500L);
        } while (++c < 20);

        Logger.error(TAG, "Failed to connect to " + ssid);
        return false;
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private static boolean connectToWifiQ(Context context, final String ssid, String password, CameraController.OnCameraDisconnectedListener onCameraDisconnected) {

        if (WifiConnectionNotifier.isConnecting()) {
            Logger.warning(TAG, "Connection in progress");
            return false;
        }

        WifiNetworkSpecifier.Builder builder = new WifiNetworkSpecifier.Builder();
        if (TextUtils.isEmpty(ssid)) {
            // TODO: Set pentax camera pattern
            builder.setSsidPattern(new PatternMatcher("GR_", PatternMatcher.PATTERN_PREFIX));
            builder.setBssidPattern(MacAddress.fromString("e8:e8:b7:00:00:00"), MacAddress.fromString("ff:ff:ff:00:00:00"));
        } else {
            builder.setWpa2Passphrase(password);
            builder.setSsid(ssid);
        }

        final NetworkSpecifier specifier = builder.build();

        final NetworkRequest request =
                new NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .setNetworkSpecifier(specifier)
                        .build();

        final ConnectivityManager connectivityManager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);

        boolean result;
        WifiConnectionNotifier.startConnection();
        try {

            if (networkCallback != null) {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            }

            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    if (Logger.DEBUG) Logger.debug(TAG, "Connected to " + ssid);
                    WifiConnectionNotifier.setConnected(true);
                }

                @Override
                public void onUnavailable() {
                    if (Logger.DEBUG) Logger.debug(TAG, "NOT Connected to " + ssid);
                    WifiConnectionNotifier.setConnected(false);
                }

                @Override
                public void onLosing(@NonNull Network network, int maxMsToLive) {
                    super.onLosing(network, maxMsToLive);
                    if (Logger.DEBUG) Logger.debug(TAG, "onLosing");
                }

                @Override
                public void onLost(@NonNull Network network) {
                    if (networkCallback != null) {
                        connectivityManager.unregisterNetworkCallback(networkCallback);
                        networkCallback = null;
                    }

                    if (onCameraDisconnected != null) {
                        onCameraDisconnected.onCameraDisconnected();
                    }

                    if (Logger.DEBUG) Logger.debug(TAG, "onLost");
                }
            };

            connectivityManager.requestNetwork(request, networkCallback);

            result = WifiConnectionNotifier.isConnected();
            if (!result) {
                // Release the request when done.
                connectivityManager.unregisterNetworkCallback(networkCallback);
                networkCallback = null;
            }

        } finally {
            WifiConnectionNotifier.finishConnection();
        }

        return result;
    }

    public static boolean isWifiInRange(String ssid) {
        for(ScanResult scanResult : getLatestScanResults()) {
            if(scanResult.SSID != null && scanResult.SSID.equalsIgnoreCase(ssid)) {
                return true;
            }
        }
        return false;
    }

    private static ConnectivityManager.NetworkCallback networkCallback;
    private static class WifiConnectionNotifier {
        private static  WifiConnectionNotifier instance = null;

        private boolean isConnected;

        public static boolean isConnecting() {
            return instance != null;
        }

        public static void startConnection() {
            instance = new WifiConnectionNotifier();
        }

        public static void setConnected(boolean  connected) {
            instance.internalSetConnected(connected);
        }

        public static boolean isConnected() {
            boolean result = instance.internalIsConnected();
            return result;
        }

        public static void finishConnection() {
            instance = null;
        }

        private void waitConnectionResult() {
            synchronized (this) {
                try { this.wait(); } catch (InterruptedException ignored) { }
            }
        }

        private void internalSetConnected(boolean connected) {
            synchronized (this) {
                isConnected = connected;
                this.notify();
            }
        }

        private boolean internalIsConnected() {
            waitConnectionResult();
            synchronized (this) {
                return this.isConnected;
            }
        }
    }
}
