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

package com.archos.mediascraper.settings;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

/*
 * Class holding the definition of a particular setting for a scraper.
 * Holds its type, default value, label, id, etc.
 */
public class ScraperSetting {
    public static final int TYPE_BOOL = 1;
    public static final String STR_BOOL = "bool";
    public static final int TYPE_LABELENUM = 2;
    public static final String STR_LABELENUM = "labelenum";
    public static final int TYPE_SEP = 3;
    public static final String STR_SEP = "sep";
    public static final int TYPE_TEXT = 4;
    public static final String STR_TEXT = "text";
    public static final int TYPE_INTEGER = 5;
    public static final String STR_INTEGER = "integer";
    public static final int TYPE_FILEENUM = 6;
    public static final String STR_FILEENUM = "filenum";

    public static final Hashtable<String, Integer> TYPE_MAP = new Hashtable<String, Integer>();
    static {
        TYPE_MAP.put(STR_BOOL, TYPE_BOOL);
        TYPE_MAP.put(STR_LABELENUM, TYPE_LABELENUM);
        TYPE_MAP.put(STR_SEP, TYPE_SEP);
        TYPE_MAP.put(STR_TEXT, TYPE_TEXT);
        TYPE_MAP.put(STR_INTEGER, TYPE_INTEGER);
        TYPE_MAP.put(STR_FILEENUM, TYPE_FILEENUM);
    }

    private String mId;
    private String mLabel;
    private int mType;
    private Object mDefault;
    private String mEnable;
    private List<String> mValues;

    public ScraperSetting(String id, String type) {
        super();
        mValues = new ArrayList<String>();
        mId = id;
        mType = TYPE_MAP.get(type);
    }

    public String getId() {
        return mId;
    }

    public String getLabel() {
        return mLabel;
    }

    public Boolean getBooleanDefault() {
        return (Boolean)mDefault;
    }

    public Integer getIntDefault() {
        return (Integer)mDefault;
    }

    public String getStringDefault() {
        return (String)mDefault;
    }

    public int getType() {
        return mType;
    }

    public List<String> getValues() {
        if(mType == TYPE_LABELENUM || mType == TYPE_FILEENUM)
            return mValues;
        return null;
    }

    public void setDefault(String def) {
        switch(mType) {
            case TYPE_BOOL:
                if(def.equals("true")) {
                    mDefault = Boolean.TRUE;
                } else {
                    mDefault = Boolean.FALSE;
                }
                break;
            case TYPE_LABELENUM: case TYPE_TEXT: case TYPE_FILEENUM:
                mDefault = def;
                break;
            case TYPE_INTEGER:
                mDefault = Integer.valueOf(def);
                break;
        }
    }

    public void setLabel(String label) {
        mLabel = label;
    }

    public void setEnable(String enable) {
        mEnable = enable;
    }

    public void setValues(String values) {
        if(values == null || TextUtils.isEmpty(values))
            return;

        String[] splitted = values.split("\\|");
        for(String elem: splitted) {
            switch(mType) {
                case TYPE_LABELENUM:
                    mValues.add(elem);
                    break;
                case TYPE_FILEENUM:
                    mValues.add(elem);
                    break;
            }
        }
    }

    @Override
    public String toString() {
        return "ScraperSetting id: " + mId + " label: " + mLabel + " type: " +
            mType + " values: " + mValues + " default: " + mDefault;
    }
}
