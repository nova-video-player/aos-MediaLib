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
import android.util.LruCache;
import android.util.Xml;

import com.archos.filecorelibrary.FileEditor;
import com.archos.mediacenter.filecoreextension.upnp2.FileEditorFactoryWithUpnp;
import com.archos.filecorelibrary.FileUtils;
import com.archos.medialib.R;

import org.xmlpull.v1.XmlSerializer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

public class NfoWriter {

    public static class ExportContext {
        private final LruCache<String, String> exportedFiles = new LruCache<String, String>(64);
        public void put(String key) {
            exportedFiles.put(key, "");
        }
        public boolean contains(String key) {
            return exportedFiles.get(key) != null;
        }
    }

    private NfoWriter() {
        // all static
    }

    private static void textTag(XmlSerializer serializer, String tag, String text)
            throws IllegalArgumentException, IllegalStateException, IOException {
        if (text != null && !text.isEmpty()) {
            serializer.startTag("", tag);
            serializer.text(text);
            serializer.endTag("", tag);
        }
    }

    private static void textTag(XmlSerializer serializer, String tag, float value)
            throws IllegalArgumentException, IllegalStateException, IOException {
        if (value != 0f)
            textTag(serializer, tag, String.valueOf(value));
    }

    private static void textTag(XmlSerializer serializer, String tag, int value)
            throws IllegalArgumentException, IllegalStateException, IOException {
        if (value != 0)
            textTag(serializer, tag, String.valueOf(value));
    }

    private static void textTag(XmlSerializer serializer, String tag, long value)
            throws IllegalArgumentException, IllegalStateException, IOException {
        if (value != 0L)
            textTag(serializer, tag, String.valueOf(value));
    }

    private static XmlSerializer initAndStartDocument(Writer output)
            throws IllegalArgumentException, IllegalStateException, IOException {
        XmlSerializer serializer = Xml.newSerializer();
        serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
        serializer.setOutput(output);
        serializer.startDocument(StringUtils.CHARSET_UTF8.name(), Boolean.TRUE);
        return serializer;
    }

    private static void endDocument(XmlSerializer serializer)
            throws IllegalArgumentException, IllegalStateException, IOException {
        serializer.endDocument();
    }

    public static void writeXmlInner(XmlSerializer serializer, MovieTags tag) throws
            IllegalArgumentException,
            IllegalStateException,
            IOException {

        serializer.startTag("", "movie");
        {
            textTag(serializer, "title", tag.getTitle());
            textTag(serializer, "rating", tag.getRating());
            textTag(serializer, "year", tag.getYear());
            textTag(serializer, "outline", tag.getPlot());
            textTag(serializer, "runtime", tag.getRuntime(TimeUnit.MINUTES));
            textTag(serializer, "lastplayed", tag.getLastPlayed(TimeUnit.SECONDS));
            textTag(serializer, "bookmark", tag.getBookmark());
            textTag(serializer, "resume", tag.getResume());
            textTag(serializer, "mpaa", tag.getContentRating());
            for (String director : tag.getDirectors())
                textTag(serializer, "director", director);
            textTag(serializer, "id", tag.getImdbId());
            textTag(serializer, "tmdbid", tag.getOnlineId());
            for (String studio : tag.getStudios())
                textTag(serializer, "studio", studio);
            for (String genre : tag.getGenres())
                textTag(serializer, "genre", genre);
            for (Entry<String, String> entry : tag.getActors().entrySet()) {
                serializer.startTag("", "actor");
                {
                    textTag(serializer, "name", entry.getKey());
                    textTag(serializer, "role", entry.getValue());
                }
                serializer.endTag("", "actor");
            }
            List<ScraperImage> posters = tag.getPosters();
            if (posters != null) {
                for (ScraperImage image : posters) {
                    if (!image.isHttpImage()) continue;
                    serializer.startTag("", "thumb");
                    serializer.attribute("", "preview", image.getThumbUrl());
                    serializer.text(image.getLargeUrl());
                    serializer.endTag("", "thumb");
                }
            }
            List<ScraperImage> backdrops = tag.getBackdrops();
            if (backdrops != null && !backdrops.isEmpty()) {
                serializer.startTag("", "fanart");
                for (ScraperImage image : backdrops) {
                    if (!image.isHttpImage()) continue;
                    serializer.startTag("", "thumb");
                    serializer.attribute("", "preview", image.getThumbUrl());
                    serializer.text(image.getLargeUrl());
                    serializer.endTag("", "thumb");
                }
                serializer.endTag("", "fanart");
            }
        }
        serializer.endTag("", "movie");

    }

    private static final SimpleDateFormat SHOW_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd",
            Locale.ROOT);

    private static String formatShowDate(Date date) {
        return date != null && date.getTime() > 0 ? SHOW_DATE_FORMAT.format(date) : null;
    }

    public static void writeXmlInner(XmlSerializer serializer, EpisodeTags tag) throws
            IllegalArgumentException,
            IllegalStateException,
            IOException {

        serializer.startTag("", "episodedetails");
        {
            textTag(serializer, "title", tag.getTitle());
            textTag(serializer, "showtitle", tag.getShowTags().getTitle());
            textTag(serializer, "rating", tag.getRating());
            textTag(serializer, "season", tag.getSeason());
            textTag(serializer, "episode", tag.getEpisode());
            textTag(serializer, "plot", tag.getPlot());
            textTag(serializer, "mpaa", tag.getContentRating());
            textTag(serializer, "imdbid", tag.getImdbId());
            textTag(serializer, "tvdbid", tag.getOnlineId());
            textTag(serializer, "lastplayed", tag.getLastPlayed(TimeUnit.SECONDS));
            textTag(serializer, "bookmark", tag.getBookmark());
            textTag(serializer, "resume", tag.getResume());
            textTag(serializer, "aired", formatShowDate(tag.getAired()));
            textTag(serializer, "runtime", tag.getRuntime(TimeUnit.MINUTES));
            for (String director : tag.getDirectors())
                textTag(serializer, "director", director);
            for (Entry<String, String> entry : tag.getActors().entrySet()) {
                serializer.startTag("", "actor");
                {
                    textTag(serializer, "name", entry.getKey());
                    textTag(serializer, "role", entry.getValue());
                }
                serializer.endTag("", "actor");
            }
        }
        serializer.endTag("", "episodedetails");

    }

    public static void writeXmlInner(XmlSerializer serializer, ShowTags tag) throws
            IllegalArgumentException,
            IllegalStateException,
            IOException {

        serializer.startTag("", "tvshow");
        {
            textTag(serializer, "title", tag.getTitle());
            textTag(serializer, "rating", tag.getRating());
            textTag(serializer, "plot", tag.getPlot());
            textTag(serializer, "mpaa", tag.getContentRating());
            textTag(serializer, "premiered", formatShowDate(tag.getPremiered()));
            textTag(serializer, "id", tag.getOnlineId());
            textTag(serializer, "tvdbid", tag.getOnlineId());
            textTag(serializer, "imdbid", tag.getImdbId());
            for (String studio : tag.getStudios())
                textTag(serializer, "studio", studio);
            for (String genre : tag.getGenres())
                textTag(serializer, "genre", genre);
            for (Entry<String, String> entry : tag.getActors().entrySet()) {
                serializer.startTag("", "actor");
                {
                    textTag(serializer, "name", entry.getKey());
                    textTag(serializer, "role", entry.getValue());
                }
                serializer.endTag("", "actor");
            }
            // Note: we expect to get all posters here
            List<ScraperImage> posters = tag.getPosters();
            if (posters != null) {
                for (ScraperImage image : posters) {
                    if (!image.isHttpImage()) continue;
                    serializer.startTag("", "thumb");
                    serializer.attribute("", "preview", image.getThumbUrl());
                    int season = image.getSeason();
                    if (season >= 0) {
                        serializer.attribute("", "type", "season");
                        serializer.attribute("", "season", String.valueOf(season));
                    }
                    serializer.text(image.getLargeUrl());
                    serializer.endTag("", "thumb");
                }
            }
            List<ScraperImage> backdrops = tag.getBackdrops();
            if (backdrops != null && !backdrops.isEmpty()) {
                serializer.startTag("", "fanart");
                for (ScraperImage image : backdrops) {
                    if (!image.isHttpImage()) continue;
                    serializer.startTag("", "thumb");
                    serializer.attribute("", "preview", image.getThumbUrl());
                    serializer.text(image.getLargeUrl());
                    serializer.endTag("", "thumb");
                }
                serializer.endTag("", "fanart");
            }
        }
        serializer.endTag("", "tvshow");

    }

    private static void exportInternal(Uri video, MovieTags tag) throws IOException {

        String videoName = FileUtils.getFileNameWithoutExtension(video);
        Uri parent = FileUtils.getParentUrl(video);
        Uri exportTarget =  Uri.withAppendedPath(parent, videoName + NfoParser.CUSTOM_NFO_EXTENSION);
        try {
            FileEditor editor = FileEditorFactoryWithUpnp.getFileEditorForUrl(exportTarget, null);
            // Delete existing file to avoid overwrite issue (end of previous content still there is the new content is shorter)
            if (editor.exists()) {
                editor.delete();
            }
            BufferedWriter  writer = new BufferedWriter(new OutputStreamWriter(
                    editor.getOutputStream(), StringUtils.CHARSET_UTF8));

            try {

                XmlSerializer serializer = initAndStartDocument(writer);
                writeXmlInner(serializer, tag);
                endDocument(serializer);
                writer.close();
                writer = null;
                exportImage(tag.getDefaultPoster(), parent, videoName + NfoParser.POSTER_EXTENSION);
                exportImage(tag.getDefaultBackdrop(), parent, videoName + NfoParser.BACKDROP_EXTENSION);
            } finally {
                if (writer != null) {
                    // writer is only != null if writing nfo has thrown an exception
                    // -> get rid of potentially semi-complete nfo files.
                    editor.delete();
                    IOUtils.closeSilently(writer);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void exportInternal(Uri video, EpisodeTags tag) throws IOException {
        String videoName = FileUtils.getFileNameWithoutExtension(video);
        Uri parent = FileUtils.getParentUrl(video);
        Uri exportTarget =  Uri.withAppendedPath(parent, videoName + NfoParser.CUSTOM_NFO_EXTENSION);
        try {
            FileEditor editor = FileEditorFactoryWithUpnp.getFileEditorForUrl(exportTarget,null);
            // Delete existing file to avoid overwrite issue (end of previous content still there is the new content is shorter)
            if (editor.exists()) {
                editor.delete();
            }
            BufferedWriter  writer = new BufferedWriter(new OutputStreamWriter(
                    editor.getOutputStream(), StringUtils.CHARSET_UTF8));

            try {

                XmlSerializer serializer = initAndStartDocument(writer);
                writeXmlInner(serializer, tag);
                endDocument(serializer);
                writer.close();
                writer = null;
                String image = NfoParser.getCustomSeasonPosterName(tag.getShowTitle(), tag.getSeason());
                exportImage(tag.getDefaultPoster(), parent, image);
            } finally {
                if (writer != null) {
                    // writer is only != null if writing nfo has thrown an exception
                    // -> get rid of potentially semi-complete nfo files.
                    editor.delete();
                    IOUtils.closeSilently(writer);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private static void exportInternal(Uri video, ShowTags tag, ExportContext exportContext) throws IOException {
        String videoName = FileUtils.getFileNameWithoutExtension(video);
        Uri parent = FileUtils.getParentUrl(video);
        String showTitle = StringUtils.fileSystemEncode(tag.getTitle());
        Uri exportTarget =  Uri.withAppendedPath(parent, showTitle + NfoParser.CUSTOM_SHOW_NFO_EXTENSION);
        try {
            FileEditor editor = FileEditorFactoryWithUpnp.getFileEditorForUrl(exportTarget, null);
            // Delete existing file to avoid overwrite issue (end of previous content still there is the new content is shorter)
            if (editor.exists()) {
                editor.delete();
            }
            BufferedWriter  writer = new BufferedWriter(new OutputStreamWriter(
                    editor.getOutputStream(), StringUtils.CHARSET_UTF8));

            try {

                XmlSerializer serializer = initAndStartDocument(writer);
                writeXmlInner(serializer, tag);
                endDocument(serializer);
                writer.close();
                writer = null;
                exportImage(tag.getDefaultPoster(), parent,  showTitle + NfoParser.POSTER_EXTENSION);
                exportImage(tag.getDefaultBackdrop(), parent, showTitle + NfoParser.BACKDROP_EXTENSION);
            } finally {
                if (writer != null) {
                    // writer is only != null if writing nfo has thrown an exception
                    // -> get rid of potentially semi-complete nfo files.
                    editor.delete();
                    IOUtils.closeSilently(writer);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void export(Uri video, BaseTags tag, ExportContext exportContext) throws IOException {
        if (tag instanceof MovieTags) {
            exportInternal(video, (MovieTags)tag);
        } else if (tag instanceof EpisodeTags) {
            EpisodeTags etag = (EpisodeTags) tag;
            exportInternal(video, etag);
            // checks if this showTags was already exported within this context
            exportInternal(video, etag.getShowTags(), exportContext);
        }
    }

    private static void exportImage(ScraperImage image, Uri folder,
            String imageName) {

        //TODO test
        Uri target = Uri.withAppendedPath(folder, imageName);
        if (image != null) {
            File file = image.getLargeFileF();
            Uri from = Uri.parse(file.getAbsolutePath());
            FileEditor editor = FileEditorFactoryWithUpnp.getFileEditorForUrl(from, null);
            if (from!=null && editor.exists()) {
                if (target != null) {
                    try {
                        editor.copyFileTo(target, null);
                        target = null;
                    } catch (Exception e) {
                        // target will not be null > will be deleted
                    }
                }
            }
        }


    }

    public static boolean isNfoAutoExportEnabled(Context context) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        String prefKey = context.getString(R.string.nfo_export_auto_prefkey);
        boolean prefDefault = context.getResources().getBoolean(R.bool.nfo_export_auto_default);
        boolean result = pref.getBoolean(prefKey, prefDefault);
        return result;
    }
}
