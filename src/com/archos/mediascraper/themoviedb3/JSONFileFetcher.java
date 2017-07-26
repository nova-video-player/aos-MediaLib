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

import com.archos.mediascraper.FileFetcher;
import com.archos.mediascraper.HttpCache;
import com.archos.mediascraper.ScrapeStatus;
import com.archos.mediascraper.FileFetcher.FileFetchResult;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class JSONFileFetcher extends FileFetcher {
    private static final Map<String, String> JSON_ACCEPT = new HashMap<String, String>();
    static {
        JSON_ACCEPT.put("Accept", "application/json");
    }

    private final HttpCache mCache;

    public JSONFileFetcher(HttpCache cache) {
        mCache = cache;
    }

    @Override
    public FileFetchResult getFile(String url) {
        FileFetchResult result = new FileFetchResult();
        File f = mCache.getFile(url, true, JSON_ACCEPT);
        result.file = f;
        result.status = f != null ? ScrapeStatus.OKAY : ScrapeStatus.ERROR_NETWORK;
        return result;
    }

}
