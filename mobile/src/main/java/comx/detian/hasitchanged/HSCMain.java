package comx.detian.hasitchanged;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.FragmentManager;
import android.app.PendingIntent;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;


public class HSCMain extends Activity
        implements NavigationDrawerFragment.NavigationDrawerCallbacks {

    protected static final String AUTHORITY = "comx.detian.hasitchanged.provider";
    //RFC 822 date format
    static final SimpleDateFormat df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss");
    static final String PREFERENCE_PREFIX = "HSCPREFERENCE.";
    private static Account mAccount = null;
    private static PendingIntent exactSyncIntent = null;
    private static PendingIntent inexactSyncIntent = null;

    //TODO figure out why getBroadcast is returning different PendingIntents, current is workaround
    public static PendingIntent getExactSyncIntent(Context context) {
        if (exactSyncIntent==null){
            exactSyncIntent = PendingIntent.getBroadcast(context, 1, new Intent(context, HSCAlarmSync.class), PendingIntent.FLAG_CANCEL_CURRENT);
        }
        return exactSyncIntent;
    }

    public static PendingIntent getInExactSyncIntent(Context context) {
        if (inexactSyncIntent==null){
            inexactSyncIntent = PendingIntent.getBroadcast(context, 2, new Intent(context, HSCAlarmSync.class), PendingIntent.FLAG_CANCEL_CURRENT);
        }
        return inexactSyncIntent;
    }

    /*public static Intent getInexactIntent() {
        return inexactIntent;
    }

    private static Intent inexactIntent;*/
    ContentResolver mResolver;
    /**
     * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
     */
    private NavigationDrawerFragment mNavigationDrawerFragment;
    /**
     * Used to store the last screen title. For use in {@link #restoreActionBar()}.
     */
    private CharSequence mTitle;

    public static Account getAccount(Context context) {
        if (mAccount == null) {
            Account[] accounts = ((AccountManager) context.getSystemService(ACCOUNT_SERVICE)).getAccountsByType("HSC.comx");
            if (accounts.length >= 1) {
                //account already made
                mAccount = accounts[0];
                assert (accounts.length == 1);
            } else if (accounts.length == 0) {
                mAccount = CreateSyncAccount(context);
            }
        }
        return mAccount;
    }

    //in Milliseconds
    static long calculateTimeToTrigger(ArrayList<String> targetTimes, ArrayList<String> syncTimes) {
        if (BuildConfig.DEBUG && targetTimes.size() != syncTimes.size()) {
            throw new RuntimeException("TargetTimes and syncTimes size doesn't match " + targetTimes.size() + " vs " + syncTimes.size());
        }
        long minTimeToSync = Long.MAX_VALUE;
        for (int i = 0; i < targetTimes.size(); i++) {
            if (syncTimes.get(i) == null) { //this entry has never been synced
                minTimeToSync = 0;
                break;
            }
            long timeDifference = calcTimeDiff(syncTimes.get(i), targetTimes.get(i));
            if (timeDifference < minTimeToSync) {
                minTimeToSync = timeDifference;
            }
        }
        return minTimeToSync;
    }

    /**
     * @param lastSyncTime - DB stored GMT timestamp
     * @param targetT      - User given time string for sync interval
     * @return time in mills of how far in the in future the next sync should be
     */
    static long calcTimeDiff(String lastSyncTime, String targetT) {
        long elapsedTime;
        String[] pieces = targetT.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");
        long targetTime = 0;
        long temp = -1;
        for (int j = 0; j < pieces.length; j++) {
            //Start with number
            if (temp < 0) {
                try {
                    temp = Long.parseLong(pieces[j]);
                } catch (NumberFormatException e) {
                    //Ignore
                }
            } else { //get units for number
                pieces[j] = pieces[j].trim().toLowerCase();
                //Grumble Grumble
                if (pieces[j].endsWith("s")) {
                    pieces[j] = pieces[j].substring(0, pieces[j].length() - 1);
                }
                long temp2 = temp;
                temp = -1;
                if (pieces[j].equals("milli")) {
                    targetTime += temp2;
                } else if (pieces[j].equals("sec")) {
                    targetTime += (temp2 * 1000);
                } else if (pieces[j].equals("min")) {
                    targetTime += (temp2 * 1000 * 60);
                } else if (pieces[j].equals("hour")) {
                    targetTime += (temp2 * 1000 * 60 * 60);
                } else if (pieces[j].equals("day")) {
                    targetTime += (temp2 * 1000 * 60 * 60 * 24);
                } else if (pieces[j].equals("week")) {
                    targetTime += (temp2 * 1000 * 60 * 60 * 24 * 7);
                } else {
                    //Not valid unit, continue trying to get valid num
                    temp = temp2;
                }
            }
        }
        try {
            elapsedTime = (new Date()).getTime() - df.parse(lastSyncTime).getTime();
        } catch (Exception e) {
            elapsedTime = targetTime;
        }

        if (BuildConfig.DEBUG && elapsedTime<0){
            throw new RuntimeException("ElapsedTime" + lastSyncTime + " ::: " + df.format(new Date()));
        }


        long timeDifference = targetTime - elapsedTime;

        Log.d("CalcFuture", " Item has passed " + elapsedTime + " on its way to " + targetTime);
        Log.d("CalcFuture", "Item has " + timeDifference + " to go before sync");

        return timeDifference;
    }

    /**
     * Request a Sync to the content resolver
     *
     * @param context the application context
     * @param idToSync 0 to force sync all, -1 to only sync those necessary; otherwise sync idToSync
     */
    static void requestSyncNow(final Context context, long idToSync) {
        /*if (ContentResolver.isSyncPending(HSCMain.getAccount(context), AUTHORITY) ||
                ContentResolver.isSyncActive(HSCMain.getAccount(context), AUTHORITY)) {
            Log.d("SYNC: Manual", "Sync pending, cancelling");
            ContentResolver.cancelSync(HSCMain.getAccount(context), AUTHORITY);
        }*/
        final Bundle params = new Bundle();
        params.putBoolean(
                ContentResolver.SYNC_EXTRAS_MANUAL, true);
        params.putBoolean(
                ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        if (idToSync == 0) {
            params.putBoolean("FORCE_SYNC_ALL", true);
            Toast.makeText(context, "Checking All for Changes", Toast.LENGTH_SHORT).show();
        } else {
            params.putBoolean("FORCE_SYNC_" + idToSync, true);
            Toast.makeText(context, "Checking this for Changes", Toast.LENGTH_SHORT).show();
        }
        //ContentResolver.requestSync(HSCMain.getAccount(context), AUTHORITY, params);
        //TODO consider using AsyncTask instead
        new Thread(new Runnable() {
            @Override
            public void run() {
                ContentProviderClient client = context.getContentResolver().acquireContentProviderClient(HSCMain.AUTHORITY);
                HSCSyncAdapter.performSyncNow(context, params, client);
                client.release();
            }
        }).start();
    }

    /**
     * Returns the next wakeup in milliseconds
     * @param context
     * @param methodToCheck
     * @param inexact
     * @return
     */
    protected static long getNextSyncTime(Context context, String methodToCheck, boolean inexact) {
        Cursor cursor = context.getContentResolver().query(DatabaseOH.getBaseURI(), null, null, null, null);

        ArrayList<String> targetTimes = new ArrayList<String>();
        ArrayList<String> syncTimes = new ArrayList<String>();

        //Skip first dummy
        cursor.moveToNext();

        while (cursor.moveToNext()) {
            long siteId = cursor.getLong(DatabaseOH.COLUMNS._id.ordinal());

            //for (int i = 1; i<mAdapter.getItemCount(); i++) {
            //long siteId = mAdapter.getItemId(i);
            SharedPreferences sp = context.getSharedPreferences(PREFERENCE_PREFIX + siteId, MODE_MULTI_PROCESS);
            if (sp.getString("pref_site_sync_method", null).equals(methodToCheck)
                    && sp.getString("pref_site_sync_type", null).equals("elapsed_time")
                    && sp.getBoolean("pref_site_sync_allow_inexact", true) == inexact) {
                if (sp.getString("pref_site_url", "").length()!=0
                        &&!sp.getString("pref_site_sync_time_elapsed", "never").equals("never")) {
                    targetTimes.add(sp.getString("pref_site_sync_time_elapsed", "never"));
                    //System.out.println(methodToCheck+ cursor.getString(DatabaseOH.COLUMNS.URL.ordinal()));
                    syncTimes.add(cursor.getString(DatabaseOH.COLUMNS.LUDATE.ordinal()));
                }
            }
        }
        cursor.close();

        long nextSync = calculateTimeToTrigger(targetTimes, syncTimes);
        Log.d("CalcFuture", methodToCheck + inexact + "Calculated sync for " + nextSync + " seconds in the future");
        nextSync = nextSync <= 0 ? 1 : nextSync;

        return nextSync;
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    protected static void updateNextSyncTime(Context context) {
        ContentResolver.removePeriodicSync(getAccount(context), AUTHORITY, new Bundle());
        long nextSyncTime = getNextSyncTime(context, "sync", true);
        if (nextSyncTime != Long.MAX_VALUE) {
            nextSyncTime/=1000; //Sync needs to be in seconds
            //Prevent syncs from being too close together
            nextSyncTime = nextSyncTime < 60 ? 120 : nextSyncTime;
            ContentResolver.addPeriodicSync(getAccount(context), AUTHORITY, new Bundle(), nextSyncTime);
        }

        //TODO check to make sure can schedule the same PendingIntent multiple times (same IntentSender?)
        //TODO handle per url with independent alarms
        AlarmManager alarmMgr = (AlarmManager) context.getSystemService(ALARM_SERVICE);

        //PendingIntent syncIntent = PendingIntent.getBroadcast(context, 1, getSyncIntent(context), PendingIntent.FLAG_NO_CREATE);
        alarmMgr.cancel(getExactSyncIntent(context));
        long nextExactAlarmTime = getNextSyncTime(context, "alarm", false);
        if (nextExactAlarmTime != Long.MAX_VALUE) {
            alarmMgr.setExact(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + nextExactAlarmTime, getExactSyncIntent(context));
            Log.d("SYNC_STATUS", "Setting exact for "+nextExactAlarmTime+" millis" + getExactSyncIntent(context));
        }

        //PendingIntent inexactSyncIntent = PendingIntent.getBroadcast(context, 2, getSyncIntent(context), PendingIntent.FLAG_NO_CREATE);
        alarmMgr.cancel(getInExactSyncIntent(context));
        long nextInexactAlarmTime = getNextSyncTime(context, "alarm", true);
        if (nextInexactAlarmTime != Long.MAX_VALUE) {
            alarmMgr.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + nextInexactAlarmTime, getInExactSyncIntent(context));
        }

        Log.d("SYNC_STATUS: ", (nextSyncTime==Long.MAX_VALUE?"NEVER":nextSyncTime) + " " + (nextExactAlarmTime==Long.MAX_VALUE?"NEVER":nextExactAlarmTime) + " " + (nextInexactAlarmTime==Long.MAX_VALUE?"NEVER":nextInexactAlarmTime));
    }

    public static Account CreateSyncAccount(Context context) {
        Account out = new Account("Dummy", "HSC.comx");
        AccountManager am = (AccountManager) context.getSystemService(ACCOUNT_SERVICE);

        if (am.addAccountExplicitly(out, null, null)) {
            return out;
        } else {
            return null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hscmain);

        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getFragmentManager().findFragmentById(R.id.navigation_drawer);
        mTitle = "HSC?";//getTitle();

        mResolver = getContentResolver();

        ContentResolver.setSyncAutomatically(getAccount(this), AUTHORITY, true);

        Bundle params = new Bundle();

        //ContentResolver.addPeriodicSync(mAccount, AUTHORITY, params, 120);

        df.setTimeZone(TimeZone.getTimeZone("GMT"));

        // Set up the drawer.
        mNavigationDrawerFragment.setUp(
                R.id.navigation_drawer,
                (DrawerLayout) findViewById(R.id.drawer_layout));
    }

    @Override
    public void onNavigationDrawerItemSelected(int position, long id) {
        // update the main content by replacing fragments
        FragmentManager fragmentManager = getFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.container, id == 0 ? OverviewFragment.newInstance() : SiteSettingsFragment.newInstance(id))
                .commit();
    }

    public void restoreActionBar() {
        ActionBar actionBar = getActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(mTitle);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!mNavigationDrawerFragment.isDrawerOpen()) {
            // Only show items in the action bar relevant to this screen
            // if the drawer is not showing. Otherwise, let the drawer
            // decide what to show in the action bar.
            getMenuInflater().inflate(R.menu.hscmain, menu);
            restoreActionBar();
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

}