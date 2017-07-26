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
import android.net.Uri;

import com.archos.filecorelibrary.MetaFile;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.mediascraper.BaseTags;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

public class NfoRootHandler extends BasicHandler {

    private XMLReader mReader;
    private NfoMovieHandler mMovieHandler;
    private NfoEpisodeHandler mEpisodeHandler;

    public NfoRootHandler(XMLReader reader, NfoMovieHandler movieHandler,
            NfoEpisodeHandler episodeHandler) {
        mReader = reader;
        mMovieHandler = movieHandler;
        mEpisodeHandler = episodeHandler;
    }

    @Override
    protected void startFile() {
        // don't care
    }

    @Override
    protected boolean startItem(int hierarchyLevel, String uri, String localName, String qName,
            Attributes attributes) throws SAXException {

        if (hierarchyLevel == 0) {
            if ("movie".equals(localName)) {
                mMovieHandler.startSubParse(mReader, this, uri, localName, qName, attributes);
            }
            else if ("episodedetails".equals(localName)) {
                mEpisodeHandler.startSubParse(mReader, this, uri, localName, qName, attributes);
            }
        }
        return false;
    }

    @Override
    protected void endItem(int hierarchyLevel, String uri, String localName, String qName) {
        // don't care
    }

    @Override
    protected void stopFile() {
        // don't care
    }

    /** returns the first found result, order: movie, episode, show */
    public BaseTags getResult(Context context, Uri movieFile) {
        BaseTags result;
        result = mMovieHandler.getResult(context, movieFile);
        if (result != null) return result;
        result = mEpisodeHandler.getResult(context, movieFile);
        return result;
    }

    public void clear() {
        mMovieHandler.clear();
        mEpisodeHandler.clear();
    }
}
