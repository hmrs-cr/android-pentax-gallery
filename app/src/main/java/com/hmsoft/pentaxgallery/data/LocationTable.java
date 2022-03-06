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

    public static final String SQL_DROP_TABLE = "DROP TABLE IF EXISTS " + TABLE_NAME;

    public static final String[] SQL_CREATE_INDICES = new String[]{};
    public static final String SQL_CREATE_TABLE =
            "CREATE TABLE " + TABLE_NAME + " (" +
                    COLUMN_NAME_TIMESTAMP + DatabaseHelper.TYPE_INTEGER + DatabaseHelper.TYPE_PRIMARY_KEY + DatabaseHelper.COMMA_SEP +
                    COLUMN_NAME_LATITUDE + DatabaseHelper.TYPE_REAL + DatabaseHelper.COMMA_SEP +
                    COLUMN_NAME_LONGITUD + DatabaseHelper.TYPE_REAL + DatabaseHelper.COMMA_SEP +
                    COLUMN_NAME_ALTITUDE + DatabaseHelper.TYPE_REAL + DatabaseHelper.COMMA_SEP +
                    COLUMN_NAME_ACCURACY + DatabaseHelper.TYPE_REAL + ")";

    public static final String INSERT_SQL = "INSERT OR IGNORE INTO " + TABLE_NAME + " (" +
            COLUMN_NAME_TIMESTAMP + DatabaseHelper.COMMA_SEP +
            COLUMN_NAME_LATITUDE + DatabaseHelper.COMMA_SEP +
            COLUMN_NAME_LONGITUD + DatabaseHelper.COMMA_SEP +
            COLUMN_NAME_ALTITUDE + DatabaseHelper.COMMA_SEP +
            COLUMN_NAME_ACCURACY + ") VALUES (?,?,?,?,?)";

    public static final String UPDATE_SQL = "UPDATE " + TABLE_NAME + " SET " +
            COLUMN_NAME_TIMESTAMP + "=?" + DatabaseHelper.COMMA_SEP +
            COLUMN_NAME_LATITUDE  + "=?" + DatabaseHelper.COMMA_SEP +
            COLUMN_NAME_LONGITUD  + "=?" + DatabaseHelper.COMMA_SEP +
            COLUMN_NAME_ALTITUDE  + "=?" + DatabaseHelper.COMMA_SEP +
            COLUMN_NAME_ACCURACY  + "=?" + "=? WHERE " +
            COLUMN_NAME_TIMESTAMP + "=?";

    private static final String TIMESTAMP_WHERE_CONDITION = COLUMN_NAME_TIMESTAMP + " = ?";

    private static final ContentValues sUpdateValues = new ContentValues(1);
    private static final String[] sUpdateValuesValues = new String[1];
    private static final ContentValues sInsertValues = new ContentValues(7);
    private static SQLiteStatement sInsertStatement = null;

    private static final String[] sLastLongFields = new String[]{COLUMN_NAME_LATITUDE, COLUMN_NAME_LONGITUD};

    public static LatLong getLocationAtTimestamp(long timestamp, long window) {

        String selectionExpression = COLUMN_NAME_TIMESTAMP + " BETWEEN " + (timestamp - window) + " AND " + (timestamp + window);

        String orderByExpression = "ABS(" + COLUMN_NAME_TIMESTAMP + " - " + timestamp + ")/1000," + COLUMN_NAME_ACCURACY;

        DatabaseHelper helper = DatabaseHelper.getInstance();
        Cursor cursor = helper.getReadableDatabase().query(
                TABLE_NAME,
                sLastLongFields,
                selectionExpression,
                null,
                null,
                null,
                orderByExpression,
                "1");

        if(cursor != null) {
            try	{
                if(cursor.moveToFirst()) {
                    Double latitude = cursor.getDouble(cursor.getColumnIndex(COLUMN_NAME_LATITUDE));
                    Double longitude = cursor.getDouble(cursor.getColumnIndex(COLUMN_NAME_LONGITUD));
                    return new LatLong(latitude, longitude);
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
        sInsertStatement.execute();

        if (Logger.DEBUG) {
            Logger.debug(TAG, "Location %s", "inserted");
        }

        return 1;
    }

    public static final class LatLong {
        public final double latitude;
        public final double longitude;

        protected LatLong(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public String getLatGeoCoordinates() {
            return getGeoCoordinates(latitude);
        }

        public String getLatRef() {
            return latitude < 0 ? "S" : "N";
        }

        public String getLongRef() {
            return longitude < 0 ? "W" : "E";
        }

        public String getLongGeoCoordinates() {
            return getGeoCoordinates(longitude);
        }

        private String getGeoCoordinates(double value) {
            // degrees, minutes, and seconds
            // ddd/1,mm/1,ss/1

            value = Math.abs(value);
            int degrees = (int)value;

            value *= 60;
            value -= (degrees * 60.0d);
            int minutes = (int)value;

            value *= 60;
            value -= (minutes * 60.0d);
            int seconds = (int)(value * 1000.0d);


            return degrees + "/1," + minutes + "/1," + seconds + "/1000";
        }
    }

    private static synchronized void prepareDmlStatements() {
        if(sInsertStatement == null) {
            DatabaseHelper helper = DatabaseHelper.getInstance();
            SQLiteDatabase db = helper.getWritableDatabase();
            sInsertStatement = db.compileStatement(INSERT_SQL);
        }
    }
}