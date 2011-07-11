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
import android.database.Cursor;
import android.database.sqlite.*;
import android.database.SQLException;
import android.os.Environment;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class DbHelper {
    
    private static final String TAG = "DbHelper";
    
    private static final String CSV_ENC = "UTF-8";
    
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
        
        public static final String[] ALL = {RCVD_TIMESTAMP, SAMPLE_TIME, X, Y, Z};
    }
    
    static final class LocDataColumns {
        public static final String _ID = "_id";
        // public static final String _COUNT = "_count";
        public static final String RCVD_TIMESTAMP = "rcvd_time";
        public static final String SAMPLE_TIME = "sample_time";
        public static final String LATITUDE = "latitude";
        public static final String LONGITUDE = "longitude";
        public static final String ALTITUDE = "altitude";
        
        public static final String[] ALL = {RCVD_TIMESTAMP, SAMPLE_TIME, LATITUDE, LONGITUDE, ALTITUDE};
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
        cv.put(AccelDataColumns.SAMPLE_TIME, time);
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
        
        ContentValues cv = new ContentValues(values.size() + 2);
        cv.put(LocDataColumns.RCVD_TIMESTAMP, (new Timestamp(rcvdTime.getTime())).toString());
        cv.put(LocDataColumns.SAMPLE_TIME, time);
        cv.put(LocDataColumns.LATITUDE, values.get("latitude"));
        cv.put(LocDataColumns.LONGITUDE, values.get("longitude"));
        cv.put(LocDataColumns.ALTITUDE, values.get("altitude"));
        
        long result;
        
        try {
            result = database.insertOrThrow(LOC_DATA_TABLE_NAME, null, cv);
        } catch (SQLException e) {
            Log.w(TAG, "SQLException while storing "+cv, e);
            return false;
        }
        
        return result >= 0;
    }
    
    protected boolean writeAccelData(File f) {
        try {
            Writer out = new OutputStreamWriter(new FileOutputStream(f), CSV_ENC);
            try {
                // first write a csv header
                out.write("rcvd_time, sample_time, x, y, z\n");
            
                // now write the table values
                Cursor c = database.query(ACCEL_DATA_TABLE_NAME,
                                          AccelDataColumns.ALL,
                                          null, null, null, null, null);
                                      
                if (c.moveToFirst()) {
                    for (int i=0; i<c.getCount(); i++) {
                        String line = String.format("%s,%d,%f,%f,%f\n",
                                                    c.getString(0),
                                                    c.getLong(1),
                                                    c.getDouble(2),
                                                    c.getDouble(3),
                                                    c.getDouble(4));
                        out.write(line);
                        c.moveToNext();
                    }
                }
                c.close();
            }
            finally {
                out.close();
            }
        } catch (IOException ioe) {
            Log.w(TAG, ioe);
            return false;
        }
        return true;
    }
    
    protected boolean writeLocData(File f) {
        try {
            Writer out = new OutputStreamWriter(new FileOutputStream(f), CSV_ENC);
            try {
                // header
                out.write("rcvd_time, sample_time, longitude, latitude, altitude\n");
                
                Cursor c = database.query(LOC_DATA_TABLE_NAME,
                                          LocDataColumns.ALL,
                                          null, null, null, null, null);
                
                if (c.moveToFirst()) {
                    for (int i=0; i<c.getCount(); i++) {
                        String line = String.format("%s,%d,%f,%f,%f\n",
                                                    c.getString(0),
                                                    c.getLong(1),
                                                    c.getDouble(2),
                                                    c.getDouble(3),
                                                    c.getDouble(4));
                        out.write(line);
                        c.moveToNext();
                    }
                }
                c.close();
            }
            finally {
                out.close();
            }
        } catch (IOException ioe) {
            Log.w(TAG, ioe);
            return false;
        }
        return true;
    }
    
    public File writeContentsToSdCard() {
        // we write two files, one for accel data, and one for location data,
        // to a folder on the sd card, named with the current date and time
        
        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd 'at' HH-mm-ss", Locale.US);
        // TODO: change the folder name to one derived from the package name
        String bundleName = "WaveLogger Database Export "+sdf.format(now);
        
        // check access to the sd card
        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            return null;
        }
        
        File root = Environment.getExternalStorageDirectory();
        File parent = new File(root, bundleName);
        if (parent.mkdir() && parent.canWrite()) {
            // bundle directory created
            File accelDataFile = new File(parent, "accelerometer.csv");
            File locDataFile = new File(parent, "location.csv");
            
            boolean didFail = false;
            if (!writeAccelData(accelDataFile)) {
                didFail = true;
                Log.d(TAG, "Failure writing accelerometer data");
            }
            if (!writeLocData(locDataFile)) {
                didFail = true;
                Log.d(TAG, "Failure writing location data");
            }
            if (didFail) {
                return null;
            }
        } else {
            Log.w(TAG, "Could not create directory "+parent+" for WaveLogger database export");
            return null;
        }
        
        return parent;
    }
    
    public long emptyDatabase() {
        long accel_count = database.delete(ACCEL_DATA_TABLE_NAME, "1", null);
        long loc_count = database.delete(LOC_DATA_TABLE_NAME, "1", null);
        
        Log.d(TAG, String.format("emptyDatabase deleted %d accelerometer records & %d location records", accel_count, loc_count));
        
        return (accel_count + loc_count);
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
                    + AccelDataColumns.SAMPLE_TIME + " INTEGER,"
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
                    + LocDataColumns.ALTITUDE + " REAL"
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