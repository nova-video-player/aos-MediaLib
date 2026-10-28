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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class IntroDbApiHelperTest {

    @Test
    public void creditsAtStartOfMediaAreNotSkips() throws Exception {
        IntroDbResult result = IntroDbApiHelper.parseMedia(new JSONObject("{\"credits\":["
                + "{\"start_ms\":0,\"end_ms\":null},"
                + "{\"start_ms\":0,\"end_ms\":600000}]}"));

        assertTrue(result.getCredits().isEmpty());
    }

    @Test
    public void validOpenEndedCreditsAreKept() throws Exception {
        IntroDbResult result = IntroDbApiHelper.parseMedia(new JSONObject("{\"credits\":["
                + "{\"start_ms\":5801777,\"end_ms\":6371111},"
                + "{\"start_ms\":6408000,\"end_ms\":null}]}"));

        assertEquals(2, result.getCredits().size());
        assertEquals(Long.valueOf(6408000L), result.getCredits().get(1).startMs);
        assertNull(result.getCredits().get(1).endMs);
    }

    @Test
    public void introMayStartAtBeginningButNeedsAnEnd() throws Exception {
        IntroDbResult result = IntroDbApiHelper.parseMedia(new JSONObject("{\"intro\":["
                + "{\"start_ms\":null,\"end_ms\":24716},"
                + "{\"start_ms\":0,\"end_ms\":null}]}"));

        assertEquals(1, result.getIntro().size());
        assertNull(result.getIntro().get(0).startMs);
        assertEquals(Long.valueOf(24716L), result.getIntro().get(0).endMs);
    }

    @Test
    public void invalidRangesAreDropped() throws Exception {
        IntroDbResult result = IntroDbApiHelper.parseMedia(new JSONObject("{\"credits\":["
                + "{\"start_ms\":1000,\"end_ms\":1000},"
                + "{\"start_ms\":2000,\"end_ms\":1000},"
                + "{\"start_ms\":-1,\"end_ms\":600000}]}"));

        assertTrue(result.getCredits().isEmpty());
    }

    @Test
    public void noSegmentMarkersAreDroppedForAllTypes() throws Exception {
        IntroDbResult result = IntroDbApiHelper.parseMedia(new JSONObject("{"
                + "\"intro\":[{\"start_ms\":null,\"end_ms\":0}],"
                + "\"recap\":[{\"start_ms\":0,\"end_ms\":0}],"
                + "\"credits\":[{\"start_ms\":null,\"end_ms\":null}],"
                + "\"preview\":[{\"start_ms\":0,\"end_ms\":0}]}"));

        assertFalse(result.hasAnySegment());
    }
}
