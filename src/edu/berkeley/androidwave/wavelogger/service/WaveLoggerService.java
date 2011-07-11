// 
//  WaveLoggerService.java
//  WaveLogger
//  
//  Created by Philip Kuryloski on 2011-06-30.
//  Copyright 2011 University of California, Berkeley. All rights reserved.
// 

package edu.berkeley.androidwave.wavelogger.service;

import edu.berkeley.androidwave.waveclient.*;
import edu.berkeley.androidwave.wavelogger.*;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Date;

/**
 * WaveLoggerService
 * 
 * Continuously log recipe data for a given recipe to sqlite
 * 
 * This is not a binding service, but a stop/start service. It should run in
 * the foreground so the android system does not shut it down when attempting
 * to reclaim memory.
 */
public class WaveLoggerService extends Service {
    
    private static final String TAG = WaveLoggerService.class.getSimpleName();
    
    private static final String ACTION_WAVE_SERVICE = "edu.berkeley.androidwave.intent.action.WAVE_SERVICE";
    
    public static final String RECIPE_IDS_EXTRA = "recipe_ids";
    
    private static final int NOTIFICATION_ID = 1;
    
    private String API_KEY;

    private IWaveServicePublic mWaveService;
    private boolean mBound;
    
    protected DbHelper databaseHelper;
    
    private boolean mLogging;
    
    protected Intent startIntent;

    @Override
    public void onCreate() {
        API_KEY = this.getPackageName();
        
        mWaveService = null;
        mBound = false;
        mLogging = false;
        
        databaseHelper = new DbHelper(this);
        
        // we cannot bind to the WaveService in onCreate, so we must wait
        // until onStartCommand

        // move this service into the foreground
        NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        int icon = R.drawable.cal;
        CharSequence tickerText = "WaveLogger logging started";
        long when = System.currentTimeMillis();
        Notification notification = new Notification(icon, tickerText, when);
        
        CharSequence contentTitle = "WaveLogger";
        CharSequence contentText = "logging started";
        Intent associatedIntent = new Intent(this, WaveLogger.class);
        PendingIntent i = PendingIntent.getActivity(this,
                                                    0, // requestCode
                                                    associatedIntent,
                                                    PendingIntent.FLAG_UPDATE_CURRENT);
        notification.setLatestEventInfo(this,
                                        contentTitle,
                                        contentText,
                                        i);
        
        this.startForeground(NOTIFICATION_ID, notification);
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        synchronized(this) {
            if (!mLogging) {
                startIntent = intent;
                // bind to WaveService
                Intent i = new Intent(ACTION_WAVE_SERVICE);
                if (bindService(i, mConnection, Context.BIND_AUTO_CREATE)) {
                    mBound = true;
                } else {
                    Log.d(getClass().getSimpleName(), "Could not bind with "+i);
                    Toast.makeText(this, "Could not connect to the WaveService!", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "WaveLoggerService is already logging.", Toast.LENGTH_SHORT);
                stopSelf();
            }
        }
        
        return START_STICKY;
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        // Binding is not provided
        return null;
    }
    
    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        
        // stop logging if we are logging
        synchronized(this) {
            if (mLogging) {
                if (mWaveService != null) {
                    Toast.makeText(this, "WaveLogger stopping logging", Toast.LENGTH_SHORT);
                    Log.d(TAG, "WaveLogger stopping logging");
                    try {
                        mWaveService.unregisterRecipeOutputListener(API_KEY, WaveLogger.ACCEL_RECIPE_ID);
                        mWaveService.unregisterRecipeOutputListener(API_KEY, WaveLogger.LOC_RECIPE_ID);
                    } catch (RemoteException e) {
                        Log.d(TAG, "lost connection to the service");
                    }
                }
            }
        }
        
        databaseHelper.closeDatabase();
        
        // disconnect from the WaveService
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }
    
    protected void afterWaveServiceBound() {
        synchronized(this) {
            // extract the recipe ids from the intent extras, and begin listening
            // and logging
            ArrayList<String> recipeIds = startIntent.getStringArrayListExtra(RECIPE_IDS_EXTRA);
            Toast.makeText(this, "Logging data for the following recipes: "+recipeIds, Toast.LENGTH_SHORT);
            Log.d(TAG, "Logging data for the following recipes: "+recipeIds);
            for (String id : recipeIds) {
                IWaveRecipeOutputDataListener outputListener;
                if (id.equals(WaveLogger.ACCEL_RECIPE_ID)) {
                    outputListener = accelOutputListener;
                } else {
                    outputListener = locOutputListener;
                }
            
                try {
                    if (!mWaveService.registerRecipeOutputListener(API_KEY, id, outputListener)) {
                        Toast.makeText(this, "Error requesting data for recipe "+id, Toast.LENGTH_SHORT);
                        Log.d(TAG, "Error requesting data for recipe "+id);
                    }
                } catch (RemoteException e) {
                    Log.d(TAG, "lost connection to the service");
                }
            }
            mLogging = true;
        }
    }

    // Nested ServiceConnection subclass
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG, String.format("ServiceConnection.onServiceConnected(%s, %s)", className, service));
            mWaveService = IWaveServicePublic.Stub.asInterface(service);
            afterWaveServiceBound();
        }
        
        public void onServiceDisconnected(ComponentName className) {
            Log.d(TAG, String.format("ServiceConnection.onServiceDisconnected(%s)", className));
            mWaveService = null;
        }
    };
    
    // Recipe output listeners
    private IWaveRecipeOutputDataListener accelOutputListener = new IWaveRecipeOutputDataListener.Stub() {
        public void receiveWaveRecipeOutputData(ParcelableWaveRecipeOutputData wrOutput) {
            // log the received data to the appropriate SQLite table
            databaseHelper.insertAccelData(new Date(), wrOutput.getTime(), wrOutput.valuesAsMap());
            // Log.v(TAG, "wrOutput => " + wrOutput);
        }
    };
    
    private IWaveRecipeOutputDataListener locOutputListener = new IWaveRecipeOutputDataListener.Stub() {
        public void receiveWaveRecipeOutputData(ParcelableWaveRecipeOutputData wrOutput) {
            // log the received data to the appropriate SQLite table
            databaseHelper.insertLocData(new Date(), wrOutput.getTime(), wrOutput.valuesAsMap());
            // Log.v(TAG, "wrOutput => " + wrOutput);
        }
    };
    
    public synchronized boolean isBound() {
        return (mBound && (mWaveService != null));
    }
}