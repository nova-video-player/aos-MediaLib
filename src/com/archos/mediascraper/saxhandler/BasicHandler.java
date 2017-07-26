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
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import android.text.TextUtils;
import android.util.Log;

/**
 * Base Class for Sax Handler that simplifies parsing a little.
 */
public abstract class BasicHandler extends DefaultHandler {
    private final static String TAG = "BasicHandler";
    private final static boolean DBG = false;

    private boolean mProcessContent = false;
    private int mElementLevel = 0;

    private long mStartTime = 1;
    private long mStopTime = 0;
    private final StringBuilder mStringBuilder;

    public BasicHandler() {
        // initialize stuff we can keep around
        mStringBuilder = new StringBuilder();
    }

    @SuppressWarnings("unused")
    // not throwing SAXException
    @Override
    public final void characters(char[] ch, int start, int length) throws SAXException {
        // add string content to buffer if the current tag is of interest
        if (mProcessContent) {
            mStringBuilder.append(ch, start, length);
        }
    }

    @SuppressWarnings("unused")
    // not throwing SAXException
    @Override
    public final void startDocument() throws SAXException {
        // new document, initialize stuff relevant for that
        if (DBG) mStartTime = System.currentTimeMillis();
        mElementLevel = 0;
        startFile();
    }

    /**
     * Called at the start of parsing a File. Initialize your result here.
     */
    protected abstract void startFile();

    /**
     * Called when the parser encounters the start of an element. Evaluate
     * Attributes here and return true if you are interested in the Text of that
     * element
     * @throws SAXException
     */
    protected abstract boolean startItem(int hierarchyLevel, String uri, String localName,
            String qName, Attributes attributes) throws SAXException;

    /**
     * Called when the parser encounters the end of an element. Use
     * {@link #getString()} or the int / float version to read the text that was
     * contained in this element. Note that this text will be <code>""</code> if
     * you did not return true in
     * {@link #startItem(int, String, String, String, Attributes)}.
     */
    protected abstract void endItem(int hierarchyLevel, String uri, String localName, String qName);

    /**
     * Called at the end of a file. Can be used to perform some cleanup
     * operations.
     */
    protected abstract void stopFile();

    /** called when parsing is back to the same hierarchy level it started on */
    protected void onHierarchyEnd(String uri, String localName, String qName) throws SAXException {
        // empty per default
    }

    @Override
    public final void startElement(String uri, String localName, String qName, Attributes attributes)
            throws SAXException {
        mStringBuilder.setLength(0);
        mProcessContent = startItem(mElementLevel, uri, localName, qName, attributes);
        mElementLevel++; // increase the hierarchy level
    }

    @SuppressWarnings("unused")
    // not throwing SAXException
    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        mElementLevel--;
        endItem(mElementLevel, uri, localName, qName);
        if (mElementLevel == 0)
            onHierarchyEnd(uri, localName, qName);
        mProcessContent = false;
    }

    @SuppressWarnings("unused")
    // not throwing SAXException
    @Override
    public final void endDocument() throws SAXException {
        stopFile();
        if (DBG) mStopTime = System.currentTimeMillis();
        if (DBG) Log.d(TAG, "Parsing took: " + (mStopTime - mStartTime));
    }

    /**
     * @return the String that was contained in the last processed element. Will
     *         return <code>""</code> if
     *         {@link #startItem(int, String, String, String, Attributes)}
     *         returned false
     */
    protected String getString() {
        String ret = mStringBuilder.toString().trim();
        mStringBuilder.setLength(0);
        return ret;
    }

    /**
     * @return similar to {@link #getString()} but returns an int or 0 if the
     *         element did not contain anything that can be parsed into an int.
     */
    protected int getInt() {
        return getInt(0);
    }

    /**
     * @return similar to {@link #getString()} but returns an int or errorValue if the
     *         element did not contain anything that can be parsed into an int.
     */
    protected int getInt(int errorValue) {
        String string = getString();
        if (!TextUtils.isEmpty(string)) {
            try {
                return Integer.parseInt(string);
            } catch (NumberFormatException e) {
                // continues below
            }
        }
        Log.w(TAG, "Error parsing \"" + string + "\" - not a number");
        return errorValue;
    }

    /**
     * @return similar to {@link #getString()} but returns a long or 0 if the
     *         element did not contain anything that can be parsed into a long.
     */
    protected long getLong() {
        return getLong(0L);
    }

    /**
     * @return similar to {@link #getString()} but returns a long or errorValue if the
     *         element did not contain anything that can be parsed into a long.
     */
    protected long getLong(long errorValue) {
        String string = getString();
        if (TextUtils.isEmpty(string))
            return errorValue;
        long ret = errorValue;
        try {
            ret = Long.parseLong(string);
        } catch (NumberFormatException e) {
            // ret stays at errorValue
        }
        return ret;
    }

    /**
     * @return similar to {@link #getString()} but returns a float or 0f if the
     *         element did not contain anything that can be parsed into a float.
     */
    protected float getFloat() {
        String string = getString();
        if (TextUtils.isEmpty(string))
            return 0f;
        float ret = 0f;
        try {
            ret = Float.parseFloat(string);
        } catch (NumberFormatException e) {
            // ret stays at 0
        }
        return ret;
    }
}
