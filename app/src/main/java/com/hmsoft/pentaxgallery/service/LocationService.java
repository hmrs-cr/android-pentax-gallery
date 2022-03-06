package com.hmsoft.pentaxgallery.service;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.hmsoft.pentaxgallery.BuildConfig;
import com.hmsoft.pentaxgallery.MyApplication;
import com.hmsoft.pentaxgallery.R;
import com.hmsoft.pentaxgallery.camera.Camera;
import com.hmsoft.pentaxgallery.camera.model.BaseResponse;
import com.hmsoft.pentaxgallery.camera.model.UpdateGpsLocationResponse;
import com.hmsoft.pentaxgallery.data.LocationTable;
import com.hmsoft.pentaxgallery.util.Logger;
import com.hmsoft.pentaxgallery.util.TaskExecutor;
import com.hmsoft.pentaxgallery.util.Utils;

import java.util.Date;
import java.util.Locale;

@SuppressLint("MissingPermission")
public class LocationService extends Service {

    private static  final int NOTIFICATION_ID = 9;
    private static final String ACTION_UPDATE_LOCATION = BuildConfig.APPLICATION_ID + ".UPDATE_LOCATION";
    private static final String ACTION_STOP_LOCATION_UPDATES = BuildConfig.APPLICATION_ID + ".STOP_LOCATION_UPDATES";
    private static final String ACTION_UPDATE_CONFIG = BuildConfig.APPLICATION_ID + ".ACTION_UPDATES_CONFIG";

    private static final String TAG = "LocationService";

    private AlarmManager mAlarm = null;
    private PendingIntent mAlarmLocationCallback = null;

    private  NotificationManagerCompat mNotificationManager;
    private LocationManager mLocationManager;
    private boolean mNetProviderEnabled;
    private boolean mGpsProviderEnabled;
    private LocationListener mNetLocationListener;
    private LocationListener mGpsLocationListener;
    private LocationListener mPassiveLocationListener;

    private Location mLastSavedLocation;
    private Location mCurrentBestLocation;

    private static long sLocationUpdateInterval = 0;
    private int mGpsTimeout = 60;
    private float mMinimumDistance = 100;
    private boolean mTimeoutRoutinePending;
    private float mMaxReasonableSpeed = 55;
    private int mMinimumAccuracy = 150;
    private long mMinTimeDelta = 60;
    private float mBestAccuracy = 20;

    private Intent mMapIntent;
    private ComponentName mMapIntentComponent;
    private int mLocationCount;
    private PowerManager mPowerManager;
    private PowerManager.WakeLock mWakeLock;
    private PendingIntent mStopPendingIntent = null;

    public static void start(Context context) {
        start(context, ACTION_UPDATE_LOCATION);
    }

    public static void updateConfig(Context context) {
        start(context, ACTION_UPDATE_CONFIG);
    }

    public static void stopLocationUpdates(Context context) {
        start(context, ACTION_STOP_LOCATION_UPDATES);
    }

    public static void start(Context context, String action) {
        Intent intent = new Intent();
        intent.setAction(action);
        start(context, intent);
    }

    public static void start(Context context, Intent intent) {
        context = context.getApplicationContext();
        intent.setClass(context, LocationService.class);

        if (!isServiceEnabled()) {
            context.stopService(intent);
            return;
        }

        if (checkLocationPermission(context)) {
            context.startForegroundService(intent);
        }
    }

    public static LocationTable.LatLong getLocationAtTime(long time) {
        if (sLocationUpdateInterval == 0) {
            sLocationUpdateInterval = MyApplication.getIntPref(R.string.key_location_update_interval, R.string.default_location_update_interval);
        }

        return LocationTable.getLocationAtTimestamp(time, sLocationUpdateInterval * 2);
    }

    void updateConfig() {
        if (!isServiceEnabled()) {
            stopSelf();
            return;
        }

        mGpsTimeout = 60;
        mMinimumDistance = 100;
        mMaxReasonableSpeed = 280;
        mMinimumAccuracy = 200;
        mMinTimeDelta = 30;
        mBestAccuracy = 20;

        sLocationUpdateInterval = MyApplication.getIntPref(R.string.key_location_update_interval, R.string.default_location_update_interval);
    }

    private static boolean isServiceEnabled() {
        boolean enabled =  MyApplication.getBooleanPref(R.string.key_enable_location_service, R.string.default_enable_location_service);
        if (Logger.DEBUG && !enabled) {
            Logger.debug(TAG, "Service disabled.");
        }
        return enabled;
    }

    void startLocationListener() {
        acquireWakeLock();
        if (mLocationManager == null) {

            mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

            mNetProviderEnabled = mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            mGpsProviderEnabled = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

            long time = mGpsTimeout / 4;
            if (Logger.DEBUG) {
               // mGpsTimeout = 10;
            }

            float minDistance = mMinimumDistance / 2;

            if (mNetProviderEnabled) {
                if (mNetLocationListener == null) {
                    mNetLocationListener = new LocationListener(this, LocationManager.NETWORK_PROVIDER);
                }
                mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, time, minDistance, mNetLocationListener);
                if (Logger.DEBUG)
                    Logger.debug(TAG, "requestLocationUpdates for %s", mNetLocationListener.mProvider);
            }

            if (mGpsProviderEnabled) {
                if (mGpsLocationListener == null) {
                    mGpsLocationListener = new LocationListener(this, LocationManager.GPS_PROVIDER);
                }
                mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, time, minDistance, mGpsLocationListener);
                if (Logger.DEBUG)
                    Logger.debug(TAG, "requestLocationUpdates for %s", mGpsLocationListener.mProvider);
            }
        }

        if (!mTimeoutRoutinePending) {
            if (Logger.DEBUG) Logger.debug(TAG, "Executing gps timeout in %d seconds", mGpsTimeout);
            mTimeoutRoutinePending = true;
            TaskExecutor.executeOnUIThread(new Runnable() {
                @Override
                public void run() {
                    mTimeoutRoutinePending = false;
                    if (mLocationManager != null /*|| mLocationRequest != null*/) {
                        if (Logger.DEBUG) Logger.debug(TAG, "GPS Timeout");
                        saveLastLocation();
                        stopLocationListener();
                    }
                }
            }, mGpsTimeout * 1000L);
        }
    }

    private void stopLocationListener() {
        stopLocationListener(true);
    }

    private void stopLocationListener(boolean triggerNextAlarm) {
        if (mNetLocationListener != null) {
            mLocationManager.removeUpdates(mNetLocationListener);
            if (Logger.DEBUG) Logger.debug(TAG, "LocationListener stopped: %s", mNetLocationListener.mProvider);
            mNetLocationListener.mService = null;
            mNetLocationListener = null;
        }
        if (mGpsLocationListener != null) {
            mLocationManager.removeUpdates(mGpsLocationListener);
            if (Logger.DEBUG) Logger.debug(TAG, "LocationListener stopped: %s", mGpsLocationListener.mProvider);
            mGpsLocationListener.mService = null;
            mGpsLocationListener = null;
        }

        if (triggerNextAlarm) {
            setLocationAlarm();
        }

        releaseWakeLock();
        mLocationManager = null;
    }

    private void handleLocation(Location location, String provider) {
        if (mCurrentBestLocation != null &&
                (mCurrentBestLocation.getTime() == location.getTime())) {
            logLocation(location, "Location is the same location that currentBestLocation");
            return;
        }

        if (isBetterLocation(location, mCurrentBestLocation)) {
            if (LocationManager.PASSIVE_PROVIDER.equals(provider) && mLocationManager != null) {
                if (Logger.DEBUG)
                    Logger.debug(TAG, "Ignored passive location while in location request.");
            } else if (isBetterLocation(location, mCurrentBestLocation)) {
                mCurrentBestLocation = location;
                if ((!mGpsProviderEnabled) ||
                        (isFromGps(mCurrentBestLocation) && location.getAccuracy() <= mBestAccuracy)) {
                    if (Logger.DEBUG)
                        Logger.debug(TAG, "Received location update: Accuracy:" + location.getAccuracy() + ", Time: " + location.getTime() + ", Provider: " + provider + "/" + location.getProvider());
                    saveLastLocation();
                    stopLocationListener();
                } else {
                    if (Logger.DEBUG) Logger.debug(TAG, "No good GPS location: " + location.getProvider() + ", " + location.getAccuracy() + ":" + mBestAccuracy);
                }
            }
        }
    }

    void setLocationAlarm() {
        if(Logger.DEBUG) {
            Toast.makeText(this, "Location alarm set to " + sLocationUpdateInterval + "s", Toast.LENGTH_LONG).show();
        }

        mAlarm.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() +
                sLocationUpdateInterval * 1000L, mAlarmLocationCallback);

        if(Logger.DEBUG) Logger.debug(TAG, "Set alarm to %d seconds", sLocationUpdateInterval);
    }

    private void saveLastLocation() {
        if(Logger.DEBUG) Logger.debug(TAG, "saveLastLocation");

        if (mCurrentBestLocation == mLastSavedLocation ||
                (mLastSavedLocation != null && mCurrentBestLocation.getTime() == mLastSavedLocation.getTime())) {
            logLocation(null, "currentBestLocation is the same lastSavedLocation. Saving nothing...");
            return;
        }

        if (mCurrentBestLocation != null && isFromGps(mCurrentBestLocation)) {
            if(Logger.DEBUG) Logger.debug(TAG, "currentBestLocation is from GPS");
            saveLocation(mCurrentBestLocation);
            logLocation(mCurrentBestLocation, "*** Location saved (current best)");
            return;
        }

        Location bestLastLocation = mCurrentBestLocation;
        if (mGpsProviderEnabled) {
            if(Logger.DEBUG) Logger.debug(TAG, "currentBestLocation is not from GPS, but GPS is enabled");
            Location lastKnownGpsLocation = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (isBetterLocation(lastKnownGpsLocation, bestLastLocation)) {
                if(Logger.DEBUG) Logger.debug(TAG, "Got good LastKnownLocation from GPS provider.");
                bestLastLocation = lastKnownGpsLocation;
            } else {
                if(Logger.DEBUG) Logger.debug(TAG, "LastKnownLocation from GPS provider is not better than currentBestLocation.");
            }
        }

        if (mNetProviderEnabled) {
            Location lastKnownNetLocation = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (isBetterLocation(lastKnownNetLocation, bestLastLocation)) {
                bestLastLocation = lastKnownNetLocation;
            }
        }

        if (bestLastLocation != null) {
            saveLocation(bestLastLocation);
            logLocation(bestLastLocation, "*** Location saved (best last)");
            if(mCurrentBestLocation == null) {
                mCurrentBestLocation = bestLastLocation;
            }
        } else if (Logger.DEBUG) {
            logLocation(null, "No last location. Turn on GPS!");
        }
    }

    private void saveLocation(Location location) {
        Camera camera = Camera.instance;

        final boolean storeInCamera = camera.getPreferences().isStoreLocationInCameraEnabled()
                && camera.isConnected() && camera.getCameraData().geoTagging;

        TaskExecutor.executeOnSingleThreadExecutor(() -> {

            long saved = LocationTable.saveToDatabase(location);
            if (saved > 0) {
                TaskExecutor.executeOnUIThread(() -> {
                    mLastSavedLocation = location;
                    if (Logger.DEBUG) logLocation(location, "Saved last best location.");
                    mLocationCount++;
                    updateNotification();
                });
            }

            if (storeInCamera) {
                BaseResponse response = Camera.instance.getController().updateGpsLocation(location);
                if (Logger.DEBUG) {
                    if (response.success) {
                        UpdateGpsLocationResponse r = (UpdateGpsLocationResponse) response;
                        if (Logger.DEBUG)
                            Logger.debug(TAG, "Camera location updated: " + r.gpsInfo);
                    } else {
                        if (Logger.DEBUG)
                            Logger.debug(TAG, "Failed to update camera location: " + response.errMsg);
                    }
                }
            }
        });
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Context context = getApplicationContext();
        mAlarm = (AlarmManager) getSystemService(ALARM_SERVICE);
        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        
        Intent i = new Intent(context, LocationService.class);
        i.setAction(ACTION_UPDATE_LOCATION);
        mAlarmLocationCallback = PendingIntent.getService(context, 0, i, 0);
        mNotificationManager = NotificationManagerCompat.from(context);
        updateConfig();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (!checkLocationPermission(getApplicationContext())) {
            stopSelf();
            return  START_NOT_STICKY;
        }

        updateNotification();

        String action = intent != null ?  intent.getAction() : null;
        if (action == null) {
            return START_NOT_STICKY;
        }

        if (Logger.DEBUG) Logger.debug(TAG, "onStartCommand:" + action);

        switch (action) {
            case ACTION_UPDATE_LOCATION:
                startLocationListener();
                break;
            case ACTION_STOP_LOCATION_UPDATES:
                stopLocationListener(false);
                mAlarm.cancel(mAlarmLocationCallback);
                mNotificationManager.cancel(NOTIFICATION_ID);
                stopForeground(true);
                stopSelf();
                break;
            case ACTION_UPDATE_CONFIG:
                stopLocationListener(false);
                updateConfig();
                startLocationListener();
                break;
        }

        // Passive updates are always on.
        if (mLocationManager == null) {
            mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            mPassiveLocationListener = new LocationListener(this, LocationManager.PASSIVE_PROVIDER);
            mLocationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 60000,
                    mMinimumDistance / 2, mPassiveLocationListener);
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (Logger.DEBUG) Logger.debug(TAG, "onDestroy");

        if (mPassiveLocationListener != null && mLocationManager != null) {
            mLocationManager.removeUpdates(mPassiveLocationListener);
            mPassiveLocationListener.mService = null;
            mPassiveLocationListener = null;
        }

        stopLocationListener(false);
        mAlarm.cancel(mAlarmLocationCallback);

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static boolean checkLocationPermission(Context context) {
        if (!Utils.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            if (Logger.DEBUG) Logger.debug(TAG, "Missing location permission.");
            Toast.makeText(context.getApplicationContext(), "Please grant location permission to Pentax Gallery", Toast.LENGTH_LONG).show();
            return  false;
        }

        return  true;
    }

    private void acquireWakeLock() {
            if (mWakeLock == null) {
                mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, BuildConfig.APPLICATION_ID + ":location");
                mWakeLock.setReferenceCounted(false);
            }
            if(!mWakeLock.isHeld()) {
                mWakeLock.acquire(Math.round(mGpsTimeout * 1.5) * 1000);
                if (Logger.DEBUG) Logger.debug(TAG, "Wakelock acquired");
            }
    }

    private void releaseWakeLock() {
        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
            mWakeLock = null;
            if (Logger.DEBUG) Logger.debug(TAG, "Wakelock released.");
        }
    }

    private void logLocation(Location location, String message) {
        if(Logger.DEBUG) {
            String locationStr = "NOLOC";
            if (location != null) locationStr = location.toString();
            Logger.debug(TAG, message + ": " + locationStr);
        }
    }

    private boolean isBetterLocation(Location location, Location currentBestLocation) {
        return isBetterLocation(location, currentBestLocation, mMinTimeDelta, mMinimumAccuracy, mMaxReasonableSpeed);
    }

    private static boolean isBetterLocation(Location location, Location currentBestLocation,
                                           long minTimeDelta, int minimumAccuracy, float maxReasonableSpeed) {

        if (location == null) {
            // A new location is always better than no location
            return false;
        }

        if (location.getAccuracy() > minimumAccuracy) {
            if(Logger.DEBUG) Logger.debug(TAG, "Location below min accuracy of %d meters", minimumAccuracy);
            return false;
        }

        if (currentBestLocation == null) {
            // A new location is always better than no location
            return true;
        }


        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(),
                currentBestLocation.getProvider());

        long timeDelta = location.getTime() - currentBestLocation.getTime();

        if(isFromSameProvider || !isFromGps(location)) {
            float meters = location.distanceTo(currentBestLocation);
            long seconds = timeDelta / 1000L;
            float speed = meters / seconds;
            if (speed > maxReasonableSpeed) {
                if (Logger.DEBUG)
                    Logger.debug(TAG, "Super speed detected. %f meters from last location", meters);
                return false;
            }
        }

        // Check whether the new location fix is newer or older
        boolean isSignificantlyNewer = timeDelta > minTimeDelta;
        boolean isSignificantlyOlder = timeDelta < -minTimeDelta;
        boolean isNewer = timeDelta > 0;

        // If it's been more than two minutes since the current location, use the new location
        // because the user has likely moved
        if (isSignificantlyNewer) {
            return true;
            // If the new location is more than two minutes older, it must be worse
        } else if (isSignificantlyOlder) {
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }
        return false;
    }

    private static boolean isFromGps(Location location) {
        return LocationManager.GPS_PROVIDER.equals(location.getProvider());
    }

    private static boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }

    private Notification createNotification(String contentText, String contentTitle, long when, PendingIntent contentIntent) {

        if (mStopPendingIntent == null) {
            Intent i = new Intent(getApplicationContext(), LocationService.class);
            i.setAction(ACTION_STOP_LOCATION_UPDATES);
            mStopPendingIntent = PendingIntent.getService(getApplicationContext(), 0, i, 0);
        }

        NotificationCompat.Builder notificationBuilder = (new NotificationCompat.Builder(getApplicationContext(), MyApplication.NOTIFICATION_CHANNEL_ID)).
                setAutoCancel(false).
                setOngoing(true).
                setContentTitle(contentTitle).
                setContentText(contentText).
                setSubText("Location Service").
                setSmallIcon(R.drawable.ic_sync_white_24dp).
                addAction(R.drawable.ic_cancel_white_24dp,  getString(R.string.stop_label), mStopPendingIntent).
                setShowWhen(when > 0);


        if (contentIntent != null) {
            notificationBuilder.setContentIntent(contentIntent);
        }

        if (when > 0) {
            notificationBuilder.setWhen(when);
        }

        return  notificationBuilder.build();
    }

    private void updateNotification() {
        long when = 0;
        int accuracy = 0;
        String provider = "-";
        String contentText = "Updating location...";
        String contentTitle = "Location Service";

        PendingIntent mapPendingIntent = null;
        if (mLastSavedLocation != null) {

            Uri mapUri = Uri.parse(String.format(Locale.US, "geo:%f,%f?z=%d", mLastSavedLocation.getLatitude(),
                    mLastSavedLocation.getLongitude(), 18));

            if (mMapIntent == null) {
                mMapIntent = new Intent(Intent.ACTION_VIEW, mapUri);
                mMapIntentComponent = mMapIntent.resolveActivity(getPackageManager());
            }

            if (mMapIntentComponent != null) {
                mMapIntent.setData(mapUri);
                mapPendingIntent = PendingIntent.getActivity(this, 0, mMapIntent, 0);
            }

            when = mLastSavedLocation.getTime();
            accuracy = Math.round(mLastSavedLocation.getAccuracy());
            provider = mLastSavedLocation.getProvider().substring(0, 1);

            contentText = getString(R.string.location_service_notification_content,
                    mLocationCount, accuracy, provider);

            contentTitle = (mLastSavedLocation != null ?
                    String.format(Locale.US, "Last Location: %f,%f", mLastSavedLocation.getLatitude(), mLastSavedLocation.getLongitude()) :
                    getString(R.string.app_name));
        }

        startForeground(NOTIFICATION_ID, createNotification(contentText, contentTitle, when, mapPendingIntent));
    }

    private static class LocationListener implements android.location.LocationListener
            /*, com.google.android.gms.location.LocationListener*/ {

        private static final String TAG = "LocationListener";

        LocationService mService;
        String mProvider;

        public LocationListener(LocationService service, String provider) {
            mService = service;
            mProvider = provider;
        }

        @Override
        public void onLocationChanged(Location location) {
            if(Logger.DEBUG) Logger.debug(TAG, "onLocationChanged:%s", mProvider);
            if(mService != null) mService.handleLocation(location, mProvider);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }
    }
}