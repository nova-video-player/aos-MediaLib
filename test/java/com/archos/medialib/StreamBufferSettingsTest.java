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

package com.archos.medialib;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class StreamBufferSettingsTest {
    @Test public void preservesAdvancedValuesAndStreamZeroFallback() {
        assertEquals(0, StreamBufferSettings.streamSize(0));
        assertEquals(512, StreamBufferSettings.streamSize(512));
        assertEquals(2041, StreamBufferSettings.streamSize(2041));
        assertEquals(1023, StreamBufferSettings.frameSize(1023));
    }
    @Test public void invalidAllocationsUseDefaultsWithoutWrapping() {
        assertEquals(24, StreamBufferSettings.streamSize(-1));
        assertEquals(24, StreamBufferSettings.streamSize(2042));
        assertEquals(24, StreamBufferSettings.streamSize(Integer.MAX_VALUE));
        assertEquals(6, StreamBufferSettings.frameSize(0));
        assertEquals(6, StreamBufferSettings.frameSize(-1));
        assertEquals(6, StreamBufferSettings.frameSize(1024));
        assertEquals(6, StreamBufferSettings.frameSize(Integer.MAX_VALUE));
    }
}
