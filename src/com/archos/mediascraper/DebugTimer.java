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

package com.archos.mediascraper;

/** Small debug timing utility */
public class DebugTimer {
    private final long startTime;
    private long stepTime;

    /** init start time */
    public DebugTimer() {
        long now = System.currentTimeMillis();
        startTime = now;
        stepTime = now;
    }

    /** time since last step() or init */
    public String step() {
        long now = System.currentTimeMillis();
        long step = now - stepTime;
        stepTime = now;
        return "[+" + step + "ms]";
    }

    /** time since init */
    public String total() {
        long now = System.currentTimeMillis();
        long step = now - startTime;
        return "[=" + step + "ms]";
    }
}
