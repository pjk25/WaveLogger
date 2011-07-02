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
    
    private final String API_KEY = this.getPackageName();

    private IWaveServicePublic mWaveService;
    private boolean mBound;
    
    protected DbHelper databaseHelper;
    
    private boolean mLogging;

    @Override
    public void onCreate() {
        mWaveService = null;
        mBound = false;
        mLogging = false;
        
        databaseHelper = new DbHelper(this);
        
        // bind to WaveService
        Intent i = new Intent(ACTION_WAVE_SERVICE);
        if (bindService(i, mConnection, Context.BIND_AUTO_CREATE)) {
            mBound = true;
            // Toast.makeText(this, "Connected to WaveService", Toast.LENGTH_SHORT).show();
        } else {
            Log.d(getClass().getSimpleName(), "Could not bind with "+i);
            Toast.makeText(this, "Could not connect to the WaveService!", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        
        synchronized(this) {
            if (!mLogging) {
                // extract the recipe ids from the intent extras, and begin listening
                // and logging
        
                ArrayList<String> recipeIds = intent.getStringArrayListExtra(RECIPE_IDS_EXTRA);
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
                        }
                    } catch (RemoteException e) {
                        Log.d(TAG, "lost connection to the service");
                    }
                }
                mLogging = true;
            } else {
                Toast.makeText(this, "WaveLoggerService is already logging.", Toast.LENGTH_SHORT);
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
        // stop logging if we are logging
        synchronized(this) {
            if (mLogging) {
                try {
                    mWaveService.unregisterRecipeOutputListener(API_KEY, WaveLogger.ACCEL_RECIPE_ID);
                    mWaveService.unregisterRecipeOutputListener(API_KEY, WaveLogger.LOC_RECIPE_ID);
                } catch (RemoteException e) {
                    Log.d(TAG, "lost connection to the service");
                }
            }
        }
        
        // disconnect from the WaveService
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }

    // Nested ServiceConnection subclass
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mWaveService = IWaveServicePublic.Stub.asInterface(service);
            // NOTE: We may have problems if binding is not complete before
            //       onStartCommand is called.  We might have to make onCreate
            //       block until binding is complete
        }
        
        public void onServiceDisconnected(ComponentName className) {
            mWaveService = null;
        }
    };
    
    // Recipe output listeners
    private IWaveRecipeOutputDataListener accelOutputListener = new IWaveRecipeOutputDataListener.Stub() {
        public void receiveWaveRecipeOutputData(ParcelableWaveRecipeOutputData wrOutput) {
            // log the received data to the appropriate SQLite table
            databaseHelper.insertAccelData(new Date(), wrOutput.getTime(), wrOutput.valuesAsMap());
        }
    };
    
    private IWaveRecipeOutputDataListener locOutputListener = new IWaveRecipeOutputDataListener.Stub() {
        public void receiveWaveRecipeOutputData(ParcelableWaveRecipeOutputData wrOutput) {
            // log the received data to the appropriate SQLite table
            databaseHelper.insertLocData(new Date(), wrOutput.getTime(), wrOutput.valuesAsMap());
        }
    };
    
    public boolean isBound() {
        return (mBound && (mWaveService != null));
    }
}