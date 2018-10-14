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


package com.archos.mediascraper.saxhandler;

import android.content.Context;

import com.archos.mediascraper.ScraperImage;
import com.archos.mediascraper.ScraperImage.Type;

import org.xml.sax.Attributes;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Sax Handler for results from
 * http://www.thetvdb.com/api/api-key/series/$ID$/all/$LANGUAGE$.zip//banners.xml
 * 
 */
/*-
<Banners>
  <!-- A season cover -->
  <Banner>
    <id>149841</id>
    <BannerPath>seasons/76290-5-6.jpg</BannerPath>
    <BannerType>season</BannerType>
    <BannerType2>season</BannerType2>
    <Language>en</Language>
    <Rating>8.7143</Rating>
    <RatingCount>7</RatingCount>
    <Season>5</Season>
  </Banner>
  <!-- A show cover -->
  <Banner>
    <id>34064</id>
    <BannerPath>posters/76290-4.jpg</BannerPath>
    <BannerType>poster</BannerType>
    <BannerType2>680x1000</BannerType2>
    <Language>en</Language>
    <Rating>8.8947</Rating>
    <RatingCount>19</RatingCount>
  </Banner>
  <!-- A backdrop image -->
  <Banner>
    <id>290791</id>
    <BannerPath>fanart/original/76290-23.jpg</BannerPath>
    <BannerType>fanart</BannerType>
    <BannerType2>1920x1080</BannerType2>
    <Colors>|101,102,97|110,114,113|133,134,128|</Colors>
    <Language>en</Language>
    <Rating>10.0000</Rating>
    <RatingCount>5</RatingCount>
    <SeriesName>false</SeriesName>
    <ThumbnailPath>_cache/fanart/original/76290-23.jpg</ThumbnailPath>
    <VignettePath>fanart/vignette/76290-23.jpg</VignettePath>
  </Banner>
</Banners>
*/
public class ShowBannersHandler extends BasicHandler {
    // private final static String TAG = "ShowAllDetailsHandler";
    // private final static boolean DBG = true;

    private CoverResult mResult = null;
    private String mCurrentBannerPath = null;
    private int mCurrentBannerMajorType = BANNER_M_TYPE_NONE;
    private int mCurrentBannerSubType = BANNER_S_TYPE_ANY;
    private int mCurrentSeason = -1;

    private static final int BANNER_M_TYPE_NONE = 0;
    private static final int BANNER_M_TYPE_BACKDROP = 1;
    private static final int BANNER_M_TYPE_SEASON_POSTER_OR_BANNER = 2;
    private static final int BANNER_M_TYPE_SHOW_POSTER = 3;
    private static final int BANNER_M_TYPE_SHOW_BANNER = 4;

    private static final int BANNER_S_TYPE_ANY = 0;
    private static final int BANNER_S_TYPE_SEASON_POSTER = 1;
    private static final int BANNER_S_TYPE_SEASON_BANNER = 2;

    private final static String ELEMENT_1BANNER = "Banner";
    private final static String ELEMENT_2BANNER_PATH = "BannerPath";
    private final static String ELEMENT_2BANNER_TYPE = "BannerType";
    private final static String ELEMENT_2BANNER_TYPE_BACKDROP = "fanart";
    private final static String ELEMENT_2BANNER_TYPE_SEASON_POSTER_OR_BANNER = "season";
    private final static String ELEMENT_2BANNER_TYPE_SHOW_POSTER = "poster";
    private final static String ELEMENT_2BANNER_TYPE_SHOW_BANNER = "series";
    private final static String ELEMENT_2BANNER_TYPE2 = "BannerType2";
    private final static String ELEMENT_2BANNER_TYPE2_SEASON_POSTER = "season";
    private final static String ELEMENT_2BANNER_TYPE2_SEASON_BANNER = "seasonwide";
    private final static String ELEMENT_2SEASON = "Season";

    private final static String COVER_START = "https://www.thetvdb.com/banners/_cache/";
    private final static String COVER_THUMB_START = "https://www.thetvdb.com/banners/";
    private final static String BACKDROP_START = "https://www.thetvdb.com/banners/";
    private final static String BACKDROP_THUMB_START = "https://www.thetvdb.com/banners/_cache/";
    private String mLanguage="";

    public static class CoverResult {
        public final List<ScraperImage> posters = new ArrayList<ScraperImage>();
        public final List<ScraperImage> backdrops = new LinkedList<ScraperImage>();
    }

    private ScraperImage fallbackBanner;
    private List<ScraperImage> showposters;
    private List<ScraperImage> seasonposters;
    private String mNameSeed;
    private final Context mContext;

    public ShowBannersHandler(Context context) {
        mContext = context;
    }

    public CoverResult getResult() {
        CoverResult result = mResult;
        mResult = null;
        // make sure this is never null
        if (result == null)
            result = new CoverResult();
        return result;
    }

    @Override
    protected void startFile() {
        mResult = new CoverResult();
        showposters = new LinkedList<ScraperImage>();
        seasonposters = new LinkedList<ScraperImage>();
        fallbackBanner = null;
    }

    @Override
    protected boolean startItem(int hierarchyLevel, String uri, String localName, String qName,
            Attributes attributes) {
        if(localName.equals("Language"))
            return true;
        if (hierarchyLevel == 1 && ELEMENT_1BANNER.equals(localName)) {
            mCurrentBannerPath = null;
            mCurrentBannerMajorType = BANNER_M_TYPE_NONE;
            mCurrentBannerSubType = BANNER_S_TYPE_ANY;
            mCurrentSeason = -1;
            return false;
        }
        if (hierarchyLevel == 2) {
            if (ELEMENT_2BANNER_PATH.equals(localName)) {
                return true;
            } else if (ELEMENT_2BANNER_TYPE.equals(localName)) {
                return true;
            } else if (ELEMENT_2BANNER_TYPE2.equals(localName)) {
                return true;
            } else if (ELEMENT_2SEASON.equals(localName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void endItem(int hierarchyLevel, String uri, String localName, String qName) {
        if("Language".equals(localName))
            mLanguage = getString();
        if (hierarchyLevel == 1 && ELEMENT_1BANNER.equals(localName)) {
            ScraperImage image = null;

            switch (mCurrentBannerMajorType) {
                case BANNER_M_TYPE_BACKDROP:
                    image = new ScraperImage(Type.SHOW_BACKDROP, mNameSeed);
                    image.setLargeUrl(BACKDROP_START + mCurrentBannerPath);
                    image.setThumbUrl(BACKDROP_THUMB_START + mCurrentBannerPath);
                    image.setLanguage(mLanguage);
                    image.generateFileNames(mContext);
                    mResult.backdrops.add(image);
                    break;
                case BANNER_M_TYPE_SHOW_POSTER:
                    image = new ScraperImage(Type.SHOW_POSTER, mNameSeed);
                    image.setLargeUrl(COVER_THUMB_START + mCurrentBannerPath);
                    image.setThumbUrl(COVER_START + mCurrentBannerPath);
                    image.generateFileNames(mContext);
                    showposters.add(image);
                    break;
                case BANNER_M_TYPE_SEASON_POSTER_OR_BANNER:
                    // ignore season banners.
                    if (mCurrentBannerSubType == BANNER_S_TYPE_SEASON_POSTER) {

                        image = new ScraperImage(Type.EPISODE_POSTER, mNameSeed);
                        image.setLargeUrl(COVER_THUMB_START + mCurrentBannerPath);
                        image.setThumbUrl(COVER_START + mCurrentBannerPath);
                        image.setLanguage(mLanguage);
                        image.setSeason(mCurrentSeason);
                        image.generateFileNames(mContext);
                        seasonposters.add(image);


                    }
                    break;
                case BANNER_M_TYPE_SHOW_BANNER:
                    // just in case there is no real poster, save a single banner
                    if (fallbackBanner == null) {
                        image = new ScraperImage(Type.SHOW_POSTER, mNameSeed);
                        image.setLargeUrl(COVER_THUMB_START + mCurrentBannerPath);
                        image.setThumbUrl(COVER_START + mCurrentBannerPath);
                        image.setLanguage(mLanguage);
                        image.generateFileNames(mContext);
                        fallbackBanner = image;
                    }
                    break;
            }
        } else if (hierarchyLevel == 2) {
            if (ELEMENT_2BANNER_PATH.equals(localName)) {
                mCurrentBannerPath = getString();
            } else if (ELEMENT_2BANNER_TYPE.equals(localName)) {
                String bannerType = getString();
                if (ELEMENT_2BANNER_TYPE_BACKDROP.equals(bannerType))
                    mCurrentBannerMajorType = BANNER_M_TYPE_BACKDROP;
                else if (ELEMENT_2BANNER_TYPE_SEASON_POSTER_OR_BANNER.equals(bannerType))
                    mCurrentBannerMajorType = BANNER_M_TYPE_SEASON_POSTER_OR_BANNER;
                else if (ELEMENT_2BANNER_TYPE_SHOW_POSTER.equals(bannerType))
                    mCurrentBannerMajorType = BANNER_M_TYPE_SHOW_POSTER;
                else if (ELEMENT_2BANNER_TYPE_SHOW_BANNER.equals(bannerType))
                    mCurrentBannerMajorType = BANNER_M_TYPE_SHOW_BANNER;
            } else if (ELEMENT_2BANNER_TYPE2.equals(localName)) {
                String bannerType = getString();
                if (ELEMENT_2BANNER_TYPE2_SEASON_BANNER.equals(bannerType))
                    mCurrentBannerSubType = BANNER_S_TYPE_SEASON_BANNER;
                if (ELEMENT_2BANNER_TYPE2_SEASON_POSTER.equals(bannerType))
                    mCurrentBannerSubType = BANNER_S_TYPE_SEASON_POSTER;
            } else if (ELEMENT_2SEASON.equals(localName)) {
                mCurrentSeason = getInt(-1);
            }
        }
    }

    @Override
    protected void stopFile() {
        // add show & season posters. Show posters first so default is first.
        mResult.posters.addAll(showposters);
        mResult.posters.addAll(seasonposters);
        // if we found no real posters but a banner use that instead.
        if (mResult.posters.isEmpty() && fallbackBanner != null)
            mResult.posters.add(fallbackBanner);
    }

    public void setNameSeed(String seed) {
        mNameSeed = seed;
    }
}
