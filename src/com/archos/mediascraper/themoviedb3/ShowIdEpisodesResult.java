// Copyright 2020 Courville Software
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

import com.archos.mediascraper.EpisodeTags;
import com.archos.mediascraper.ScrapeStatus;

import java.util.Collections;
import java.util.Map;

public class ShowIdEpisodesResult {
    public static final Map<String, EpisodeTags> EMPTY_MAP = Collections.<String, EpisodeTags>emptyMap();
    public Map<String, EpisodeTags> episodes;
    public ScrapeStatus status;
    public Throwable reason;
    public ShowIdEpisodesResult() {
        this.episodes = EMPTY_MAP;
    }
}
