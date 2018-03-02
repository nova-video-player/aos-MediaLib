// Copyright 2017 Archos SA
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.archos.mediacenter.utils;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.AsyncTask;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.archos.environment.ArchosUtils;

public enum FTPShortcutDbAdapter {
	
	//made for SFTP and FTP
    VIDEO(FTPShortcutDbAdapter.DATABASE_VIDEO_TABLE, FTPShortcutDbAdapter.DATABASE_CREATE_VIDEO);

    private static final String TAG = "FTPShortcutDbAdapter";
    protected final static boolean DBG = false;

    // To be incremented each time the architecture of the database is changed
    private static final int DATABASE_VERSION = 4;

    public static final String ACTION_SHORTCUTS_CHANGED = "com.archos.mediacenter.ftp_shortcuts_changed";
    private static final String KEY_PATH = "path";
    private static final String KEY_HOST = "host";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_ROWID = "_id";
    private static final String KEY_PORT= "port";
    private static final String KEY_FTP_TYPE= "ftp_type";
    private static final String KEY_SHORTCUT_NAME = "shortcut_name";
    // ftp types
    public static final int FTP = 0;
    public static final int SFTP = 1;
     
    private static final String DATABASE_NAME = "ftp_shortcuts_db";
    private static final String DATABASE_VIDEO_TABLE = "ftp_shortcuts_table_video";

    private static final String DATABASE_CREATE_VIDEO =
        "create table "+DATABASE_VIDEO_TABLE+" (_id integer primary key autoincrement, " + KEY_PATH + " text not null, "+KEY_FTP_TYPE+" integer, " + KEY_HOST + " text not null , "+KEY_PORT+" integer, " + KEY_USERNAME + " text not null , " + KEY_PASSWORD + " text not null" + ", "+KEY_SHORTCUT_NAME+" text not null );";

    private static final String[] SHORTCUT_COLS = { KEY_ROWID, KEY_PATH,KEY_FTP_TYPE, KEY_HOST,KEY_PORT, KEY_USERNAME, KEY_PASSWORD,KEY_SHORTCUT_NAME };

    private Context mContext;
    private DatabaseHelper mDbHelper;
    // The path is the only info the other classes need to know.
    private List<FTPShortcut> mFTPShortcutList;
    private List<FTPShortcut> mSFTPShortcutList;
    private HashMap<String, String> mShortcutIpList;
    // The database id is only needed locally for the database management
    private List<Long> mShortcutDbIdList;
    private SQLiteDatabase mDb;
    private final String mDatabaseTable, mDatabaseCreate;
    public static class FTPShortcut{
    	public String host;
    	public int port;
    	public int type;
    	public String path;
    	public String username;
    	public String password;
    	public String shortcutName;
    	public long rowID;
    	public FTPShortcut(String host, int port, int type, String path, String username, String password, String shortcutName){
    		this(-1,host,port,type,path,username,password,shortcutName);
    		
    				
    	}
    	public FTPShortcut(long rowID, String host, int port, int type, String path, String username, String password, String shortcutName){
    		this.host=host;
    		this.port=port;
    		this.type = type;
    		this.path=path;
    		this.password = password;
    		this.username=username;
    		this.rowID = rowID;
    		this.shortcutName=shortcutName;
    		
    				
    	}

    	
    }
    FTPShortcutDbAdapter(String databaseTable, String databaseCreate) {
        mDatabaseTable = databaseTable;
        mDatabaseCreate = databaseCreate;
    }

    /*
     * Open the shortcut database
     */
    private void open() throws SQLException {
        mDb = mDbHelper.getWritableDatabase();
    }

    /*
     * Close the shortcut database
     */
    private void close() {
        if (mDb != null) {
            mDb.close();
        }
        if (mDbHelper != null) {
            mDbHelper.close();
        }
    }

    /*
     * Start all stuff needed par ShortcutDbAdapter.
     */
    // TODO we should do this in an AsyncTask but browsers currently
    // expect a right a way answer.
    public void loadShortcuts(Context context) {
        if (mContext != null) {
            return;
        }

        mContext = context;
        mDbHelper = new DatabaseHelper(context);
        mFTPShortcutList = new ArrayList<FTPShortcut>();
        mSFTPShortcutList = new ArrayList<FTPShortcut>();
        mShortcutIpList = new HashMap<String, String>();
        mShortcutDbIdList = new ArrayList<Long>();

        open();
        Cursor cursor = getAllShortcuts();
        if (cursor != null) {

            int rowIdColumnIndex = cursor.getColumnIndex(KEY_ROWID);
            int pathColumnIndex = cursor.getColumnIndex(KEY_PATH);
            int hostColumnIndex = cursor.getColumnIndex(KEY_HOST);
            int ftpTypeColumnIndex = cursor.getColumnIndex(KEY_FTP_TYPE);
            int portColumnIndex = cursor.getColumnIndex(KEY_PORT);
            int shortcutNameIndex = cursor.getColumnIndex(KEY_SHORTCUT_NAME);
            int usernameColumnIndex = cursor.getColumnIndex(KEY_USERNAME);
            int passwordColumnIndex = cursor.getColumnIndex(KEY_PASSWORD);
            int shortcutCount = cursor.getCount();

            if (shortcutCount > 0) {

                if (DBG) {
                    Log.d(TAG, "loadShortcuts : found " + shortcutCount
                          + " shortcuts in the database");
                }
                cursor.moveToFirst();
                do {
                	long rowId  = cursor.getLong(rowIdColumnIndex);
                    String path = cursor.getString(pathColumnIndex);
                    String host = cursor.getString(hostColumnIndex);
                    String username = cursor.getString(usernameColumnIndex);
                    String password = cursor.getString(passwordColumnIndex);
                    int type = cursor.getInt(ftpTypeColumnIndex);
                    int port = cursor.getInt(portColumnIndex);
                    String shortcutName = 	cursor.getString(shortcutNameIndex);
                    if(shortcutName==null)
                    	shortcutName = host;
                    if(type == FTP){
                    	mFTPShortcutList.add(new FTPShortcut(rowId,host, port, type, path, username, password,shortcutName));
                    	
                    }
                    else
                    	mSFTPShortcutList.add(new FTPShortcut(rowId,host, port, type, path, username, password,shortcutName));

                } while (cursor.moveToNext());
            }
            cursor.close();
        }
        close();
        if (DBG)
            Log.d(TAG, "loadShortcuts : found " + mSFTPShortcutList.size() + " shortcuts");
    }

    public void addToShortcutList(int type, String host,int port, String path,String username, String password, String shortcutName){


        // Add the new shortcut to the current list
    	FTPShortcut shortcut = new FTPShortcut(host, port, type, path, username, password,shortcutName);
    	addToShortcutList(shortcut);
    }
    public List<FTPShortcut> getFTPShortcutList() {
        return mFTPShortcutList;
    }
    public void addToShortcutList(FTPShortcut shortcut){


        // Add the new shortcut to the current list
    	if(shortcut.type==FTP){
    		mFTPShortcutList.add(shortcut);
    	}
    	else
    		mSFTPShortcutList.add(shortcut);


        new AddToShortcutListTask().execute(shortcut);
    }
    public void removeFromSFTPShortcutList(FTPShortcut shortcutPath) {
      

        // Remove the shortcut from the current list
        Long rowId = Long.valueOf(-1);
        int indexInArray =-1;
        if(shortcutPath.type==SFTP){
        	for(int i =0; i<mSFTPShortcutList.size(); i++){
        		if(mSFTPShortcutList.get(i).host.equals(shortcutPath.host)&&
        				mSFTPShortcutList.get(i).path.equals(shortcutPath.path)&&
        				mSFTPShortcutList.get(i).username.equals(shortcutPath.username)&&
        				mSFTPShortcutList.get(i).password.equals(shortcutPath.password)&&
        				mSFTPShortcutList.get(i).port== shortcutPath.port){
        			indexInArray = i;
        			break;
        		}
        	}
        	if (indexInArray >= 0 ) {
            	rowId = mSFTPShortcutList.get(indexInArray).rowID;
                mSFTPShortcutList.remove(indexInArray);
        	}
        }
        else {
        	for(int i =0; i<mFTPShortcutList.size(); i++){
        		if(mFTPShortcutList.get(i).host.equals(shortcutPath.host)&&
        				mFTPShortcutList.get(i).path.equals(shortcutPath.path)&&
        				mFTPShortcutList.get(i).username.equals(shortcutPath.username)&&
        				mFTPShortcutList.get(i).password.equals(shortcutPath.password)&&
        				mFTPShortcutList.get(i).port== shortcutPath.port){
        			indexInArray = i;

        			break;

        		}
        	}
        	if (indexInArray >= 0 ) {
            	rowId = mFTPShortcutList.get(indexInArray).rowID;
                mFTPShortcutList.remove(indexInArray);
        	}
        	
        }
        new RemoveFromShortcutListTask().execute(rowId);
        
    }

    public List<FTPShortcut> getSFTPShortcutList() {
        return mSFTPShortcutList;
    }

    public HashMap<String, String> getShortcutIpList() {
        return mShortcutIpList;
    }

    /*
     * Insert a new shortcut into the database
     */
    private long insertShortcut(FTPShortcut shortcut) {
        ContentValues initialValues = new ContentValues(1);
        initialValues.put(KEY_PATH, shortcut.path);
        initialValues.put(KEY_HOST, shortcut.host);
        initialValues.put(KEY_PORT, shortcut.port);
        initialValues.put(KEY_FTP_TYPE, shortcut.type);
        initialValues.put(KEY_USERNAME, shortcut.username);
        initialValues.put(KEY_PASSWORD, shortcut.password);
        initialValues.put(KEY_SHORTCUT_NAME, shortcut.shortcutName);
        open();
        long rowId = mDb.insert(mDatabaseTable, null, initialValues);
        close();
        return rowId;
    }

    /*
     * Delete the shortcut corresponding to the provided row id
     */
    private boolean deleteShortcut(long rowId) {
        // Delete the shortcut stored in the provided row
        boolean ret;
        if (rowId >= 0) {
            open();
            ret = mDb.delete(mDatabaseTable, KEY_ROWID + "=" + rowId, null) > 0;
            close();
        } else {
            ret = false;
        }
        return ret;
    }

    /*
     * Retrieve all the shortcuts stored in the table
     */
    private Cursor getAllShortcuts() {
        try {
            return mDb.query(mDatabaseTable,
                            SHORTCUT_COLS,
                            null,
                            null,
                            null,
                            null,
                            null);
        }
        catch (SQLiteException e) {
            // The table corresponding to this type does not exist yet
            Log.w(TAG, e);
            return null;
        }
    }

    /*
     * Retrieve the shortcut corresponding to the provided row id
     */
    public Cursor getShortcut(long rowId) throws SQLException {
        Cursor cursor = mDb.query(true,
                                  mDatabaseTable,
                                  SHORTCUT_COLS,
                                  KEY_ROWID + "=" + rowId,
                                  null,
                                  null,
                                  null,
                                  null,
                                  null);
        if (cursor != null) {
            cursor.moveToFirst();
        }
        return cursor;
    }

    /*
     * Update the path of the shortcut corresponding to the provided row id
     */
    public boolean updateShortcut(long rowId, String path, String ipPath) {
        ContentValues args = new ContentValues(1);
       // args.put(KEY_PATH, path);
        //args.put(KEY_IPPATH, ipPath);
    //    return mDb.update(mDatabaseTable, args, KEY_ROWID + "=" + rowId, null) > 0;
        return true;
    }

    private static class DatabaseHelper extends SQLiteOpenHelper {



		DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            // This method is only called once when the database is created for the first time
            db.execSQL(DATABASE_CREATE_VIDEO);
            
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        	if (oldVersion < 3) {
                 db.execSQL("ALTER TABLE "+DATABASE_VIDEO_TABLE+" ADD COLUMN " + KEY_SHORTCUT_NAME + " TEXT");
        	 }
            
        }
    }

    private class AddToShortcutListTask extends AsyncTask<FTPShortcut, Void, Long> {

        protected void onPostExecute(Long rowId) {
            // Store the database id too so that we can retrieve it later without
            // queries to the databse
            mShortcutDbIdList.add(rowId);

            // Tell all instances of browsers to update their display if needed
            Intent intent = new Intent(ACTION_SHORTCUTS_CHANGED);
            intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
            mContext.sendBroadcast(intent);
        }

		@Override
		protected Long doInBackground(FTPShortcut... params) {
			// TODO Auto-generated method stub
			params[0].rowID = Long.valueOf(insertShortcut(params[0]));

			return params[0].rowID;
		}
    }

    private class RemoveFromShortcutListTask extends AsyncTask<Long, Void, Void> {
        protected Void doInBackground(Long... rowId) {
            // Remove the shortcut from the database
            deleteShortcut(rowId[0].longValue());
            return null;
        }

        protected void onPostExecute(Void args) {
            // Tell all instances of browsers to update their display if needed
            Intent intent = new Intent(ACTION_SHORTCUTS_CHANGED);
            intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
            mContext.sendBroadcast(intent);
        }
    }
}
