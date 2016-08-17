/**
 * GBIT Map Share for Pebble
 * Copyright (c) Ben Caller 2016
 */

package caller.pebble.mapper;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.PebbleKit.PebbleAckReceiver;
import com.getpebble.android.kit.PebbleKit.PebbleNackReceiver;
import com.getpebble.android.kit.util.PebbleDictionary;

import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

public class ShareIntentReceiver extends Activity {
    private final static UUID PEBBLE_APP_UUID = UUID.fromString("6f983a89-f645-4a11-aefd-3b84dd8946f0");
    private static final int KEY_CMD = 1;
    private static final String VALUE_CMD = "insert";
    private static final int KEY_LATITUDE = 20;
    private static final int KEY_LONGITUDE = 21;
    private static final int KEY_PLACE = 22;
    private static final int KEY_SENT_FROM = 23;
    private static final int MAX_TRIES_SEND = 100;

    private static final boolean LOGGING = false;

    private BroadcastReceiver receivedAckHandler;
    private BroadcastReceiver receivedNackHandler;

    @Override
    protected void onStart() {
        final Intent intent = getIntent();
        Bundle bundle = intent.getExtras();
        boolean andDie = false;
        if(bundle != null) {
            final String name = bundle.get(Intent.EXTRA_SUBJECT).toString();
            String[] etext_parts = bundle.get(Intent.EXTRA_TEXT).toString().split("\n");
            final String url = etext_parts[etext_parts.length - 1];
            if(LOGGING) Log.i(getLocalClassName(), name);
            if(LOGGING) Log.i(getLocalClassName(), url);

            if(!android.util.Patterns.WEB_URL.matcher(url).matches()) {
                Toast.makeText(this, "Sorry, only sharing from Google Maps is currently supported", Toast.LENGTH_LONG).show();
                andDie = true;
            } else if(PebbleKit.isWatchConnected(getApplicationContext())) {
                PebbleKit.startAppOnPebble(getApplicationContext(), PEBBLE_APP_UUID);
                new LatLngFromGmapsUrl(name).execute(url);
            } else {
                Toast.makeText(this, R.string.pebble_disconnected, Toast.LENGTH_LONG).show();
                andDie = true;
            }
        }
        super.onStart();
        if(andDie)
            finish();
    }

    class LatLngFromGmapsUrl extends AsyncTask<String, Void, LatLngResult> {
        private String name;

        public LatLngFromGmapsUrl(String name) {
            this.name = name;
        }

        @Override
        protected LatLngResult doInBackground(String... params) {
            return LatLngResult.getLatLngFromUrl(params[0]);
        }

        @Override
        protected void onPostExecute(LatLngResult latlng) {
            if(LOGGING) Log.i("Latlng", latlng.toString());
            // Preemptively start app
            PebbleKit.startAppOnPebble(getApplicationContext(), PEBBLE_APP_UUID);

            // Actually send the result to Pebble
            final PebbleDictionary data = new PebbleDictionary();
            data.addString(KEY_CMD, VALUE_CMD);
            data.addString(KEY_LATITUDE, latlng.getLatitude());
            data.addString(KEY_LONGITUDE, latlng.getLongitude());
            data.addString(KEY_SENT_FROM, getString(R.string.timeline_body));
            if(!this.name.equals(getString(R.string.generic_location)))
                data.addString(KEY_PLACE, this.name);

            startAppSendUntilAcked(this.name, data);
        }

    }

    private void startAppSendUntilAcked(final String name, final PebbleDictionary data) {
        final FinalInt transactionCounter = new FinalInt(1);
        Toast.makeText(this, getString(R.string.sending_begin, name), Toast.LENGTH_LONG).show();

        receivedAckHandler = PebbleKit.registerReceivedAckHandler(getApplicationContext(), new PebbleAckReceiver(PEBBLE_APP_UUID) {

            @Override
            public void receiveAck(Context context, int transactionId) {
                if(LOGGING) Log.i(getLocalClassName(), "Received ack for transaction " + transactionId);
                boolean alreadyFinished = transactionCounter.getValue() == -1;
                transactionCounter.setValue(-1);
                if (transactionId > -1 && !alreadyFinished)
                    Toast.makeText(getApplicationContext(), getString(R.string.sending_complete, name), Toast.LENGTH_LONG).show();
                tryUnregister(context, receivedNackHandler);
                tryUnregister(context, receivedAckHandler);
                finish();
            }
        });

        receivedNackHandler = PebbleKit.registerReceivedNackHandler(getApplicationContext(), new PebbleNackReceiver(PEBBLE_APP_UUID) {
            @Override
            public void receiveNack(Context context, int transactionId) {
                if(LOGGING) Log.i(getLocalClassName(), "Received nack for transaction " + transactionId);
                if (transactionId != -1 && transactionId == transactionCounter.getValue()) {
                    if(transactionId >= MAX_TRIES_SEND) {
                        Toast.makeText(getApplicationContext(), getString(R.string.send_failed, name), Toast.LENGTH_SHORT).show();
                        tryUnregister(context, receivedNackHandler);
                        tryUnregister(context, receivedAckHandler);
                        finish();
                    } else {
                        final Handler handler = new Handler();
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                // If not already acked somehow
                                if (transactionCounter.getValue() != -1) {
                                    transactionCounter.increment();
                                    // Repeat message ad infinitum (ok, MAX_TRIES_SEND)
                                    if (transactionCounter.getValue() % 20 == 19)
                                        Toast.makeText(getApplicationContext(), getString(R.string.sending_pending, name), Toast.LENGTH_SHORT).show();
                                    PebbleKit.startAppOnPebble(getApplicationContext(), PEBBLE_APP_UUID);
                                    if (LOGGING)
                                        Log.d(getLocalClassName(), "resending with transaction " + transactionCounter.getValue());
                                    PebbleKit.sendDataToPebbleWithTransactionId(getApplicationContext(), PEBBLE_APP_UUID, data, transactionCounter.getValue());
                                }
                            }
                        }, 750);
                    }
                }
            }

        });
        if(LOGGING) Log.d(getLocalClassName(), "sending with transaction " + transactionCounter.getValue());
        PebbleKit.sendDataToPebbleWithTransactionId(getApplicationContext(), PEBBLE_APP_UUID, data, transactionCounter.getValue());
    }

    /***
     * Unregister a BroadcastReceiver if it is registered
     */
    static void tryUnregister(Context context, BroadcastReceiver broadcastReceiver) {
        try {
            context.unregisterReceiver(broadcastReceiver);
        } catch (IllegalArgumentException ignored) {/*Already unregistered*/}
    }

    @Override
    protected void onStop() {
        tryUnregister(this, receivedNackHandler);
        tryUnregister(this, receivedAckHandler);
        super.onStop();
    }
}
