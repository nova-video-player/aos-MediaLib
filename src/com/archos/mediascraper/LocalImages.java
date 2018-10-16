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
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.FileUtils;
import com.archos.mediacenter.filecoreextension.upnp2.FileEditorFactoryWithUpnp;

import java.io.File;
import java.util.Locale;

/**
 * Checks if there is a picture stored along with a file that can be used as
 * poster for this video.
 *
 * Assuming there is a video called
 * <code>/Movies/Transformers/transformers.avi</code> then this
 * will try to find images matching the following
 * <ul>
 * <li><code>/Movies/Transformers/transformers-poster.(png, jpg)</code>
 * <li><code>/Movies/Transformers/transformers.(tbn, png, jpg)</code>
 * <li><code>/Movies/Transformers/folder.(jpg, tbn)</code>
 * <li><code>/Movies/Transformers/poster.(png, jpg)</code>
 * <li><code>/Movies/Transformers.tbn</code>
 * </ul>
 *
 * List compiled from <code>http://wiki.xbmc.org/index.php?title=thumbnails</code> and
 * <code>http://wiki.xbmc.org/index.php?title=Frodo_FAQ#Local_images</code>
 * <p>
 * Images are rescaled and copied to local app cache on success and served from there
 * once an image was found.
 * <p>
 * Note: Operation is quite expensive since it tests for several images before
 * returning a result. Use in background.
 */
public class LocalImages {

    private static final String TAG = LocalImages.class.getSimpleName();
    private static final boolean DBG = false;

    private LocalImages() {
        /* all static */
    }

    /**
     * Checks app local cache if there is a cached thumbnail for a video file
     * Cached thumbnails are stored by using the MD5 of the video path as filename
     * <p>
     * In case of emergency safe to use from within the UI thread.
     *
     * @return null if there is no image in the cache, the cached File otherwise
     */
    public static File lookupLocalPoster(Uri video, Context context) {
        if (video != null) {
            File result = path2File(video.toString(), context);
            if (result != null && result.exists()) {
                if (DBG) Log.d(TAG, "Found cached Poster:" + result.getPath() + " for " +video.toString());
                return result;
            }
        }
        return null;
    }

    /**
     * Generates and returns a scaled copy of the best poster image found within
     * the filesystem. Images will be put into the app cache. Will return result
     * from cache if it's already there.
     * <p>
     * Not safe to use from within the UI thread since it's accessing external
     * file systems.
     *
     * @return null if no image could be found, the rescaled locally cached file
     *         otherwise
     */
    public static File generateLocalPoster(Uri video, Context context) {
        if (video == null)
            return null;

        File result = lookupLocalPoster(video, context);
        if (result == null ) {
            Uri poster = findPoster(video);
            if (poster != null) {
                if (DBG) Log.d(TAG, "Found Poster:" + poster.toString() + " for " + video.toString());
                result = saveResized(video, poster, context);
            }
        }
        return result;
    }

    /* Priority 1: %filename% + this */
    private static final String[] MATCH_LIST_DYNAMIC = {
        NfoParser.POSTER_EXTENSION,
        "-poster.jpg",
        "-poster.png",
        ".tbn",
        ".png",
        ".jpg",
    };

    /* Priority 2: static file names */
    private static final String[] MATCH_LIST_STATIC = {
        "poster.png",
        "poster.jpg",
        "folder.tbn",
        "folder.jpg",
    };

    /** tries to find a poster for a given video, null if nothing found */
    public static Uri findPoster(Uri video) {
        if (video == null)
            return null;

        // video = e.g. smb://server/share/Movies/The Movie/The Movie.avi

        Uri parent = FileUtils.getParentUrl(video);
        String nameNoExt =FileUtils.getFileNameWithoutExtension(video);
        if (parent != null && nameNoExt != null&&!nameNoExt.isEmpty()) {
            // check for smb://server/share/Movies/The Movie/The Movie.tbn
            for (String test : MATCH_LIST_DYNAMIC) {
                if(!FileUtils.isSlowRemote(parent)&&FileUtils.isOnAShare(parent)) {
                    Uri result = getIfAvailable(parent, nameNoExt + test);
                    if (result != null)
                        return result;
                }
            }
            // check for smb://server/share/Movies/The Movie/folder.jpg
            for (String test : MATCH_LIST_STATIC) {
                if(!FileUtils.isSlowRemote(parent)&&FileUtils.isOnAShare(parent)) {
                    Uri result = getIfAvailable(parent, test);
                    if (result != null)
                        return result;
                }
            }
            // check for smb://server/share/Movies/The Movie.tbn (folder thumbnail)
            Uri grandParent = FileUtils.getParentUrl(parent);
            if (grandParent != null&&FileUtils.isOnAShare(grandParent)) {
                if(!FileUtils.isSlowRemote(parent)) {
                    Uri result = getIfAvailable(grandParent, parent.getLastPathSegment() + ".tbn");
                    if (result != null)
                        return result;
                }
            }
        }
        return null;
    }

    private static final String[] SHOW_POSTERS = {
        "poster.jpg",
        "poster.png",
        "season-all.tbn",
        "folder.jpg",
        "folder.tbn",
    };

    public static Uri findShowPoster(Uri video, String showTitle) {
        if (video == null)
            return null;

        Uri parent = com.archos.filecorelibrary.FileUtils.getParentUrl(video);
        boolean testShowTitle = !TextUtils.isEmpty(showTitle);
        // we create "show name-poster.jpg" files
        String showTitleFile = testShowTitle ? NfoParser.getCustomShowPosterName(showTitle) : "";
        // xmbc uses just "poster.(tbn/jpg/png) or season-all.tbn

        if (parent != null) {
            // 1. check if our custom file exists
            if (testShowTitle) {
                Uri result = getIfAvailable(parent, showTitleFile);
                if (result != null)
                    return result;
            }
            // 2. check all other poster files
            for (String filename : SHOW_POSTERS) {
                Uri result = getIfAvailable(parent, filename);
                if (result != null)
                    return result;
            }
            // 3. episodes can be in season subfolders like
            // smb://server/share/TvShows/The Simpsons/Season 01/TheSimpsons.S01E01.avi
            // so check for images like
            // smb://server/share/TvShows/The Simpsons/poster.jpg
            Uri grandParent = FileUtils.getParentUrl(parent);
            if (grandParent != null) {
                for (String filename : SHOW_POSTERS) {
                    Uri result = getIfAvailable(grandParent, filename);
                    if (result != null)
                        return result;
                }
            }
        }
        return null;
    }

    public static Uri findSeasonPoster(Uri video, String showTitle, int season) {
        if (video == null || season <= 0)
            return null;

        Uri parent = FileUtils.getParentUrl(video);
        boolean testShowTitle = !TextUtils.isEmpty(showTitle);
        // we create "show name-season03.jpg" files
        String showTitleFile = testShowTitle ? NfoParser.getCustomSeasonPosterName(showTitle, season) : "";
        // xmbc uses just "season03.(tbn/jpg/png)
        String seasonFileNoExt = String.format(Locale.ROOT, "season%02d", Integer.valueOf(season));
        String[] seasonFiles = {
                seasonFileNoExt + ".tbn",
                seasonFileNoExt + ".jpg",
                seasonFileNoExt + ".png",
        };

        if (parent != null) {
            // 1. check if our custom file exists
            if (testShowTitle) {
                Uri result = getIfAvailable(parent, showTitleFile);
                if (result != null)
                    return result;
            }
            // 2. check all other seasonXX files
            for (String filename : seasonFiles) {
                Uri result = getIfAvailable(parent, filename);
                if (result != null)
                    return result;
            }
            // 3. episodes can be in season subfolders like
            // smb://server/share/TvShows/The Simpsons/Season 01/TheSimpsons.S01E01.avi
            // so check for images like
            // smb://server/share/TvShows/The Simpsons/season01.tbn

            //TODO test
            Uri grandParent = FileUtils.getParentUrl(parent);
            if (grandParent != null) {
                Uri result = getIfAvailable(grandParent, parent.getLastPathSegment() + ".tbn");
                if (result != null)
                    return result;
            }
        }
        return null;
    }

    /** %filename% + this */
    private static final String[] MATCH_LIST_BD_DYNAMIC = {
        NfoParser.BACKDROP_EXTENSION,
        "-fanart.jpg",
        "-fanart.png",
    };

    /** this as filename */
    private static final String[] MATCH_LIST_BD_STATIC = {
        "fanart.png",
        "fanart.jpg",
    };

    /**
     * Tries to find a backdrop / fanart image for given video.
     * If videoTitle is given also tries to find an image that is based on
     * that title in addition to filename based images
     */
    public static Uri findBackdrop(Uri video, String videoTitle) {
        if (video == null)
            return null;

        Uri result = null;
        // video = e.g. smb://server/share/Movies/Transformers/Tansformers.3.1080p.avi

        // parent = smb://server/share/Movies/Transformers/
        Uri parent = FileUtils.getParentUrl(video);

        // nameNoExt = Tansformers.3.1080p
        String nameNoExt =  FileUtils.getFileNameWithoutExtension(video);

        if (parent != null && nameNoExt != null) {
            boolean testVideoTitle = !TextUtils.isEmpty(videoTitle);
            String videoTitleSanitized = testVideoTitle ? StringUtils.fileSystemEncode(videoTitle) : "";
            for (String extension : MATCH_LIST_BD_DYNAMIC) {
                if (testVideoTitle) {
                    result = getIfAvailable(parent, videoTitleSanitized + extension);
                    if (result != null)
                        return result;
                }
                result = getIfAvailable(parent, nameNoExt + extension);
                if (result != null)
                    return result;
            }
            for (String extension : MATCH_LIST_BD_STATIC) {
                result = getIfAvailable(parent, nameNoExt + extension);
                if (result != null)
                    return result;
            }
        }
        return result;
    }

    /** returns Uri only if /folder/folder/.../name is an existing file */
    private static Uri getIfAvailable(Uri folder, String name) {
        Uri toReturn = Uri.withAppendedPath(folder, name);
        FileEditor editor = FileEditorFactoryWithUpnp.getFileEditorForUrl(toReturn, null);
        return editor.exists()?toReturn:null;
    }

    /**
     * converts a full path to the resulting image inside our cache directory
     * e.g. "smb://server/share/video.avi"
     * >> /data/data/[package]/cache/images/ahxhaihafcbnaihaihgacn
     * resulting filename is MD5 hashed path
     **/
    private static File path2File(String path, Context context) {
        if (path != null) {
            String encoded = HashGenerator.hash(path);
            if (encoded != null) {
                File imageCacheDirectory = MediaScraper.getImageCacheDirectory(context);
                // if dir does not exists, create it.
                if (!imageCacheDirectory.exists())
                    imageCacheDirectory.mkdirs();

                File thumbFile = new File(imageCacheDirectory, encoded);
                return thumbFile;
            }
        }
        return null;
    }

    /** saves a rescaled copy of given poster / video combination to cache */
    private static File saveResized(Uri video, Uri poster, Context context) {
        File result = path2File(video.toString(), context);
        if (result != null) {
            boolean saved = ImageScaler.scale(poster, result.getAbsolutePath(),
                    ScraperImage.POSTER_WIDTH, ScraperImage.POSTER_HEIGHT,
                    ImageScaler.Type.SCALE_INSIDE);
            if (saved && result.exists())
                return result;
        }
        return null;
    }
}
