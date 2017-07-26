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

package com.archos.mediascraper.themoviedb3;

import android.util.JsonReader;
import android.util.JsonToken;

import com.archos.mediascraper.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

public abstract class JSONStreamParser<Result, Config> {

    public final Result readJsonFile(File f, Config config) throws IOException {
        FileInputStream read = new FileInputStream(f);
        try {
            return readJsonStream(read, config);
        } finally {
            IOUtils.closeSilently(read);
        }
    }

    public final Result readJsonStream(InputStream in, Config config) throws IOException {
        InputStreamReader read = new InputStreamReader(in, "UTF-8");
        try {
            return readJsonReader(read, config);
        } finally {
            IOUtils.closeSilently(read);
        }
    }

    public final Result readJsonReader(Reader in, Config config) throws IOException {
        JsonReader read = new JsonReader(in);
        try {
            return getResult(read, config);
        } finally {
            IOUtils.closeSilently(read);
        }
    }

    protected abstract Result getResult(JsonReader reader, Config config) throws IOException;

    protected static boolean hasNextSkipNull(JsonReader reader) throws IOException {
        while (reader.hasNext()) {
            if (reader.peek() == JsonToken.NULL) {
                reader.skipValue();
            } else {
                return true;
            }
        }
        return false;
    }

    protected static final String getNextNotNullName(JsonReader reader) throws IOException {
        while (reader.hasNext()) {
            String name = reader.nextName();
            JsonToken jsonToken = reader.peek();
            if (jsonToken != JsonToken.NULL) {
                return name;
            }
            reader.skipValue();
        }
        return null;
    }
}
