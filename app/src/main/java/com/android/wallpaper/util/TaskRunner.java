package com.android.wallpaper.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class TaskRunner {

    private static TaskRunner INSTANCE;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final ExecutorService ioExecutor = new ThreadPoolExecutor(
            2,
            6,
            60L,
            TimeUnit.SECONDS,
            new SynchronousQueue<>());

    private final Handler handler = new Handler(Looper.getMainLooper());

    private TaskRunner() {
    }

    public static TaskRunner getINSTANCE() {
        if (INSTANCE == null) {
            synchronized (TaskRunner.class) {
                if (INSTANCE == null) INSTANCE = new TaskRunner();
            }
        }
        return INSTANCE;
    }

    public interface Callback<R> {
        void onComplete(R result);

        void onError(Exception e);
    }

    public <R> void executeIOAsync(Callable<R> callable, Callback<R> callback) {
        _executeAsync(ioExecutor, callable, callback);
    }

    public <R> void executeAsync(Callable<R> callable, Callback<R> callback) {
        _executeAsync(executor, callable, callback);
    }

    private <R> void _executeAsync(ExecutorService executor, Callable<R> callable, Callback<R> callback) {
        executor.execute(() -> {
            try {
                R result = callable.call();
                handler.post(() -> callback.onComplete(result));
            } catch (Exception e) {
                handler.post(() -> callback.onError(e));
            }
        });
    }
}