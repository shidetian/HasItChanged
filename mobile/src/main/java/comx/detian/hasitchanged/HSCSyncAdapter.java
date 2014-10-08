package comx.detian.hasitchanged;

import android.accounts.Account;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URI;
import java.net.URL;
import java.util.Calendar;
import java.util.Objects;

public class HSCSyncAdapter extends AbstractThreadedSyncAdapter {
    //ContentResolver mCR;
    RequestQueue queue;

    public HSCSyncAdapter(Context context, boolean autoInitialize){
        super(context, autoInitialize);

        /*if (context!=null)
            mCR = context.getContentResolver();
        else
            Log.d("SyncAdapter: Constructor", "Context is null");*/

        //Log.d("SyncAdapter: Constructor", context.toString());
        queue = Volley.newRequestQueue(context);
    }

    public HSCSyncAdapter(Context context, boolean autoInitialize, boolean allowParallelSyncs){
        super(context, autoInitialize, allowParallelSyncs);

        //mCR = context.getContentResolver();
    }

    @Override
    public void onPerformSync(Account account, Bundle bundle, String s, final ContentProviderClient contentProviderClient, SyncResult syncResult) {
        //TODO
        Log.d("Sync: onPerform", "called");
        //Intent i = new Intent(SYNC_FINISHED);
        //sendBroadcast(i);

        try {
            Cursor cursor = contentProviderClient.query(baseURI, null, null, null, null);
            Log.d("SyncAdapter: onPerform", "Iterating....");
            while (cursor.moveToNext()){
                final long id = cursor.getLong(0);
                final String url = cursor.getString(2) +"://"+cursor.getString(1);
                final int lastHash = cursor.getInt(4);
                Log.d("SyncAdapter: onPerform", cursor.getInt(0) + url + cursor.getString(3));
                /*final HSCRequest request = new HSCRequest(Request.Method.GET, url, new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        int hashCode = response.hashCode();
                        Log.d("SyncAdapter: "+url, "Hash is "+hashCode + " vs "+lastHash);
                        //TODO check to make sure consistent hashcode across runs
                    }
                }, new Response.ErrorListener(){
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.d("SyncAdapter: " + url, " Error is " + error.getMessage());
                    }
                });
                //queue.add(request);*/
                String data = downloadUrl(url, 15000, 10000, "GET");
                int hashCode = data.hashCode();
                if (lastHash != hashCode){
                    createNotification(url, "Has changed.");
                    ContentValues updateValues = new ContentValues();
                    updateValues.put("LUDATE", HSCMain.df.format(Calendar.getInstance().getTime()));
                    updateValues.put("HASH", hashCode);
                    try {
                        contentProviderClient.update(ContentUris.withAppendedId(DatabaseOH.baseURI, id), updateValues, "ID=?", new String[]{""+id});
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }

                Log.d("SyncAdapter: "+url, "Hash is "+hashCode + " vs "+lastHash);
            }

            cursor.close();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    // Given a URL, establishes an HttpUrlConnection and retrieves
    // the web page content as a InputStream, which it returns as
    // a string.
    private static String downloadUrl(String myurl, int readTimeout, int connectTimeout, String method) {
        InputStream is = null;
        // Only display the first 500 characters of the retrieved
        // web page content.
        int len = 500;
        String contentAsString = "";
        try {
            URL url = new URL(myurl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(readTimeout /* milliseconds */);
            conn.setConnectTimeout(connectTimeout /* milliseconds */);
            conn.setRequestMethod(method);
            conn.setDoInput(true);
            // Starts the query
            conn.connect();
            int response = conn.getResponseCode();
            Log.d("DownloadURL", "The response is: " + response);
            is = conn.getInputStream();

            // Convert the InputStream into a string
            //contentAsString = readIt(is, len);
            contentAsString = convertStreamToString(is);

            // Makes sure that the InputStream is closed after the app is
            // finished using it.
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (ProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return contentAsString;
    }

    // Reads an InputStream and converts it to a String.
    public String readIt(InputStream stream, int len) throws IOException, UnsupportedEncodingException {
        Reader reader = null;
        reader = new InputStreamReader(stream, "UTF-8");
        char[] buffer = new char[len];
        reader.read(buffer);
        return new String(buffer);
    }

    static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is, "UTF-8").useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    private void createNotification(String title, String content) {
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this.getContext())
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_launcher);

        Intent intent = new Intent(this.getContext(), HSCMain.class);

        // The stack builder object will contain an artificial back stack for the
        // started Activity.
        // This ensures that navigating backward from the Activity leads out of
        // your application to the Home screen.
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this.getContext());
        // Adds the back stack for the Intent (but not the Intent itself)
        stackBuilder.addParentStack(HSCMain.class);
        // Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(intent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
        mBuilder.setContentIntent(resultPendingIntent);
        NotificationManager mNotificationManager =
                (NotificationManager) this.getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        // mId allows you to update the notification later on.
        mNotificationManager.notify(42, mBuilder.build());
    }
}
