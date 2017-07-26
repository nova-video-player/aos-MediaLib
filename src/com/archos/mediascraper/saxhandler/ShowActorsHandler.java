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

import org.xml.sax.Attributes;

import android.text.TextUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sax Handler for results from
 * http://www.thetvdb.com/api/api-key/series/$ID$/all/$LANGUAGE$.zip//actors.xml
 */
public class ShowActorsHandler extends BasicHandler {
    // private final static String TAG = "ShowAllDetailsHandler";
    // private final static boolean DBG = true;

    private Map<String, String> mResult = null;
    private String mCurrentName = null;
    private String mCurrentRole = null;

    private final static String ELEMENT_1ACTOR = "Actor";
    private final static String ELEMENT_2NAME = "Name";
    private final static String ELEMENT_2ROLE = "Role";

    public ShowActorsHandler() {
        // nothing
    }

    public Map<String, String> getResult() {
        Map<String, String> result = mResult;
        mResult = null;
        mCurrentName = null;
        mCurrentRole = null;
        return result;
    }

    @Override
    protected void startFile() {
        mResult = new LinkedHashMap<String, String>();
    }

    @Override
    protected boolean startItem(int hierarchyLevel, String uri, String localName, String qName,
            Attributes attributes) {
        if (hierarchyLevel == 1 && ELEMENT_1ACTOR.equals(localName)) {
            mCurrentName = null;
            mCurrentRole = null;
            return false;
        }
        if (hierarchyLevel == 2) {
            if (ELEMENT_2NAME.equals(localName)) {
                return true;
            } else if (ELEMENT_2ROLE.equals(localName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void endItem(int hierarchyLevel, String uri, String localName, String qName) {
        if (hierarchyLevel == 1 && ELEMENT_1ACTOR.equals(localName)) {
            if (!TextUtils.isEmpty(mCurrentName))
                mResult.put(mCurrentName, mCurrentRole);
        } else if (hierarchyLevel == 2) {
            if (ELEMENT_2NAME.equals(localName)) {
                mCurrentName = getString();
            } else if (ELEMENT_2ROLE.equals(localName)) {
                mCurrentRole = getString();
            }
        }
    }

    @Override
    protected void stopFile() {
        // nothing
    }
}
