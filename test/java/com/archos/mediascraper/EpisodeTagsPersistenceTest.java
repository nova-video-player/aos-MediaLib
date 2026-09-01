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

package com.archos.mediascraper;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.content.ContentResolver;
import android.content.Context;

import org.junit.Test;

public class EpisodeTagsPersistenceTest {

    @Test
    public void parentShowFailurePreventsEpisodeWrites() {
        Context context = mock(Context.class);
        ContentResolver resolver = mock(ContentResolver.class);
        when(context.getContentResolver()).thenReturn(resolver);
        ShowTags show = new ShowTags() {
            @Override
            public long save(Context ignored, long videoId) {
                return -1;
            }
        };
        show.setTitle("Failed show");
        EpisodeTags episode = new EpisodeTags(show, 1, 1);

        assertEquals(-1, episode.save(context, 42));
        verifyNoInteractions(resolver);
    }
}
