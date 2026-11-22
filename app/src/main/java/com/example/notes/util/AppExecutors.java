package com.example.notes.util;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Application-wide executor pool that offloads database and disk I/O
 * from the main thread to prevent UI freezing and Application Not Responding (ANR) errors.
 */
public class AppExecutors {

    private static final Object LOCK = new Object();
    private static AppExecutors sInstance;

    private final Executor diskIO;
    private final Executor mainThread;

    public AppExecutors(Executor diskIO, Executor mainThread) {
        this.diskIO = diskIO;
        this.mainThread = mainThread;
    }

    public static AppExecutors getInstance() {
        if (sInstance == null) {
            synchronized (LOCK) {
                if (sInstance == null) {
                    sInstance = new AppExecutors(
                            Executors.newSingleThreadExecutor(),
                            new MainThreadExecutor()
                    );
                }
            }
        }
        return sInstance;
    }

    public Executor diskIO() {
        return diskIO;
    }

    public Executor mainThread() {
        return mainThread;
    }

    private static class MainThreadExecutor implements Executor {
        private Handler mainThreadHandler;

        private Handler getHandler() {
            if (mainThreadHandler == null) {
                try {
                    Looper looper = Looper.getMainLooper();
                    if (looper != null) {
                        mainThreadHandler = new Handler(looper);
                    }
                } catch (RuntimeException ignored) {
                    mainThreadHandler = null;
                }
            }
            return mainThreadHandler;
        }

        @Override
        public void execute(@NonNull Runnable command) {
            Handler handler = getHandler();
            if (handler != null) {
                handler.post(command);
            } else {
                // Direct fallback for JVM unit testing environments without Android Looper
                command.run();
            }
        }
    }
}
