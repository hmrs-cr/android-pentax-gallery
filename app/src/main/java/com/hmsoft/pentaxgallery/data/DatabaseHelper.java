package com.hmsoft.pentaxgallery.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.hmsoft.pentaxgallery.MyApplication;
import com.hmsoft.pentaxgallery.util.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;


public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "Helper";

    public static final String TYPE_INTEGER = "  INTEGER";
    public static final String TYPE_TEXT = "  TEXT";
    public static final String TYPE_REAL = "  REAL";
    public static final String TYPE_PRIMARY_KEY = " PRIMARY KEY";
    public static final String COMMA_SEP = ",";

    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "locationData.db";

    private static DatabaseHelper instance;

    private DatabaseHelper(Context context, String name, SQLiteDatabase.CursorFactory factory,
                           int version) {
        super(context, name, factory, version);
    }

    private DatabaseHelper() {
        this(MyApplication.ApplicationContext, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public static synchronized DatabaseHelper getInstance() {
        if (instance == null) {
            instance = new DatabaseHelper();
        }
        return instance;
    }

    public double getDoubleScalar(String query) {
        Cursor cursor = this.getReadableDatabase().rawQuery(query, null);
        if(cursor != null) {
            try	{
                if(cursor.moveToFirst()) {
                    return cursor.getDouble(0);
                }
            }
            finally	{
                cursor.close();
            }
        }
        return 0;
    }

    public File getPathFile() {
        return  new File(this.getReadableDatabase().getPath());
    }

    public boolean importDB(String inFileName) {

        final File outFile = getPathFile();
        try {

            File dbFile = new File(inFileName);
            FileInputStream fis = new FileInputStream(dbFile);

            // Open the empty db as the output stream
            OutputStream output = new FileOutputStream(outFile);

            // Transfer bytes from the input file to the output file
            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                output.write(buffer, 0, length);
            }

            // Close the streams
            output.flush();
            output.close();
            fis.close();

            return true;

        } catch (Exception e) {
            Logger.warning(TAG, "importDB", e);
            return false;
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        if(Logger.DEBUG) Logger.debug(TAG, "onCreate");

        db.execSQL(LocationTable.SQL_CREATE_TABLE);

        for (String index : LocationTable.SQL_CREATE_INDICES) {
            db.execSQL(index);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(Logger.DEBUG) Logger.debug(TAG, "onUpgrade");
    }
}