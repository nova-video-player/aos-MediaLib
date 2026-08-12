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
import androidx.preference.PreferenceManager;
import android.text.TextUtils;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public class NfoParser {

    private static final Logger log = LoggerFactory.getLogger(NfoParser.class);

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
        // Handlers match element names through SAX localName. Make that contract
        // explicit; otherwise standard JVM parsers return an empty localName.
        parserFactory.setNamespaceAware(true);
        // NFO files are untrusted external content (network shares, USB, downloaded
        // libraries). Disable DOCTYPEs/external entities/external DTD loading to avoid
        // XXE, SSRF and entity-expansion attacks. NFO files never legitimately need them.
        setFeatureQuietly(parserFactory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        setFeatureQuietly(parserFactory, "http://xml.org/sax/features/external-general-entities", false);
        setFeatureQuietly(parserFactory, "http://xml.org/sax/features/external-parameter-entities", false);
        setFeatureQuietly(parserFactory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        try {
            return parserFactory.newSAXParser();
        } catch (ParserConfigurationException | SAXException e) {
            log.error("Exception", e);
            throw new RuntimeException(e);
        }
    }

    private static void setFeatureQuietly(SAXParserFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (ParserConfigurationException | SAXException e) {
            // feature not supported on this XML stack, ignore
            log.debug("setFeatureQuietly: {} not supported", feature);
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

        // relocate uri for local files to writeable location to comply with API30
        Uri videoParent = result.videoFolder = FileUtils.relocateNfoAppPublicDir(FileUtils.getParentUrl(video));
        String videoNameNoExt = result.videoFileNameNoExt = FileUtils.getFileNameWithoutExtension(video);
        if (videoParent == null)
            return result;

        // check for our custom .arcnfo files first
        Uri movieNfoFile = Uri.withAppendedPath(videoParent, videoNameNoExt + CUSTOM_NFO_EXTENSION);
        if (fileOk(movieNfoFile)) {
            result.videoNfo = movieNfoFile;
            // a custom .archos.nfo episode resolves its show through the parsed show title,
            // but a regular tvshow.nfo may still be the only show metadata available, so look it up too
            result.showNfo = findShowNfo(videoParent);
        } else {
            // 1. there should be a "videoname.nfo" file
            Uri nfoFile = Uri.withAppendedPath(videoParent, videoNameNoExt + NFO_EXTENSION);
            if (fileOk(nfoFile)) {
                result.videoNfo = nfoFile;
                // 2. there could be a tvshow.nfo file in this or the parent folder if it is a tv show
                result.showNfo = findShowNfo(videoParent);
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

    /** Locates a tvshow.nfo in the given folder, falling back to its parent folder
     * ("Simpsons/Season 1/Ep1.avi" -> "Simpsons/tvshow.nfo"). Returns null if none. */
    private static Uri findShowNfo(Uri videoParent) {
        Uri showNfoFile = Uri.withAppendedPath(videoParent, TV_SHOW_NFO);
        if (fileOk(showNfoFile))
            return showNfoFile;
        Uri parentParent = FileUtils.getParentUrl(videoParent);
        if (parentParent != null) {
            showNfoFile = Uri.withAppendedPath(parentParent, TV_SHOW_NFO);
            if (fileOk(showNfoFile))
                return showNfoFile;
        }
        return null;
    }

    private static boolean fileOk(Uri file) {
        if(file==null)
            return false;
        MetaFile2 metaFile2 = null;
        try {
            metaFile2 = MetaFileFactoryWithUpnp.getMetaFileForUrl(file);
        } catch (Exception e) {
            // Most calls are speculative existence probes, so a missing candidate is expected.
            // Some backends (notably legacy SFTP) report missing files as a generic permission error.
            if (log.isDebugEnabled()) log.debug("fileOk: could not stat {}: {}", file, e.toString());
        }
        return metaFile2 != null && metaFile2.isFile();
    }

    public static BaseTags getTagForFile(Uri file, Context context) {
        NfoFile nfo = determineNfoFile(file);
        if (log.isDebugEnabled()) log.debug("getTagForFile: found nfo file {}, nfo.hasNfo()={}", nfo.videoNfo, nfo.hasNfo());
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
            NfoRootHandler rootHandler = null;
            try {
                // relocate uri for local files to writeable location to comply with API30
                nfoInputStream = FileEditorFactoryWithUpnp
                        .getFileEditorForUrl(FileUtils.relocateNfoAppPublicDirForNfoJpgFiles(
                                nfo.videoNfo), null).getInputStream();
                rootHandler = importContext.getRootHandler();
                // clear before parsing: handlers are reused across files in a shared
                // ImportContext, and a previous parse aborted by an exception can leave
                // stale movie/episode state behind
                rootHandler.clear();
                importContext.getParser().parse(nfoInputStream, rootHandler);
                BaseTags tag = rootHandler.getResult(context, nfo.videoFile);
                if (tag != null) {
                    if (tag instanceof MovieTags) {
                        MovieTags movieTags = (MovieTags) tag;

                        // check if we can add local image to posters
                        Uri poster = LocalImages.findPoster(nfo.videoFile);
                        if (poster != null) {
                            movieTags.addDefaultPoster(context, poster, nfo.videoFile);
                        }

                        // check if we can add local image to backdrops
                        Uri backdrop = LocalImages.findBackdrop(nfo.videoFile, null, false);
                        if (backdrop != null) {
                            movieTags.addDefaultBackdrop(context, backdrop, nfo.videoFile);
                        }

                        tag.downloadPoster(context);
                        return tag;
                    }

                    if (tag instanceof EpisodeTags) {
                        EpisodeTags epTags = (EpisodeTags) tag;
                        ShowTags showTags = resolveEpisodeShowTags(nfo, epTags, context, importContext);

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
                log.error("XML parsing failed for the NFO file.", e);
            } catch (IOException e) {
                // could not read file
                log.error("Failed to read the NFO file.", e);
            } catch (Exception e) {
                log.error("Failed to read the NFO file.", e);
            } finally {
                // always clear so a partial result from an aborted parse cannot leak
                // into the next file that reuses this ImportContext; only touch the handler
                // if it was actually obtained so a construction failure stays fail-soft
                if (rootHandler != null) rootHandler.clear();
                if(nfoInputStream!=null)
                    try {
                        nfoInputStream.close();
                    } catch (IOException e) {
                        log.error("Failure closing stream", e);
                    }
            }
        }
        return null;
    }

    /**
     * Resolve the show metadata for an episode. SMB metadata probes can transiently report that a
     * show-root {@code tvshow.nfo} does not exist even though opening that same URI succeeds. Do not
     * make the episode import depend exclusively on the earlier stat result: try the conventional
     * same-folder and parent-folder paths directly after any discovered/custom candidate.
     */
    private static ShowTags resolveEpisodeShowTags(NfoFile nfo, EpisodeTags epTags, Context context,
            ImportContext importContext) {
        Set<String> attempted = new HashSet<>();
        String showTitleEncoded = StringUtils.fileSystemEncode(epTags.getShowTitle());

        // Nova's custom show NFO may be next to the episode or in the show root. Only probe custom
        // candidates that stat successfully; their absence is normal and must not emit an error.
        if (!TextUtils.isEmpty(showTitleEncoded)) {
            String customName = showTitleEncoded + CUSTOM_SHOW_NFO_EXTENSION;
            ShowTags result = parseExistingShowCandidate(
                    Uri.withAppendedPath(nfo.videoFolder, customName), nfo.videoFile, context,
                    importContext, attempted);
            if (result != null) return result;

            Uri showFolder = FileUtils.getParentUrl(nfo.videoFolder);
            if (showFolder != null) {
                result = parseExistingShowCandidate(Uri.withAppendedPath(showFolder, customName),
                        nfo.videoFile, context, importContext, attempted);
                if (result != null) return result;
            }
        }

        // Prefer the path discovered during determineNfoFile().
        ShowTags result = parseShowCandidate(nfo.showNfo, nfo.videoFile, context, importContext,
                attempted, true);
        if (result != null) return result;

        // Retry standard paths by opening them directly. This intentionally does not trust fileOk():
        // the failing #1782 SMB trace found every episode NFO and local image but missed tvshow.nfo.
        Uri sameFolder = Uri.withAppendedPath(nfo.videoFolder, TV_SHOW_NFO);
        result = parseShowCandidate(sameFolder, nfo.videoFile, context, importContext, attempted,
                false);
        if (result != null) return result;

        Uri showFolder = FileUtils.getParentUrl(nfo.videoFolder);
        if (showFolder != null) {
            Uri parentFolder = Uri.withAppendedPath(showFolder, TV_SHOW_NFO);
            result = parseShowCandidate(parentFolder, nfo.videoFile, context, importContext,
                    attempted, false);
            if (result != null) return result;
        }

        log.warn("resolveEpisodeShowTags: no usable show NFO for {} after trying {}",
                nfo.videoFile, attempted);
        return null;
    }

    private static ShowTags parseExistingShowCandidate(Uri candidate, Uri videoFile, Context context,
            ImportContext importContext, Set<String> attempted) {
        if (candidate == null || !fileOk(candidate)) return null;
        return parseShowCandidate(candidate, videoFile, context, importContext, attempted, true);
    }

    private static ShowTags parseShowCandidate(Uri candidate, Uri videoFile, Context context,
            ImportContext importContext, Set<String> attempted, boolean logFailure) {
        if (candidate == null || !attempted.add(candidate.toString())) return null;
        if (log.isDebugEnabled()) {
            log.debug("resolveEpisodeShowTags: trying {} for {}", candidate, videoFile);
        }
        return getShowTagsCached(candidate, videoFile, context, importContext, logFailure);
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

    private static ShowTags getShowTagsCached(Uri nfoFile, Uri videoFile, Context context,
            ImportContext importContext, boolean logFailure) {
        // key = tvshow.nfo path
        String key = nfoFile.toString();
        ShowTags result = importContext.showCache.get(key);
        if (result == null) {
            // not cached, really parse file
            result = parseShowNfo(nfoFile, videoFile, context, importContext, logFailure);
            // add local images & put in cache if successful
            if (result != null) {
                String showTitle = result.getTitle();

                // check if we can add local image as show poster
                Uri showPoster = LocalImages.findShowPoster(videoFile, showTitle);
                if (showPoster != null) {
                    result.addDefaultPoster(context, showPoster);
                }

                // check if we can add local image as show backdrop
                Uri backdrop = LocalImages.findBackdrop(videoFile, showTitle, true);
                if (backdrop != null) {
                    result.addDefaultBackdrop(context, backdrop);
                }

                // store in cache
                importContext.showCache.put(key, result);
            }
        }
        return result;
    }

    private static ShowTags parseShowNfo(Uri nfoFile, Uri videoFile, Context context,
            ImportContext importContext, boolean logFailure) {
        InputStream nfoInputStream = null;
        NfoShowHandler showHandler = null;
        try {
            nfoInputStream = FileEditorFactoryWithUpnp
                    .getFileEditorForUrl(nfoFile, null).getInputStream();
            showHandler = importContext.getShowHandler();
            SAXParser parser = importContext.getParser();
            // clear before parsing in case a previous parse left stale state behind
            showHandler.clear();
            parser.parse(nfoInputStream, showHandler);
            ShowTags result = showHandler.getResult(context, videoFile);
            return result;
        } catch (SAXException e) {
            // could not parse
            if (logFailure) log.error("XML parsing failed for show NFO {}", nfoFile, e);
            else log.debug("parseShowNfo: XML parsing failed for candidate {}: {}", nfoFile,
                    e.toString());
        } catch (Exception e) {
            // could not read file
            if (logFailure) log.error("Failed to read show NFO {}", nfoFile, e);
            else log.debug("parseShowNfo: could not read candidate {}: {}", nfoFile,
                    e.toString());
        }finally {
            // always clear so an aborted parse cannot leak into the next show parse;
            // only if the handler was obtained, to keep a construction failure fail-soft
            if (showHandler != null) showHandler.clear();
            if(nfoInputStream!=null)
                try {
                    nfoInputStream.close();
                } catch (IOException e) {
                    log.error("Failure closing stream", e);
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
