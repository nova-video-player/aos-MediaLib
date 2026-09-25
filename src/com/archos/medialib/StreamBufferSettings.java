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

/** Native allocation limits, not performance presets. Values are in MiB. */
final class StreamBufferSettings {
    private static final long MIB = 1024L * 1024;

    private StreamBufferSettings() { }

    static int streamSize(int size) {
        // Legacy parsers also allocate a fixed 6 MiB overlap. Zero keeps their fallback.
        return size >= 0 && size * MIB + 6 * MIB <= Integer.MAX_VALUE ? size : 24;
    }

    static int frameSize(int size) {
        // CBE backing storage contains two copies of its logical capacity.
        return size > 0 && size * MIB * 2 <= Integer.MAX_VALUE ? size : 6;
    }
}
