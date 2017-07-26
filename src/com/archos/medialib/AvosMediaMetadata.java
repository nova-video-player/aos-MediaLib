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

package com.archos.medialib;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import android.os.Parcel;
import android.util.Log;

public class AvosMediaMetadata extends MediaMetadata {
    private static final int AVOS_MSG_TYPE_INT = 0;
    private static final int AVOS_MSG_TYPE_INT64 = 1;
    private static final int AVOS_MSG_TYPE_BOOL = 2;
    private static final int AVOS_MSG_TYPE_STR = 3;
    private static final int AVOS_MSG_TYPE_BYTE = 4;
    private static final int AVOS_MSG_TYPE_BITMAP = 5;
    private static final int AVOS_MSG_TYPE_TEXT_SUBTITLE = 6;
    private static final int AVOS_MSG_TYPE_BITMAP_SUBTITLE = 7;

    /*
     * from public/avos_common.h

     * typedef struct avos_msg {
     *   uint32_t id;
     *   uint32_t type;
     *   uint32_t size;
     *   uint8_t data[];
     * } avos_msg_t;
     */
    public boolean parse(byte[] bytes) {
        mParcel = Parcel.obtain();

        mKeyToPosMap.clear();
        mBegin = mParcel.dataPosition();
        // Placeholder for the length of the metadata
        mParcel.writeInt(-1);
        // Write the header. The java layer will look for the marker.
        mParcel.writeInt(kMetaMarker);

        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        byteBuffer.order(ByteOrder.nativeOrder());
        while (byteBuffer.hasRemaining()) {
            int id = byteBuffer.getInt();
            int type = byteBuffer.getInt();
            int size = byteBuffer.getInt();
            boolean ok = false;

            switch (type) {
            case AVOS_MSG_TYPE_INT:
                ok = appendInt(id, byteBuffer.getInt());
                break;
            case AVOS_MSG_TYPE_INT64:
                ok = appendLong(id, byteBuffer.getLong());
                break;
            case AVOS_MSG_TYPE_BOOL:
                ok = appendBool(id, byteBuffer.getInt() == 0 ? false : true);
                break;
            case AVOS_MSG_TYPE_STR:
                byte[] data = new byte[size - 1]; // no '\n'
                byteBuffer.get(data);
                ok = appendString(id, data);
                byteBuffer.get();
                break;
            }
            if (!ok) {
                Log.d(TAG, "parse error");
                mParcel.recycle();
                mKeyToPosMap.clear();
                return false;
            }
        }

        int end = mParcel.dataPosition();
        mParcel.setDataPosition(mBegin);
        mParcel.writeInt(end - mBegin);

        mParcel.setDataPosition(mBegin);

        return true;
    }
    // Check the key (i.e metadata id) is valid if it is a system one.
    // Loop over all the exiting ones in the Parcel to check for duplicate
    // (not allowed).
    private boolean checkKey(int key) {
        // Store the record offset which points to the type
        // field so we can later on read/unmarshall the record
        // payload.
        if (mKeyToPosMap.containsKey(key)) {
            Log.e(TAG, "Duplicate metadata ID found");
            return false;
        }

        final int curr = mParcel.dataPosition();
        // should point to the type
        mKeyToPosMap.put(key, curr + 2 * kInt32Size);
        return true;
    }

    private boolean appendBool(int key, boolean val) {
        if (!checkKey(key))
            return false;

        // 4 int32s: size, key, type, value.
        mParcel.writeInt(4 * kInt32Size);
        mParcel.writeInt(key);
        mParcel.writeInt(BOOLEAN_VAL);
        mParcel.writeInt(val ? 1 : 0);

        return true;
    }

    private boolean appendInt(int key, int val) {
        if (!checkKey(key))
            return false;

        // 4 int32s: size, key, type, value.
        mParcel.writeInt(4 * kInt32Size);
        mParcel.writeInt(key);
        mParcel.writeInt(INTEGER_VAL);
        mParcel.writeInt(val);

        return true;
    }

    private boolean appendLong(int key, long val) {
        if (!checkKey(key))
            return false;

        // 4 int32s: size, key, type, value.
        mParcel.writeInt(3 * kInt32Size + 2 * kInt32Size);
        mParcel.writeInt(key);
        mParcel.writeInt(LONG_VAL);
        mParcel.writeLong(val);

        return true;
    }

    private boolean appendString(int key, byte[] bytes) {
        if (!checkKey(key))
            return false;

        //String16: len(int32_t) + str
        int strLen = (bytes.length+1) * 2 + kInt32Size;
        if (strLen % 4 != 0) {
            // Align the len
            strLen += 4 - (strLen % 4);
        }
        // 3 int32s + 1 String16: size, key, type, str.
        mParcel.writeInt(3 * kInt32Size + strLen);
        mParcel.writeInt(key);
        mParcel.writeInt(STRING_VAL);
        mParcel.writeString(new String(bytes));

        return true;
    }
}
