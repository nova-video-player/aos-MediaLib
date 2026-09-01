// Copyright 2026 Courville Software
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

package com.archos.mediacenter.utils.introdb;

import android.content.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

// Multi-provider facade. Queries both introdb providers (theintrodb.org and
// introdb.app) and fuses their answers into a single normalized IntroSegments,
// so callers (the player) never deal with provider specifics or the merge policy.
//
// Fusion policy: introdb.app is the preferred source for the overlapping types
// (intro/recap) because it has the largest collection; theintrodb.org is used for
// those types only when introdb.app returned nothing, and it additionally
// contributes credits/preview (which introdb.app does not provide). introdb.app's
// outro is kept under its own type to preserve provenance.
public class IntroDbManager {

    private static final Logger log = LoggerFactory.getLogger(IntroDbManager.class);

    public static final String SOURCE_APP = "introdb.app";
    public static final String SOURCE_ORG = "theintrodb.org";

    private IntroDbManager() {
    }

    // Initialize both provider helpers (shared OkHttp disk cache). Call once.
    public static void init(Context context) {
        IntroDbApiHelper.init(context);
        IntroDbAppApiHelper.init(context);
    }

    // Blocking fetch from both providers, fused into one result.
    // MUST be called from a background thread. Returns null when neither provider
    // returned any usable segment.
    public static IntroSegments fetch(IntroDbQueryParams query) {
        if (query == null) return null;
        if (log.isDebugEnabled()) log.debug("fetch: {}", query);

        IntroDbAppResult app = fetchApp(query);
        IntroDbResult org = fetchOrg(query);

        IntroSegments out = new IntroSegments();

        // introdb.app (preferred for intro/recap, sole source for outro)
        boolean appIntro = false, appRecap = false;
        if (app != null) {
            appIntro = addAppSegment(out, IntroSegments.Type.INTRO, app.getIntro());
            appRecap = addAppSegment(out, IntroSegments.Type.RECAP, app.getRecap());
            addAppSegment(out, IntroSegments.Type.OUTRO, app.getOutro());
        }

        // theintrodb.org (fallback for intro/recap, sole source for credits/preview)
        if (org != null) {
            if (!appIntro) addOrgSegments(out, IntroSegments.Type.INTRO, org.getIntro());
            if (!appRecap) addOrgSegments(out, IntroSegments.Type.RECAP, org.getRecap());
            addOrgSegments(out, IntroSegments.Type.CREDITS, org.getCredits());
            addOrgSegments(out, IntroSegments.Type.PREVIEW, org.getPreview());
        }

        if (!out.hasAny()) return null;
        if (log.isDebugEnabled()) log.debug("fetch: fused {}", out);
        return out;
    }

    private static IntroDbAppResult fetchApp(IntroDbQueryParams q) {
        if (!q.isShow()) return null;
        if (q.getImdbId() == null || q.getImdbId().isEmpty()) {
            if (log.isDebugEnabled()) log.debug("fetchApp: no imdb id, skipping introdb.app");
            return null;
        }
        if (q.getSeason() == null || q.getSeason() <= 0 || q.getEpisode() == null || q.getEpisode() <= 0) {
            if (log.isDebugEnabled()) log.debug("fetchApp: missing season/episode, skipping introdb.app");
            return null;
        }
        IntroDbAppQueryParams p = new IntroDbAppQueryParams();
        p.setImdbId(q.getImdbId());
        p.setSeason(q.getSeason());
        p.setEpisode(q.getEpisode());
        try {
            return IntroDbAppApiHelper.getSegments(p);
        } catch (IOException e) {
            log.warn("fetchApp: network error", e);
            return null;
        }
    }

    private static IntroDbResult fetchOrg(IntroDbQueryParams q) {
        if (!q.hasIdentifier()) {
            if (log.isDebugEnabled()) log.debug("fetchOrg: no identifier, skipping theintrodb.org");
            return null;
        }
        try {
            return IntroDbApiHelper.getMedia(q);
        } catch (IOException e) {
            log.warn("fetchOrg: network error", e);
            return null;
        }
    }

    private static boolean addAppSegment(IntroSegments out, IntroSegments.Type type, IntroDbAppResult.Segment s) {
        if (s == null) return false;
        out.add(type, new IntroSegments.Segment(s.startMs, s.endMs, s.confidence, s.submissionCount, SOURCE_APP));
        return true;
    }

    private static void addOrgSegments(IntroSegments out, IntroSegments.Type type, List<IntroDbResult.Segment> list) {
        if (list == null) return;
        for (IntroDbResult.Segment s : list)
            out.add(type, new IntroSegments.Segment(s.startMs, s.endMs, 0, 0, SOURCE_ORG));
    }
}
