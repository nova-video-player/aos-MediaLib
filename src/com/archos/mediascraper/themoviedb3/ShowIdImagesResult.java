// Copyright 2021 Courville Software
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

import com.archos.mediascraper.ScrapeStatus;
import com.archos.mediascraper.ScraperImage;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class ShowIdImagesResult {
    public static final List<ScraperImage> EMPTY_LIST = Collections.<ScraperImage>emptyList();
    public List<ScraperImage> posters;
    public List<ScraperImage> backdrops;
    public List<ScraperImage> stills;
    public List<ScraperImage> networklogos;
    public ScrapeStatus status;
    public Throwable reason;
    public ShowIdImagesResult() {
        this.posters = new LinkedList<>();
        this.backdrops = new LinkedList<>();
        this.stills = new LinkedList<>();
        this.networklogos = new LinkedList<>();
    }
}
