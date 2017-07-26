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

import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import com.archos.filecorelibrary.MetaFile;
import com.archos.mediascraper.xml.BaseScraper2;

public class SearchResult implements Parcelable {
    private int mType;
    private String mTitle;
    private int mId;
    private String mLanguage;
    private BaseScraper2 mScraper;
    private Uri mFile;
    private Bundle mExtra;

    public SearchResult() {
    }

    public SearchResult(int type, String title, int id) {
        mType = type;
        mTitle = title;
        mId = id;
    }

    @Override
    public String toString() {
        return "SearchResult title: " + mTitle + " id: " + mId + " language: " + mLanguage;
    }

    public String getTitle() { return mTitle; }
    public int getType() { return mType; }
    public int getId() { return mId; }
    public String getLanguage() { return mLanguage; }
    public BaseScraper2 getScraper() { return mScraper; }
    public Uri getFile() { return mFile; }
    public Bundle getExtra() { return mExtra; }

    public void setId(int id) { mId = id; }
    public void setLanguage(String lang) { mLanguage = lang; }
    public void setTitle(String title) { mTitle = title; }
    public void setType(int type) { mType = type; }
    public void setScraper(BaseScraper2 scraper) { mScraper = scraper; }
    public void setFile(Uri file) { mFile = file; }
    public void setExtra(Bundle extra) { mExtra = extra; }

    public static final Parcelable.Creator<SearchResult> CREATOR = new Parcelable.Creator<SearchResult>() {
        public SearchResult createFromParcel(Parcel in) {
            return new SearchResult(in);
        }

        public SearchResult[] newArray(int size) {
            return new SearchResult[size];
        }
    };

    public SearchResult(Parcel in) {
        readFromParcel(in);
    }

    private void readFromParcel(Parcel in) {
        mType = in.readInt();
        mTitle = in.readString();
        mId = in.readInt();
        mLanguage = in.readString();
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mType);
        out.writeString(mTitle);
        out.writeInt(mId);
        out.writeString(mLanguage);
    }

    public int describeContents() {
        return 0;
    }
}
