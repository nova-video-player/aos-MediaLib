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

package com.archos.mediascraper.thetvdb;

import android.util.Log;

import com.uwetrottmann.thetvdb.entities.Actor;
import com.uwetrottmann.thetvdb.entities.ActorsResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ShowIdActorsParser {

    private static final String TAG = ShowIdActorsParser.class.getSimpleName();
    private static final boolean DBG = false;

    public static Map<String, String> getResult(ActorsResponse actorsResponse) {
        Map<String, String> actors = new LinkedHashMap<>();

        if (DBG)
            for (Actor actor : actorsResponse.data)
                Log.d(TAG, "getActors: " + actor.name + " (" + actor.role + ")");

        List<Actor> tempActors = new ArrayList<>(actorsResponse.data);
        Collections.sort(tempActors, new Comparator<Actor>() {
            @Override
            public int compare(Actor a1, Actor a2) {
                return Integer.compare(a1.sortOrder, a2.sortOrder);
            }
        });
        for (Actor actor: tempActors)
            actors.put(actor.name, actor.role);
        return actors;
    }
}
