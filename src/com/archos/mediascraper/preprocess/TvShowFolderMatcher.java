
package com.archos.mediascraper.preprocess;

import android.net.Uri;

import com.archos.filecorelibrary.FileUtils;
import com.archos.mediascraper.ShowUtils;
import com.archos.mediascraper.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.archos.filecorelibrary.FileUtils.getName;
import static com.archos.mediascraper.StringUtils.removeTrailingSlash;

/**
 * Matches all sorts of "Tv Show title S01E01/randomgarbage.mkv" and similar things
 */
class TvShowFolderMatcher extends TvShowMatcher {
    private static final Logger log = LoggerFactory.getLogger(TvShowFolderMatcher.class);

    public static TvShowFolderMatcher instance() {
        return INSTANCE;
    }

    private static final TvShowFolderMatcher INSTANCE =
            new TvShowFolderMatcher();

    private TvShowFolderMatcher() {
        // singleton
    }

    @Override
    public boolean matchesFileInput(Uri fileInput, Uri simplifiedUri) {
        return ShowUtils.isTvShow(FileUtils.getParentUrl(fileInput), null);
    }

    @Override
    public SearchInfo getFileInputMatch(Uri file, Uri simplifiedUri) {
        return getMatch(getName(FileUtils.getParentUrl(file)), file);
    }

    private static SearchInfo getMatch(String matchString, Uri file) {
        log.debug("getMatch: matchString " + matchString + " file " + file.getPath());
        // clean trailing "/" if exists
        matchString = removeTrailingSlash(matchString);
        // clean leading "/"
        matchString = getName(matchString);
        Map<String, String> showName = ShowUtils.parseShowName(matchString);
        if (showName != null) {
            String showTitle = showName.get(ShowUtils.SHOW);
            String season = showName.get(ShowUtils.SEASON);
            String episode = showName.get(ShowUtils.EPNUM);
            int seasonInt = StringUtils.parseInt(season, 0);
            int episodeInt = StringUtils.parseInt(episode, 0);
            log.debug("getMatch: " + showTitle + " season " + season + " episode " + episode);
            return new TvShowSearchInfo(file, showTitle, seasonInt, episodeInt);
        }
        return null;
    }

    @Override
    public String getMatcherName() {
        return "TVShowFolder";
    }

}
