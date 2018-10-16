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

package com.archos.mediascraper;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.BaseColumns;
import android.util.Log;

import com.archos.filecorelibrary.FileUtils;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediascraper.preprocess.SearchInfo;
import com.archos.mediascraper.preprocess.SearchPreprocessor;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by alexandre on 09/12/15.
 */
public class ScraperDebug extends Thread{
    private final static String TAG = "ScraperDebug";
    public final static String FILE_NAME = "file_name";
    public final static String TITLE = "title";
    public final static String PATH = "/sdcard/scraper_debug";
    public final static String PATH_RESULT = "/sdcard/scraper_result";
    public final static String ONLINE_ID = "imdb_video_online_id";
    public final static String SHOW_ONLINE_ID = "imdb_show_online_id";
    public final static String TMDB_ONLINE_ID = "tmdb_online_id";
    public final static String TVDB_ONLINE_ID = "tvdb_online_id";

    public final static String TVDB_SHOW_ONLINE_ID = "tvdb_show_online_id";
    private final Context mContext;

    public ScraperDebug(Context context){
        super();
        mContext = context;
    }

    private static class Item{
        public String online_id;
        public String show_online_id;
        public String title;
        public String filename;
        public String tvdb_show_online_id;
        public String tmdb_online_id;
        public String tvdb_online_id;

        public Item(String online_id, String show_online_id,String tmdb_online_id, String tvdb_online_id, String tvdb_show_online_id,  String title, String filename){
            this.online_id= online_id;
            this. show_online_id = show_online_id;
            this.title = title;
            this.filename = filename;
            this.tmdb_online_id = tmdb_online_id;
            this.tvdb_online_id = tvdb_online_id;
            this.tvdb_show_online_id = tvdb_show_online_id;

        }

    }
    private static class FailedItem{
        public Item oldItem;
        public Item newItem;
        public String filename;


    }

    public void run(){
        HashMap<String, Item > map = getHashMap();
        ArrayList<String> lastTvShows = new ArrayList<>();
        int newlyScrapped = 0;
        int notScrappedAnymore = 0;
        int diff = 0;
        File file= new File (PATH_RESULT);
        if (file.exists())
        {
            file.delete();
        }
        for (Map.Entry<String, Item> entry : map.entrySet()) {
            boolean wasShow = false;
            if(!entry.getValue().show_online_id.equals("0")){
                lastTvShows.add(entry.getKey());
                wasShow = true;
            }
            log("scraping : " + entry.getKey());

            SearchInfo searchInfo = SearchPreprocessor.instance().parseFileBased(null, Uri.parse("/"+entry.getKey()));
            Scraper scraper = new Scraper(mContext);
            ScrapeDetailResult result = scraper.getAutoDetails(searchInfo);
            if(result.tag!=null){


                if(!isSet(entry.getValue().tmdb_online_id)&&!isSet(entry.getValue().tvdb_online_id)){
                    log("newly scrapped tvdb/tmdb :  " + result.tag.getOnlineId());
                    log("newly scrapped imdb :  " + result.tag.getImdbId());
                    newlyScrapped++;
                   continue;
                }

                log("Found online id " + result.tag.getImdbId());
                log("Found online title " + result.tag.getTitle()   );
                boolean hasDiff = false;
                //diff on imdb
                if(result.tag.getImdbId()!=null&&!result.tag.getImdbId().equals(entry.getValue().online_id)||(result.tag.getImdbId()==null||result.tag.getImdbId().isEmpty())&&(entry.getValue().online_id!=null&&!entry.getValue().online_id.isEmpty())){
                    log("DIFF/ id is different -> new imdb: " + result.tag.getImdbId() + " old imdb : " + entry.getValue().online_id);
                    hasDiff = true;
                }

                //diff on tmdb/tvdb

                long tmdbId = 0;
                long tvdbId = 0;

                if(result.tag instanceof EpisodeTags)
                    tvdbId = result.tag.getOnlineId();
                else
                    tmdbId = result.tag.getOnlineId();

                if(!entry.getValue().tmdb_online_id.equals(""+tmdbId)) {
                    log("DIFF/ tmdbid is different -> new tmdbid: " + tmdbId + " old tmdbid : " + entry.getValue().tmdb_online_id);
                    hasDiff = true;

                }
                if(!entry.getValue().tvdb_online_id.equals(""+tvdbId)) {
                    log("DIFF/ tvdbId is different -> new tvdbId: " + tvdbId + " old tvdbId : " + entry.getValue().tvdb_online_id);
                    if(result.tag instanceof EpisodeTags) {
                        if (!entry.getValue().show_online_id.equals("" + ((EpisodeTags) result.tag).getShowTags().getOnlineId()))
                            log("DIFF/ show_online_id is different -> new tvdbId: " + ((EpisodeTags) result.tag).getShowTags().getOnlineId() + " old show_online_id : " + entry.getValue().tvdb_show_online_id);
                    }
                    hasDiff = true;

                }

                
                if(hasDiff)
                    diff++;


            }else if(result.status != ScrapeStatus.ERROR && result.status != ScrapeStatus.ERROR_NETWORK && result.status != ScrapeStatus.ERROR_NO_NETWORK){
                log("W/ network error while scraping "+entry.getKey());
            } else {
                log("NOTSCRAP/ file " + entry.getKey());
                if(entry.getValue().online_id!=null&&!entry.getValue().online_id.isEmpty()&&!entry.getValue().online_id.equals("0")){
                    notScrappedAnymore ++ ;
                    log("DIFF/ " + entry.getKey()+ " was scraped before with imdb_id " + entry.getValue().online_id);
                }
            }

            log("-----------------------------------");

        }
        log("Diff : "+diff);
        log("notScrappedAnymore : "+notScrappedAnymore);
        log("newlyScrapped : "+newlyScrapped);

    }

    private void log(String s) {

        File file= new File (PATH_RESULT);
        FileWriter fw = null;

        if (!file.exists()) {
            try {

                file.getParentFile().mkdirs();
                file.createNewFile();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            fw = new FileWriter(PATH_RESULT,true);
            fw.append(s+"\n");
            fw.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

        Log.d(TAG, s);


    }

    private static final String WHERE_ALL_MODE =
            VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_ID + ">0 AND " +
                    VideoStore.Video.VideoColumns.ARCHOS_HIDE_FILE + "=0 AND " +
                    VideoStore.MediaColumns.DATA + " NOT LIKE ?";

    private final static String[] SCRAPER_ACTIVITY_COLS = {
            // Columns needed by the activity
            BaseColumns._ID,
            VideoStore.MediaColumns.DATA,
            VideoStore.Video.VideoColumns.SCRAPER_TITLE,
            VideoStore.Video.VideoColumns.SCRAPER_M_IMDB_ID,
            VideoStore.Video.VideoColumns.SCRAPER_S_IMDB_ID,
            VideoStore.Video.VideoColumns.SCRAPER_E_IMDB_ID,
            VideoStore.Video.VideoColumns.SCRAPER_M_ONLINE_ID,
            VideoStore.Video.VideoColumns.SCRAPER_E_ONLINE_ID,
            VideoStore.Video.VideoColumns.SCRAPER_S_ONLINE_ID,

    };

    private static Cursor getFileListCursor(Context context) {
        // Look for all the videos not yet processed and not located in the Camera folder
        final String cameraPath =  Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getPath() + "/Camera";
        String[] selectionArgs = new String[]{ cameraPath + "/%" };
        return context.getContentResolver().query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, SCRAPER_ACTIVITY_COLS, WHERE_ALL_MODE, selectionArgs, null);
    }

    public static void prepareJsonFileFromBD(Context context) {
        HashMap<String, Item> map = new HashMap<>();
        Cursor cursor = getFileListCursor(context);
        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            int fileColumn = cursor.getColumnIndex(VideoStore.MediaColumns.DATA);
            int onlineImdbMovieIdColumn = cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_M_IMDB_ID);
            int onlineImdbEpisodeIdColumn = cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_E_IMDB_ID);
            int onlineImdbShowColumn = cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_S_IMDB_ID);
            int onlineMovieIdColumn = cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_M_ONLINE_ID);
            int onlineEpisodeIdColumn = cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_E_ONLINE_ID);
            int onlineShowColumn = cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_S_ONLINE_ID);
            int titleColumn = cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_TITLE);
            do {
                String name = FileUtils.getName(Uri.parse(cursor.getString(fileColumn)));
                String onlineImdbId = cursor.getString(onlineImdbMovieIdColumn);
                String onlineEImdbId = cursor.getString(onlineImdbEpisodeIdColumn);
                String onlineSimdbId = cursor.getString(onlineImdbShowColumn);
                Long movieOnlineId = cursor.getLong(onlineMovieIdColumn);
                Long showOnlineId = cursor.getLong(onlineShowColumn);

                Long episodeOnlineId = cursor.getLong(onlineEpisodeIdColumn);

                String title = cursor.getString(titleColumn);
                map.put(name, new Item(isSet(onlineImdbId)?onlineImdbId:onlineEImdbId,onlineSimdbId,movieOnlineId+"", episodeOnlineId+"",showOnlineId+"", title,  name));

            } while (cursor.moveToNext());
            cursor.close();
        }
        writeToFile(map);
    }

    private static boolean isSet(String string) {
        return string!=null&&!string.isEmpty()&&!string.equals("0");
    }

    public static HashMap<String, Item> getHashMap(){
        HashMap<String, Item> map = new HashMap<>();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(PATH));


            StringBuilder sb = new StringBuilder();
            String line = br.readLine();

            while (line != null) {
                sb.append(line);
                sb.append("\n");
                line = br.readLine();
            }
            ;

            final JSONObject jsonObj = new JSONObject(sb.toString());

            JSONArray data = jsonObj.getJSONArray("data");
            for(int i = 0; i<data.length(); i++){
                String name = data.getJSONObject(i).getString(FILE_NAME);
                String imdb_online_id = data.getJSONObject(i).getString(ONLINE_ID);
                String imdb_show_online_id = data.getJSONObject(i).getString(SHOW_ONLINE_ID);

                String episodeOnlineId = data.getJSONObject(i).getString(TVDB_ONLINE_ID);
                String showOnlineId = data.getJSONObject(i).getString(TVDB_SHOW_ONLINE_ID);
                String movieOnlineId = data.getJSONObject(i).getString(TMDB_ONLINE_ID);
                String title = data.getJSONObject(i).getString(TITLE);
                map.put(name, new Item(imdb_online_id, imdb_show_online_id,movieOnlineId+"", episodeOnlineId+"",showOnlineId+"", title,  name));
            }

        }catch (JSONException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(br!=null)
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }
        return map;
    }



    public static void writeToFile(HashMap<String, Item> map){

        File file= new File (PATH);
        FileWriter fw = null;
        if (file.exists())
        {
            file.delete();
        }

        try {

            file.getParentFile().mkdirs();
            file.createNewFile();

        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            fw = new FileWriter(PATH);

        } catch (IOException e) {
            e.printStackTrace();
        }

        if(fw!=null){
            try {
                fw.append("{ \ndata:[\n");
                int i = 0;
                for (Map.Entry<String, Item> entry : map.entrySet()) {

                    fw.append("{");
                    fw.append(JSONObject.quote(FILE_NAME)+":"+JSONObject.quote(entry.getKey())+",");
                    fw.append(JSONObject.quote(ONLINE_ID)+":"+JSONObject.quote(entry.getValue().online_id)+",");
                    fw.append(JSONObject.quote(SHOW_ONLINE_ID)+":"+JSONObject.quote(entry.getValue().show_online_id)+",");
                    fw.append(JSONObject.quote(TMDB_ONLINE_ID)+":"+JSONObject.quote(entry.getValue().tmdb_online_id)+",");
                    fw.append(JSONObject.quote(TVDB_ONLINE_ID)+":"+JSONObject.quote(entry.getValue().tvdb_online_id)+",");
                    fw.append(JSONObject.quote(TVDB_SHOW_ONLINE_ID)+":"+JSONObject.quote(entry.getValue().tvdb_show_online_id)+",");
                    fw.append(JSONObject.quote(TITLE)+":"+JSONObject.quote(entry.getValue().title));
                    fw.append("}");
                    i++;
                    if (i < map.size())
                        fw.append(",\n\n");

                }
                fw.append("] \n}");
                fw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
}
