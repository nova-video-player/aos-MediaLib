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
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;

import com.archos.filecorelibrary.FileUtils;
import com.archos.mediacenter.filecoreextension.upnp2.FileEditorFactoryWithUpnp;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.mediacenter.filecoreextension.upnp2.MetaFileFactoryWithUpnp;
import com.archos.medialib.R;
import com.archos.mediascraper.saxhandler.NfoEpisodeHandler;
import com.archos.mediascraper.saxhandler.NfoMovieHandler;
import com.archos.mediascraper.saxhandler.NfoRootHandler;
import com.archos.mediascraper.saxhandler.NfoShowHandler;

import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public class NfoParser {
    private static final String TAG = NfoParser.class.getSimpleName();
    private static final boolean DBG = false;

    /** filename w/o extension + this */
    public static final String CUSTOM_NFO_EXTENSION = ".archos.nfo";
    /** Show title + this */
    public static final String CUSTOM_SHOW_NFO_EXTENSION = "-tvshow.archos.nfo";
    public static final String CUSTOM_SEASON_POSTER_FORMAT = "%s-season%02d.archos.jpg";
    public static String getCustomSeasonPosterName(String showTitle, int season) {
        String titleEncoded = StringUtils.fileSystemEncode(showTitle);
        if (titleEncoded != null)
            return String.format(Locale.ROOT, CUSTOM_SEASON_POSTER_FORMAT, titleEncoded, Integer.valueOf(season));
        return null;
    }

    /** showtitle / filename + this */
    public static final String BACKDROP_EXTENSION = "-fanart.archos.jpg";

    /** showtitle / filename + this */
    public static final String POSTER_EXTENSION = "-poster.archos.jpg";
    public static String getCustomShowPosterName(String showTitle) {
        String titleEncoded = StringUtils.fileSystemEncode(showTitle);
        if (titleEncoded != null)
            return titleEncoded + POSTER_EXTENSION;
        return null;
    }

    public static String getCustomShowBackdropName(String showTitle) {
        String titleEncoded = StringUtils.fileSystemEncode(showTitle);
        if (titleEncoded != null)
            return titleEncoded + BACKDROP_EXTENSION;
        return null;
    }

    public static String getCustomShowNfoName(String showTitle) {
        String titleEncoded = StringUtils.fileSystemEncode(showTitle);
        if (titleEncoded != null)
            return titleEncoded + CUSTOM_SHOW_NFO_EXTENSION;
        return null;
    }
    /** filename w/o extension + this */
    public static final String NFO_EXTENSION = ".nfo";
    public static final String TV_SHOW_NFO = "tvshow.nfo";
    public static final String MOVIE_NFO = "movie.nfo";

    public static final char[] STRING_SPLITTERS = { '|', ',', '/' };

    public static class ImportContext {
        public SAXParser getParser() {
            if (mParser == null)
                mParser = getNewParser();
            return mParser;
        }

        public NfoRootHandler getRootHandler() {
            if (mRootHandler == null) {
                XMLReader reader;
                try {
                    reader = getParser().getXMLReader();
                } catch (SAXException e) {
                    // not supposed to happen, if it happens just die.
                    throw new RuntimeException("SaxParser#getXMLReader()", e);
                }
                mRootHandler = new NfoRootHandler(reader, getMovieHandler(), getEpisodeHandler());
            }
            return mRootHandler;
        }

        public NfoMovieHandler getMovieHandler() {
            if (mMovieHandler == null)
                mMovieHandler = new NfoMovieHandler();
            return mMovieHandler;
        }
        public NfoShowHandler getShowHandler() {
            if (mShowHandler == null)
                mShowHandler = new NfoShowHandler();
            return mShowHandler;
        }
        public NfoEpisodeHandler getEpisodeHandler() {
            if (mEpisodeHandler == null)
                mEpisodeHandler = new NfoEpisodeHandler();
            return mEpisodeHandler;
        }

        private SAXParser mParser;

        private NfoRootHandler mRootHandler;
        private NfoMovieHandler mMovieHandler;
        private NfoShowHandler mShowHandler;
        private NfoEpisodeHandler mEpisodeHandler;

        public final LruCache<String, ShowTags> showCache = new LruCache<String, ShowTags>(16);
        public final LruCache<String, Uri> seasonPosterCache = new LruCache<String, Uri>(16);
    }

    static SAXParser getNewParser() {
        SAXParserFactory parserFactory = SAXParserFactory.newInstance();
        try {
            return parserFactory.newSAXParser();
        } catch (ParserConfigurationException e) {
            Log.e(TAG, "Exception: " + e, e);
            throw new RuntimeException(e);
        } catch (SAXException e) {
            Log.e(TAG, "Exception: " + e, e);
            throw new RuntimeException(e);
        }
    }

    public static class NfoFile {
        public Uri videoFile;
        public String videoFileNameNoExt;
        public Uri videoFolder;
        public Uri videoNfo;
        public Uri showNfo;

        public boolean hasDbId;
        public long dbId;

        public boolean hasNfo() {
            return videoNfo != null;
        }
        public boolean isShow() {
            return hasNfo() && showNfo != null;
        }
        public void setDbId(long dbId) {
            this.dbId = dbId;
            this.hasDbId = true;
        }
    }

    public static NfoFile determineNfoFile(Uri video) {
        if (video == null)
            return null;

        NfoFile result = new NfoFile();
        result.videoFile = video;

        Uri videoParent = result.videoFolder = FileUtils.getParentUrl(video);
        String videoNameNoExt = result.videoFileNameNoExt = FileUtils.getFileNameWithoutExtension(video);
        if (videoParent == null)
            return result;

        // check for our custom .arcnfo files, no show nfo since that is determined by parsed show title
        Uri movieNfoFile = Uri.withAppendedPath(videoParent, videoNameNoExt + CUSTOM_NFO_EXTENSION);
        if (fileOk(movieNfoFile)) {
            result.videoNfo = movieNfoFile;
        } else {
            // 1. there should be a "videoname.nfo" file
            Uri nfoFile = Uri.withAppendedPath(videoParent, videoNameNoExt + NFO_EXTENSION);
            if (fileOk(nfoFile)) {
                result.videoNfo = nfoFile;
                // 2. there could be a tvshow.nfo file in this or the parent folder if it is a tv show
                Uri showNfoFile = Uri.withAppendedPath(videoParent, TV_SHOW_NFO);
                if (fileOk(showNfoFile)) {
                    result.showNfo = showNfoFile;
                } else {
                    // check in parent folder, "Simpsons/Season 1/Ep1.avi" could have
                    // "Simpsons/tvshow.nfo"
                    Uri parentParent = FileUtils.getParentUrl(videoParent);
                    if (parentParent != null) {
                        showNfoFile = Uri.withAppendedPath(parentParent, TV_SHOW_NFO);
                        if (fileOk(showNfoFile)) {
                            result.showNfo = showNfoFile;
                        }
                    }
                }
            } else {
                // 3. single movies in directories could be represented by a movie.nfo file
                movieNfoFile = Uri.withAppendedPath(videoParent, MOVIE_NFO);
                if (fileOk(movieNfoFile)) {
                    result.videoNfo = movieNfoFile;
                }
            }
        }
        return result;
    }

    private static boolean fileOk(Uri file) {
        if(file==null)
            return false;
        MetaFile2 metaFile2 = null;
        try {
            metaFile2 = MetaFileFactoryWithUpnp.getMetaFileForUrl(file);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return metaFile2 != null && metaFile2.isFile();
    }

    public static BaseTags getTagForFile(Uri file, Context context) {
        NfoFile nfo = determineNfoFile(file);
        if (nfo != null && nfo.hasNfo()) {
            return getTagForFile(nfo, context, null);
        }
        return null;
    }

    public static BaseTags getTagForFile(NfoFile nfo, Context context, ImportContext importContext) {
        if (nfo != null && nfo.hasNfo()) {

            if (importContext == null)
                importContext = new ImportContext();
            InputStream nfoInputStream = null;
            try {
                nfoInputStream = FileEditorFactoryWithUpnp.getFileEditorForUrl(nfo.videoNfo, null).getInputStream();
                NfoRootHandler rootHandler = importContext.getRootHandler();
                importContext.getParser().parse(nfoInputStream, rootHandler);
                BaseTags tag = rootHandler.getResult(context, nfo.videoFile);
                rootHandler.clear();
                if (tag != null) {
                    if (tag instanceof MovieTags) {
                        MovieTags movieTags = (MovieTags) tag;

                        // check if we can add local image to posters
                        Uri poster = LocalImages.findPoster(nfo.videoFile);
                        if (poster != null) {
                            movieTags.addDefaultPoster(context, poster, nfo.videoFile);
                        }

                        // check if we can add local image to backdrops
                        Uri backdrop = LocalImages.findBackdrop(nfo.videoFile, null);
                        if (backdrop != null) {
                            movieTags.addDefaultBackdrop(context, backdrop, nfo.videoFile);
                        }

                        tag.downloadPoster(context);
                        return tag;
                    }

                    if (tag instanceof EpisodeTags) {
                        EpisodeTags epTags = (EpisodeTags) tag;
                        ShowTags showTags = null;
                        // try to parse show title based nfo file first
                        String showTitleEncoded = StringUtils.fileSystemEncode(epTags.getShowTitle());
                        if (!TextUtils.isEmpty(showTitleEncoded)) {
                            Uri showNfoFile = Uri.withAppendedPath(nfo.videoFolder, showTitleEncoded + CUSTOM_SHOW_NFO_EXTENSION);
                            showTags = getShowTagsCached(showNfoFile, nfo.videoFile, context, importContext);
                        }
                        // fallback to regular tvshow.nfo
                        if (showTags == null && nfo.isShow()) {
                            showTags = getShowTagsCached(nfo.showNfo, nfo.videoFile, context, importContext);
                        }

                        if (showTags != null) {
                            String showTitle = showTags.getTitle();

                            epTags.setShowTags(showTags);
                            // check if we can add local image as season poster
                            int season = epTags.getSeason();
                            Uri seasonPoster = findSeasonPosterCached(nfo.videoFile, showTitle, season, importContext);
                            if (seasonPoster != null)
                                epTags.addDefaultPoster(context, seasonPoster, showTitle);

                            epTags.downloadPoster(context);
                            return epTags;
                        }
                    }
                }
            } catch (SAXException e) {
                // could not parse
                if (DBG) Log.d(TAG, "Exception: " + e, e);
            } catch (IOException e) {
                // could not read file
                if (DBG) Log.d(TAG, "Exception: " + e, e);
            } catch (Exception e) {
                if (DBG) Log.d(TAG, "Exception: " + e, e);
            }finally {
                if(nfoInputStream!=null)
                    try {
                        nfoInputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
            }
        }
        return null;
    }

    private static Uri findSeasonPosterCached(Uri videoFile, String showTitle, int season, ImportContext importContext) {
        Uri parent = FileUtils.getParentUrl(videoFile);

        if (parent == null)
            return null;

        // build a key based from folder of show + (title of show) + season
        String showTitleKey = TextUtils.isEmpty(showTitle) ? "" : showTitle;
        String key = parent.toString() + '/' + showTitleKey + '/' + String.valueOf(season);
        // check the cache
        Uri result = importContext.seasonPosterCache.get(key);
        if (result == null) {
            // if nothing was cached, check the filesystem
            result = LocalImages.findSeasonPoster(videoFile, showTitle, season);
            if (result != null) {
                // put in cache
                importContext.seasonPosterCache.put(key, result);
            }
        }
        return result;
    }

    private static ShowTags getShowTagsCached(Uri nfoFile, Uri videoFile, Context context, ImportContext importContext) {
        // key = tvshow.nfo path
        String key = nfoFile.toString();
        ShowTags result = importContext.showCache.get(key);
        if (result == null) {
            // not cached, really parse file
            result = parseShowNfo(nfoFile, videoFile, context, importContext);
            // add local images & put in cache if successful
            if (result != null) {
                String showTitle = result.getTitle();

                // check if we can add local image as show poster
                Uri showPoster = LocalImages.findShowPoster(videoFile, showTitle);
                if (showPoster != null) {
                    result.addDefaultPoster(context, showPoster);
                }

                // check if we can add local image as show backdrop
                Uri backdrop = LocalImages.findBackdrop(videoFile, showTitle);
                if (backdrop != null) {
                    result.addDefaultBackdrop(context, backdrop);
                }

                // store in cache
                importContext.showCache.put(key, result);
            }
        }
        return result;
    }

    private static ShowTags parseShowNfo(Uri nfoFile, Uri videoFile, Context context, ImportContext importContext) {
        InputStream nfoInputStream = null;
        try {
            nfoInputStream = FileEditorFactoryWithUpnp.getFileEditorForUrl(nfoFile, null).getInputStream();
            NfoShowHandler showHandler = importContext.getShowHandler();
            SAXParser parser = importContext.getParser();
            parser.parse(nfoInputStream, showHandler);
            ShowTags result = showHandler.getResult(context, videoFile);
            showHandler.clear();
            return result;
        } catch (SAXException e) {
            // could not parse
            if (DBG) Log.d(TAG, "Exception: " + e, e);
        } catch (Exception e) {
            // could not read file
            if (DBG) Log.d(TAG, "Exception: " + e, e);
        }finally {
            if(nfoInputStream!=null)
                try {
                    nfoInputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }
        return null;
    }

    public static boolean isNetworkNfoParseEnabled(Context context) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        String prefKey = context.getString(R.string.network_nfo_parse_prefkey);
        boolean prefDefault = context.getResources().getBoolean(R.bool.network_nfo_parse_default);
        boolean result = pref.getBoolean(prefKey, prefDefault);
        return result;
    }

}
