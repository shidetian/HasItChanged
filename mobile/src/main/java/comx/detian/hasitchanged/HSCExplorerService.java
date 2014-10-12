package comx.detian.hasitchanged;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class HSCExplorerService extends Service {
    private static HSCSyncAdapter mSA = null;
    private static final Object mSALock = new Object();

    public HSCExplorerService() {

    }

    @Override
    public IBinder onBind(Intent intent) {
        synchronized (mSALock) {
            if (mSA == null) {
                mSA = new HSCSyncAdapter(getApplicationContext(), false);
            }
        }
        return mSA.getSyncAdapterBinder();
    }
}
