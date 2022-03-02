package com.hmsoft.pentaxgallery.data;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.location.Location;

import com.hmsoft.pentaxgallery.util.Logger;

@SuppressLint("Range")
public class LocationTable {

    private static final String TAG = "Location";

    private static final String CACHED_LOCATION_PROVIDER = "database";
    public static final String TABLE_NAME = "location";

    public static final String COLUMN_NAME_TIMESTAMP = "timestamp";
    public static final String COLUMN_NAME_LATITUDE = "latitude";
    public static final String COLUMN_NAME_LONGITUD = "longitude";
    public static final String COLUMN_NAME_ALTITUDE = "altitude";
    public static final String COLUMN_NAME_ACCURACY = "accuracy";
    public static final String COLUMN_NAME_SPEED = "speed";

    public static final String SQL_DROP_TABLE = "DROP TABLE IF EXISTS " + TABLE_NAME;

    public static final String[] SQL_CREATE_INDICES = new String[]{};
    public static final String SQL_CREATE_TABLE =
            "CREATE TABLE " + TABLE_NAME + " (" +
                    COLUMN_NAME_TIMESTAMP + DatabaseHelper.TYPE_INTEGER + DatabaseHelper.TYPE_PRIMARY_KEY + DatabaseHelper.COMMA_SEP +
                    COLUMN_NAME_LATITUDE + DatabaseHelper.TYPE_REAL + DatabaseHelper.COMMA_SEP +
                    COLUMN_NAME_LONGITUD + DatabaseHelper.TYPE_REAL + DatabaseHelper.COMMA_SEP +
                    COLUMN_NAME_ALTITUDE + DatabaseHelper.TYPE_REAL + DatabaseHelper.COMMA_SEP +
                    COLUMN_NAME_ACCURACY + DatabaseHelper.TYPE_REAL + DatabaseHelper.COMMA_SEP +
                    COLUMN_NAME_SPEED + DatabaseHelper.TYPE_REAL + ")";

    public static final String INSERT_SQL = "INSERT OR IGNORE INTO " + TABLE_NAME + " (" +
            COLUMN_NAME_TIMESTAMP + DatabaseHelper.COMMA_SEP +
            COLUMN_NAME_LATITUDE + DatabaseHelper.COMMA_SEP +
            COLUMN_NAME_LONGITUD + DatabaseHelper.COMMA_SEP +
            COLUMN_NAME_ALTITUDE + DatabaseHelper.COMMA_SEP +
            COLUMN_NAME_ACCURACY + DatabaseHelper.COMMA_SEP +
            COLUMN_NAME_SPEED + ") VALUES (?,?,?,?,?,?)";

    public static final String UPDATE_SQL = "UPDATE " + TABLE_NAME + " SET " +
            COLUMN_NAME_TIMESTAMP + "=?" + DatabaseHelper.COMMA_SEP +
            COLUMN_NAME_LATITUDE  + "=?" + DatabaseHelper.COMMA_SEP +
            COLUMN_NAME_LONGITUD  + "=?" + DatabaseHelper.COMMA_SEP +
            COLUMN_NAME_ALTITUDE  + "=?" + DatabaseHelper.COMMA_SEP +
            COLUMN_NAME_ACCURACY  + "=?" + DatabaseHelper.COMMA_SEP +
            COLUMN_NAME_SPEED     + "=? WHERE " +
            COLUMN_NAME_TIMESTAMP + "=?";

    private static final String TIMESTAMP_WHERE_CONDITION = COLUMN_NAME_TIMESTAMP + " = ?";

    private static final ContentValues sUpdateValues = new ContentValues(1);
    private static final String[] sUpdateValuesValues = new String[1];
    private static final ContentValues sInsertValues = new ContentValues(7);
    private static SQLiteStatement sInsertStatement = null;

    public static Location loadFromCursor(Cursor cursor) {
        Location location = new Location(CACHED_LOCATION_PROVIDER);
        location.setTime(cursor.getLong(cursor.getColumnIndex(COLUMN_NAME_TIMESTAMP)));
        location.setLatitude(cursor.getDouble(cursor.getColumnIndex(COLUMN_NAME_LATITUDE)));
        location.setLongitude(cursor.getDouble(cursor.getColumnIndex(COLUMN_NAME_LONGITUD)));
        location.setAltitude(cursor.getDouble(cursor.getColumnIndex(COLUMN_NAME_ALTITUDE)));
        location.setAccuracy(cursor.getFloat(cursor.getColumnIndex(COLUMN_NAME_ACCURACY)));
        location.setSpeed(cursor.getFloat(cursor.getColumnIndex(COLUMN_NAME_SPEED)));

        return location;
    }

    public static Location getLast() {
        DatabaseHelper helper = DatabaseHelper.getInstance();
        Cursor cursor = helper.getReadableDatabase().query(TABLE_NAME, null, null, null, null,
                null, COLUMN_NAME_TIMESTAMP + " DESC", "1");
        if(cursor != null) {
            try	{
                if(cursor.moveToFirst()) {
                    return loadFromCursor(cursor);
                }
            }
            finally	{
                cursor.close();
            }
        }
        return null;
    }


    public static synchronized long saveToDatabase(Location location) {

        prepareDmlStatements();
        sInsertStatement.bindLong(1, location.getTime());
        sInsertStatement.bindDouble(2, location.getLatitude());
        sInsertStatement.bindDouble(3, location.getLongitude());
        sInsertStatement.bindDouble(4, location.getAltitude());
        sInsertStatement.bindDouble(5, location.getAccuracy());
        sInsertStatement.bindDouble(6, location.getSpeed());
        sInsertStatement.execute();

        if (Logger.DEBUG) {
            Logger.debug(TAG, "Location %s", "inserted");
        }

        return 1;
    }

    private static synchronized void prepareDmlStatements() {
        if(sInsertStatement == null) {
            DatabaseHelper helper = DatabaseHelper.getInstance();
            SQLiteDatabase db = helper.getWritableDatabase();
            sInsertStatement = db.compileStatement(INSERT_SQL);
        }
    }
}