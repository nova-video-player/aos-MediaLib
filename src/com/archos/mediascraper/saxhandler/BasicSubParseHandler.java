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
import org.xml.sax.XMLReader;

public abstract class BasicSubParseHandler extends BasicHandler {

    private XMLReader mReader;
    private BasicHandler mRootHandler;

    public void startSubParse(XMLReader reader, BasicHandler rootHandler, String uri,
            String localName, String qName, Attributes attributes) throws SAXException {
        mReader = reader;
        mRootHandler = rootHandler;
        mReader.setContentHandler(this);
        this.startDocument();
        this.startElement(uri, localName, qName, attributes);
    }

    @Override
    protected void onHierarchyEnd(String uri, String localName, String qName) throws SAXException {
        if (mRootHandler != null && mReader != null) {
            mReader.setContentHandler(mRootHandler);
            mRootHandler.endElement(uri, localName, qName);
            mReader = null;
            mRootHandler = null;
        }
    }
}
