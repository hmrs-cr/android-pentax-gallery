/*
 * Copyright (C) 2018 Mauricio Rodriguez (ranametal@users.sf.net)
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

package com.hmsoft.pentaxgallery.camera.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.Build;

import com.hmsoft.pentaxgallery.MyApplication;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public final class HttpHelper {

    public enum RequestMethod {
        DEFAULT(null),
        GET("GET"),
        POST("POST");

        private final String mText;
        RequestMethod(final  String text) {
            mText = text;
        }

        @Override
        public String toString() {
            return mText;
        }
    }

    private HttpHelper() {}

    public static String getStringResponse(String url) {
        return getStringResponse(url, 0,0, RequestMethod.DEFAULT);
    }

    public static String getStringResponse(String url, RequestMethod method) {
        return getStringResponse(url, 0,0, method);
    }

    public static String getStringResponse(String url, int connectTimeoutInSeconds, int readTimeoutInSeconds) {
        return getStringResponse(url, connectTimeoutInSeconds, readTimeoutInSeconds, RequestMethod.DEFAULT);
    }

    public static String getStringResponse(String url, int connectTimeoutInSeconds, int readTimeoutInSeconds, RequestMethod method) {
        try {
                HttpURLConnection urlConnection = (HttpURLConnection) new URL(url).openConnection();
                try {
                    urlConnection.setConnectTimeout(connectTimeoutInSeconds * 1000);
                    urlConnection.setReadTimeout(readTimeoutInSeconds * 1000);
                    if(method != RequestMethod.DEFAULT) {
                        urlConnection.setRequestMethod(method.toString());
                    }
                    InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                    StringBuilder builder = new StringBuilder();
                    for (String line = null; (line = reader.readLine()) != null; ) {
                        builder.append(line).append("\n");
                    }

                    return builder.toString();

                } finally {
                    urlConnection.disconnect();
                }

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean bindToWifi() {
        ConnectivityManager connectivityManager = (ConnectivityManager) MyApplication.ApplicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Network[] networks = connectivityManager.getAllNetworks();

            for (Network network : networks) {
                NetworkInfo networkInfo = connectivityManager.getNetworkInfo(network);
                if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI && networkInfo.isConnected()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        connectivityManager.bindProcessToNetwork(network);
                    } else {
                        connectivityManager.setProcessDefaultNetwork(network);
                    }
                    return true;
                }
            }
        } else {
            NetworkInfo[] networksInfo = connectivityManager.getAllNetworkInfo();
            for(NetworkInfo networkInfo : networksInfo) {
                if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI && networkInfo.isConnected()) {
                    return true;
                }
            }
        }

        return false;
    }


}
