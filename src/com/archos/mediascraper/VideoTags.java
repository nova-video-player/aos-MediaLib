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

import android.os.Parcel;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

public abstract class VideoTags extends BaseTags {

    protected final List<String> mStudios = new ArrayList<String>();
    protected String mStudiosFormatted;

    protected List<String> mGenres = new ArrayList<String>();
    protected String mGenresFormatted;
    /** language-independent TMDB genre ids, delimiter wrapped: ",16,10751," */
    protected String mGenreIds;

    public VideoTags() {
        super();
    }

    public VideoTags(Parcel in) {
        super(in);
        readFromParcel(in);
    }

    public boolean genreExists(String name) {
        return mGenres.contains(name);
    }

    public List<String> getGenres() { return mGenres; }

    public String getGenresFormatted() {
        ensureFormattedGenres();
        return mGenresFormatted;
    }

    /** Stored language-independent TMDB genre ids (delimiter wrapped), or null. */
    public String getGenreIds() { return mGenreIds; }

    public void setGenreIds(String genreIds) { mGenreIds = genreIds; }

    /**
     * Localized genre display string derived from the stored genre ids for the current app locale,
     * falling back to the stored (scrape-time localized) string when no ids are available.
     */
    public String getGenresFormatted(android.content.Context context) {
        String derived = GenreUtils.formatGenreNames(context, mGenreIds, isShowTags());
        return derived != null ? derived : getGenresFormatted();
    }

    /** Whether this tag describes a show (true) or a movie (false). */
    protected abstract boolean isShowTags();

    public List<String> getStudios() { return mStudios; }

    public String getStudiosFormatted() {
        ensureFormattedStudios();
        return mStudiosFormatted;
    }

    /** does nothing if mGenresFormatted is already set, otherwise builds from mGenres */
    private void ensureFormattedGenres() {
        if (mGenresFormatted == null && mGenres != null && !mGenres.isEmpty()) {
            mGenresFormatted = TextUtils.join(", ", mGenres);
        }
    }

    /** does nothing if mStudiosFormatted is already set, otherwise builds from mStudios */
    private void ensureFormattedStudios() {
        if (mStudiosFormatted == null && mStudios != null && !mStudios.isEmpty()) {
            mStudiosFormatted = TextUtils.join(", ", mStudios);
        }
    }


    /**
     * all strings are trimmed, does not put empty values, does not replace entries.
     * optional splitCharacters to specify how to split the string
     **/
    public void addGenreIfAbsent(String genre, char... splitCharacters) {
        addIfAbsentSplitNTrim(genre, mGenres, splitCharacters);
    }

    public void setGenres(List<String> genres) { mGenres = genres; }

    public void setGenresFormatted(String genres) { mGenresFormatted = genres; }

    /**
     * all strings are trimmed, does not put empty values, does not replace entries.
     * optional splitCharacters to specify how to split the string
     **/
    public void addStudioIfAbsent(String studio, char... splitCharacters) {
        addIfAbsentSplitNTrim(studio, mStudios, splitCharacters);
    }

    public void setStudiosFormatted(String studios) { mStudiosFormatted = studios; }
    public boolean studioExists(String name) {
        return mStudios.contains(name);
    }

    @Override
    public String toString() {
        return super.toString() + " / GENRES=" + mGenres + " / STUDIOS=" + mStudios;
    }

    private void readFromParcel(Parcel in) {
        in.readStringList(mStudios);
        in.readStringList(mGenres);
        mGenreIds = in.readString();
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        super.writeToParcel(out, flags);
        out.writeStringList(mStudios);
        out.writeStringList(mGenres);
        out.writeString(mGenreIds);
    }

    public void addAllGenres(List<String> genres){
        mGenres.addAll(genres);
    }
}
