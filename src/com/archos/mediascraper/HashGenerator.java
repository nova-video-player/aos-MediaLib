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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * generates a MD5 hash based String from given input String.<p>
 * For the lulz / increased filesystem compatibility:
 *  uses 'a'-'p' instead of 0-9,a-f for hex values
 */
public class HashGenerator {

    private static final MessageDigest ENCODER = setupMd5();
    private static MessageDigest setupMd5() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Cannot do MD5");
        }
    }

    /** creates hash String out of input string, NOT null safe */
    /* package */ static final synchronized String hash(String string) {
        // synchronized since MessageDigest is not thread safe.
        return encode(ENCODER.digest(string.getBytes()));
    }

    // 4bit to char map - could be anything that makes up a good filename
    private static final char[] ENCODE_CHARS = {
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n',
            'o', 'p'
    };

    /**
     * Encodes the 128 bit (16 bytes) MD5 digest into a 32 characters long
     * <CODE>String</CODE>.
     */
    private static String encode(byte[] binaryData) {
        if (binaryData.length != 16) {
            return null;
        }

        char[] buffer = new char[32];
        for (int i = 0; i < 16; i++) {
            int low = (binaryData[i] & 0x0f);
            int high = ((binaryData[i] & 0xf0) >> 4);
            buffer[i * 2] = ENCODE_CHARS[high];
            buffer[(i * 2) + 1] = ENCODE_CHARS[low];
        }

        return new String(buffer);
    }
}
