// 
//  DbHelper.java
//  WaveLogger
//  
//  Created by Philip Kuryloski on 2011-06-30.
//  Copyright 2011 University of California, Berkeley. All rights reserved.
// 

package edu.berkeley.androidwave.wavelogger;

import android.content.ContentValues;
import android.content.Context;
// import android.database.Cursor;
import android.database.sqlite.*;
import android.database.SQLException;
import android.util.Log;
// import java.security.cert.X509Certificate;
import java.sql.Timestamp;
// import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
// import java.util.HashMap;

public class DbHelper {
    
    private static final String TAG = "DbHelper";
    
    protected static final String DATABASE_NAME = "wave_logger.db";
    protected static final int DATABASE_VERSION = 1;
    protected static final String ACCEL_DATA_TABLE_NAME = "accel_data";
    protected static final String LOC_DATA_TABLE_NAME = "location_data";
    
    protected SQLiteDatabase database;
    
    static final class AccelDataColumns {
        public static final String _ID = "_id";
        // public static final String _COUNT = "_count";
        public static final String RCVD_TIMESTAMP = "rcvd_time";
        public static final String SAMPLE_TIME = "sample_time";
        public static final String X = "x";
        public static final String Y = "y";
        public static final String Z = "z";
    }
    
    static final class LocDataColumns {
        public static final String _ID = "_id";
        // public static final String _COUNT = "_count";
        public static final String RCVD_TIMESTAMP = "rcvd_time";
        public static final String SAMPLE_TIME = "sample_time";
        public static final String LATITUDE = "latitude";
        public static final String LONGITUDE = "longitude";
        public static final String ACCURACY = "accuracy";
        public static final String ALTITUDE = "altitude";
        public static final String BEARING = "bearing";
        public static final String SPEED = "speed";
    }
    
    private DatabaseHelper mOpenHelper;
    
    public DbHelper(Context c) {
        mOpenHelper = new DatabaseHelper(c);
        database = mOpenHelper.getWritableDatabase();
    }
    
    /**
     * Data insertion methods
     */
    public boolean insertAccelData(Date rcvdTime, long time, Map<String, Double> values) {
        
        ContentValues cv = new ContentValues(values.size() + 2);
        cv.put(AccelDataColumns.RCVD_TIMESTAMP, (new Timestamp(rcvdTime.getTime())).toString());
        cv.put(AccelDataColumns.SAMPLE_TIME, (new Timestamp(time)).toString());
        cv.put(AccelDataColumns.X, values.get("x"));
        cv.put(AccelDataColumns.Y, values.get("y"));
        cv.put(AccelDataColumns.Z, values.get("z"));
        
        long result;
        
        try {
            result = database.insertOrThrow(ACCEL_DATA_TABLE_NAME, null, cv);
        } catch (SQLException e) {
            Log.w(TAG, "SQLException while storing "+cv, e);
            return false;
        }
        
        return result >= 0;
    }
    
    public boolean insertLocData(Date rcvdTime, long time, Map<String, Double> values) {
        throw new UnsupportedOperationException("not implemented yet");
    }
    
    public void closeDatabase() {
        database.close();
    }
    
    /**
     * SQLiteOpenHelper subclass
     */
    static class DatabaseHelper extends SQLiteOpenHelper {
        
        DatabaseHelper(Context context) {
            // calls the super constructor, requesting the default cursor factory.
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }
        
        @Override
        public void onCreate(SQLiteDatabase db) {
            
            db.execSQL("CREATE TABLE " + ACCEL_DATA_TABLE_NAME + " ("
                    + AccelDataColumns._ID + " INTEGER PRIMARY KEY,"
                    + AccelDataColumns.RCVD_TIMESTAMP + " TEXT NOT NULL,"
                    + AccelDataColumns.SAMPLE_TIME + " TEXT,"
                    + AccelDataColumns.X + " REAL,"
                    + AccelDataColumns.Y + " REAL,"
                    + AccelDataColumns.Z + " REAL"
                    + ");");
            
            db.execSQL("CREATE TABLE " + LOC_DATA_TABLE_NAME + " ("
                    + LocDataColumns._ID + " INTEGER PRIMARY KEY,"
                    + LocDataColumns.RCVD_TIMESTAMP + " TEXT NOT NULL,"
                    + LocDataColumns.SAMPLE_TIME + " TEXT,"
                    + LocDataColumns.LATITUDE + " REAL,"
                    + LocDataColumns.LONGITUDE + " REAL,"
                    + LocDataColumns.ACCURACY + " REAL,"
                    + LocDataColumns.ALTITUDE + " REAL,"
                    + LocDataColumns.BEARING + " REAL,"
                    + LocDataColumns.SPEED + " REAL"
                    + ");");
        }
        
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            
            // Logs that the database is being upgraded
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");

            // Kills the table and existing data
            db.execSQL("DROP TABLE IF EXISTS "+ACCEL_DATA_TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS "+LOC_DATA_TABLE_NAME);

            // Recreates the database with a new version
            onCreate(db);
        }
    }
}