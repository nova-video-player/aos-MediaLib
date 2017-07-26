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

import com.archos.mediascraper.ScrapeStatus;
import com.archos.mediascraper.SearchResult;

import java.util.Collections;
import java.util.List;

public class SearchMovieTrailerResult {
    public static class TrailerResult{

        private String service;
        private String language;
        private int id;
        private String key;
        private String name;
        private String type;

        public TrailerResult(){

        }
        public void setService(String service) {
            this.service = service;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public void setId(int id) {
            this.id = id;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getService() {
            return service;
        }

        public String getKey() {
            return key;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getLanguage() {
            return language;
        }

        public String getName() {
            return name;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }
    }
    public static final List<TrailerResult> EMPTY_LIST = Collections.<TrailerResult>emptyList();
    public List<TrailerResult> result;
    public ScrapeStatus status;
    public Throwable reason;
}
