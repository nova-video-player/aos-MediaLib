
package com.archos.mediascraper.preprocess;

import android.net.Uri;

import com.archos.filecorelibrary.MetaFile;
import com.archos.filecorelibrary.Utils;
import com.archos.mediascraper.ShowUtils;
import com.archos.mediascraper.StringUtils;

import java.util.Map;
import java.net.URI;
import java.io.File;

/**
 * Matches all sorts of "Tv Show title S01E01/randomgarbage.mkv" and similar things
 */
class TvShowFolderMatcher extends TvShowMatcher {

    public static TvShowFolderMatcher instance() {
        return INSTANCE;
    }

    private static final TvShowFolderMatcher INSTANCE =
            new TvShowFolderMatcher();

    private TvShowFolderMatcher() {
        // singleton
    }

    @Override
    public boolean matchesFileInput(Uri fileInput) {
        return ShowUtils.isTvShow(fileInput, null);
    }

    @Override
    public SearchInfo getFileInputMatch(Uri file) {
        try {
	    File f = new File(new URI(file.toString()));

            return getMatch(f.getParentFile().getName(), file);
	} catch(java.net.URISyntaxException e) {
	    return null;
	}
    }

    private static SearchInfo getMatch(String matchString, Uri file) {
        Map<String, String> showName = ShowUtils.parseShowName(matchString);
        if (showName != null) {
            String showTitle = showName.get(ShowUtils.SHOW);
            String season = showName.get(ShowUtils.SEASON);
            String episode = showName.get(ShowUtils.EPNUM);
            int seasonInt = StringUtils.parseInt(season, 0);
            int episodeInt = StringUtils.parseInt(episode, 0);
            return new TvShowSearchInfo(file, showTitle, seasonInt, episodeInt);
        }
        return null;
    }

    @Override
    public String getMatcherName() {
        return "TVShowFolder";
    }

}
