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

package com.hmsoft.pentaxgallery.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TaskExecutor 
{
	private static final String TAG = "TaskExecutor";
	
	private static Handler sExecuteOnUIHandler = null;
	private static ExecutorService sSingleThreadExecutor;

	private TaskExecutor() {
		
	}
	
	public static void init() {
		if(sExecuteOnUIHandler == null && isMainUIThread())
			sExecuteOnUIHandler = new Handler();
	}
	
	public static boolean isMainUIThread() {
        return Looper.getMainLooper().getThread().equals(Thread.currentThread());
	}
	
	public static Thread executeOnNewThread(Runnable runable){
		Thread thread = new Thread(runable);
		thread.start();
		return thread;
	}

	public static Executor getExecutor() {
		if(sSingleThreadExecutor == null) {
			sSingleThreadExecutor = Executors.newSingleThreadExecutor();
		}
		return sSingleThreadExecutor;
	}
	
	public static void executeOnSingleThreadExecutor(Runnable runnable) {
		getExecutor().execute(runnable);
	}
	
	public synchronized static void executeOnUIThread(Runnable runnable) {
		sExecuteOnUIHandler.post(runnable);
	}
	
	public synchronized static void executeOnUIThread(Runnable runnable, double seconds) {
		long m = (long)(seconds * 1000);
		sExecuteOnUIHandler.postDelayed(runnable, m);
	}

	public static void sleep(long milis) {
		if(milis < 1) return;
		try {
			Thread.sleep(milis);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}