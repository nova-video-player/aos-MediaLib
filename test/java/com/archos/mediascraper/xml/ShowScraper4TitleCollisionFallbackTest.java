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

package com.archos.mediascraper.xml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import androidx.test.core.app.ApplicationProvider;

import com.archos.mediascraper.EpisodeTags;
import com.archos.mediascraper.ScrapeDetailResult;
import com.archos.mediascraper.ScrapeSearchResult;
import com.archos.mediascraper.ScrapeStatus;
import com.archos.mediascraper.SearchResult;
import com.archos.mediascraper.preprocess.SearchInfo;
import com.archos.mediascraper.preprocess.TvShowSearchInfo;
import com.archos.medialib.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * Focused unit test for {@link ShowScraper4#searchWithTitleCollisionFallback}: verifies the
 * request-bound guard that restricts the tied-candidate cascade to genuine (distance-0) title
 * collisions, without hitting the network. All TMDb-facing calls are stubbed out via overriding
 * {@link ShowScraper4#getMatches2} (candidate list) and {@link ShowScraper4#getDetailsInternal}
 * (per-candidate detail fetch, whose invocation count is what's under test).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ShowScraper4TitleCollisionFallbackTest {

    private Context context;

    @Before
    public void setUp() {
        context = spy(ApplicationProvider.getApplicationContext());
        doReturn("051012651ba326cf5b1e2f482342eaa2").when(context).getString(R.string.tmdb_api_key);
    }

    private static SearchInfo someTvSearchInfo() {
        return new TvShowSearchInfo(Uri.parse("file:///show/S01E01.mkv"), "Show", 1, 1, null, null);
    }

    private static SearchResult candidate(int id, int distance) {
        SearchResult result = new SearchResult(SearchResult.tvshow, "Show " + id, id);
        result.setLevenshteinDistance(distance);
        // BaseScraper2.getDetails() short-circuits to ERROR (without calling
        // getDetailsInternal()) when the file is null, so a non-null file is required here to
        // actually exercise the per-candidate detail-fetch path under test.
        result.setFile(Uri.parse("file:///show/S01E01.mkv"));
        return result;
    }

    /** Never resolves to a genuine (titled) episode: forces the fallback/cascade branch. */
    private static ScrapeDetailResult notFoundResult() {
        EpisodeTags tag = new EpisodeTags();
        return new ScrapeDetailResult(tag, false, null, ScrapeStatus.NOT_FOUND, null);
    }

    /** Resolves to a genuine episode with a real title. */
    private static ScrapeDetailResult genuineResult() {
        EpisodeTags tag = new EpisodeTags();
        tag.setTitle("Pilot");
        return new ScrapeDetailResult(tag, false, null, ScrapeStatus.OKAY, null);
    }

    /** Test double that stubs the network-facing calls and counts detail-fetch invocations. */
    private static class CountingShowScraper extends ShowScraper4 {
        final List<SearchResult> candidates;
        final List<Integer> genuineMatchIndexes;
        int detailFetchCount = 0;

        CountingShowScraper(Context context, List<SearchResult> candidates, List<Integer> genuineMatchIndexes) {
            super(context);
            this.candidates = candidates;
            this.genuineMatchIndexes = genuineMatchIndexes;
            for (SearchResult candidate : candidates) candidate.setScraper(this);
        }

        @Override
        public ScrapeSearchResult getMatches2(SearchInfo info, int maxItems) {
            return new ScrapeSearchResult(candidates, false, ScrapeStatus.OKAY, null);
        }

        @Override
        protected ScrapeDetailResult getDetailsInternal(SearchResult result, Bundle options) {
            int index = candidates.indexOf(result);
            detailFetchCount++;
            return genuineMatchIndexes.contains(index) ? genuineResult() : notFoundResult();
        }
    }

    @Test
    public void fuzzyTopMatch_doesNotCascade_evenWithTiedCandidates() {
        // top candidate is only a fuzzy match (distance 3); two more candidates are tied at the
        // same non-zero distance, but they are not genuine title collisions with the top pick.
        List<SearchResult> candidates = new ArrayList<>();
        candidates.add(candidate(1, 3));
        candidates.add(candidate(2, 3));
        candidates.add(candidate(3, 3));
        CountingShowScraper scraper = new CountingShowScraper(context, candidates, List.of());

        ScrapeDetailResult result = scraper.searchWithTitleCollisionFallback(someTvSearchInfo());

        assertNotNull(result);
        assertEquals("only the top candidate should be tried when the top distance is non-zero",
                1, scraper.detailFetchCount);
    }

    @Test
    public void exactTitleCollision_cascadesThroughTiedCandidates_untilGenuineMatch() {
        // top three candidates are exact title matches (distance 0, a genuine collision, e.g. a
        // classic show and its reboot); only the third one resolves to a real episode.
        List<SearchResult> candidates = new ArrayList<>();
        candidates.add(candidate(1, 0));
        candidates.add(candidate(2, 0));
        candidates.add(candidate(3, 0));
        candidates.add(candidate(4, 5)); // not tied: must never be tried
        CountingShowScraper scraper = new CountingShowScraper(context, candidates, List.of(2));

        ScrapeDetailResult result = scraper.searchWithTitleCollisionFallback(someTvSearchInfo());

        assertNotNull(result);
        assertEquals("cascade must stop as soon as a genuine match is found",
                3, scraper.detailFetchCount);
        assertEquals("Pilot", ((EpisodeTags) result.tag).getTitle());
    }

    @Test
    public void exactTitleCollision_noneGenuine_triesAllTiedCandidatesOnly() {
        List<SearchResult> candidates = new ArrayList<>();
        candidates.add(candidate(1, 0));
        candidates.add(candidate(2, 0));
        candidates.add(candidate(3, 5)); // not tied: must never be tried
        CountingShowScraper scraper = new CountingShowScraper(context, candidates, List.of());

        scraper.searchWithTitleCollisionFallback(someTvSearchInfo());

        assertEquals("cascade must be bounded to the distance-0 tied candidates",
                2, scraper.detailFetchCount);
    }
}
