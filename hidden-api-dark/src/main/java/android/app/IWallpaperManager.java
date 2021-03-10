package android.app;

import android.os.Binder;
import android.os.IBinder;
import android.content.ComponentName;
import android.os.IInterface;

public interface IWallpaperManager extends IInterface {

    // SecurityException
    void setWallpaperComponent(ComponentName name);

    abstract class Stub extends Binder implements IWallpaperManager {

        public static IWallpaperManager asInterface(IBinder obj) {
            throw new UnsupportedOperationException();
        }
    }
}