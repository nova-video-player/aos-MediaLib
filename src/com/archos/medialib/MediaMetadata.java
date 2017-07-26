/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.archos.medialib;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Set;
import java.util.TimeZone;


/**
   Class to hold the media's metadata.  Metadata are used
   for human consumption and can be embedded in the media (e.g
   shoutcast) or available from an external source. The source can be
   local (e.g thumbnail stored in the DB) or remote.

   Metadata is like a Bundle. It is sparse and each key can occur at
   most once. The key is an integer and the value is the actual metadata.

   The caller is expected to know the type of the metadata and call
   the right get* method to fetch its value.
   
   @hide
 */
public class MediaMetadata implements Parcelable
{
    // The metadata are keyed using integers rather than more heavy
    // weight strings. We considered using Bundle to ship the metadata
    // between the native layer and the java layer but dropped that
    // option since keeping in sync a native implementation of Bundle
    // and the java one would be too burdensome. Besides Bundle uses
    // String for its keys.
    // The key range [0 8192) is reserved for the system.
    //
    // We manually serialize the data in Parcels. For large memory
    // blob (bitmaps, raw pictures) we use MemoryFile which allow the
    // client to make the data purge-able once it is done with it.
    //

    /**
     * {@hide}
     */
    public static final int ANY = 0;  // Never used for metadata returned, only for filtering.
                                      // Keep in sync with kAny in MediaPlayerService.cpp

    // Shorthands to set the MediaPlayer's metadata filter.
    /**
     * {@hide}
     */
    public static final Set<Integer> MATCH_NONE = Collections.EMPTY_SET;
    /**
     * {@hide}
     */
    public static final Set<Integer> MATCH_ALL = Collections.singleton(ANY);

    /**
     * {@hide}
     */
    public static final int STRING_VAL     = 1;
    /**
     * {@hide}
     */
    public static final int INTEGER_VAL    = 2;
    /**
     * {@hide}
     */
    public static final int BOOLEAN_VAL    = 3;
    /**
     * {@hide}
     */
    public static final int LONG_VAL       = 4;
    /**
     * {@hide}
     */
    public static final int DOUBLE_VAL     = 5;
    /**
     * {@hide}
     */
    public static final int DATE_VAL       = 6;
    /**
     * {@hide}
     */
    public static final int BYTE_ARRAY_VAL = 7;
    // FIXME: misses a type for shared heap is missing (MemoryFile).
    // FIXME: misses a type for bitmaps.
    protected static final int LAST_TYPE = 7;

    protected static final String TAG = "MediaMetadata";
    protected static final int kInt32Size = 4;
    protected static final int kMetaHeaderSize = 2 * kInt32Size; //  size + marker
    protected static final int kRecordHeaderSize = 3 * kInt32Size; // size + id + type

    protected static final int kMetaMarker = 0x4d455441;  // 'M' 'E' 'T' 'A'

    // After a successful parsing, set the parcel with the serialized metadata.
    protected Parcel mParcel;

    protected int mBegin = 0;

    // Map to associate a Metadata key (e.g TITLE) with the offset of
    // the record's payload in the parcel.
    // Used to look up if a key was present too.
    // Key: Metadata ID
    // Value: Offset of the metadata type field in the record.
    protected final HashMap<Integer, Integer> mKeyToPosMap =
            new HashMap<Integer, Integer>();

    /**
     * {@hide}
     */
    public MediaMetadata() { }

    /**
     * Go over all the records, collecting metadata keys and records'
     * type field offset in the Parcel. These are stored in
     * mKeyToPosMap for latter retrieval.
     * Format of a metadata record:
     <pre>
                         1                   2                   3
      0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |                     record size                               |
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |                     metadata key                              |  // TITLE
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |                     metadata type                             |  // STRING_VAL
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |                                                               |
      |                .... metadata payload ....                     |
      |                                                               |
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     </pre>
     * @param parcel With the serialized records.
     * @param bytesLeft How many bytes in the parcel should be processed.
     * @return false if an error occurred during parsing.
     */
    private boolean scanAllRecords(Parcel parcel, int bytesLeft) {
        int recCount = 0;
        boolean error = false;

        mKeyToPosMap.clear();
        while (bytesLeft > kRecordHeaderSize) {
            final int start = parcel.dataPosition();
            // Check the size.
            final int size = parcel.readInt();

            if (size <= kRecordHeaderSize) {  // at least 1 byte should be present.
                Log.e(TAG, "Record is too short");
                error = true;
                break;
            }

            // Check the metadata key.
            final int metadataId = parcel.readInt();

            // Store the record offset which points to the type
            // field so we can later on read/unmarshall the record
            // payload.
            if (mKeyToPosMap.containsKey(metadataId)) {
                Log.e(TAG, "Duplicate metadata ID found");
                error = true;
                break;
            }

            mKeyToPosMap.put(metadataId, parcel.dataPosition());

            // Check the metadata type.
            final int metadataType = parcel.readInt();
            if (metadataType <= 0 || metadataType > LAST_TYPE) {
                Log.e(TAG, "Invalid metadata type " + metadataType);
                error = true;
                break;
            }

            // Skip to the next one.
            parcel.setDataPosition(start + size);
            bytesLeft -= size;
            ++recCount;
        }

        if (0 != bytesLeft || error) {
            Log.e(TAG, "Ran out of data or error on record " + recCount);
            mKeyToPosMap.clear();
            return false;
        } else {
            return true;
        }
    }

    /**
     * Check a parcel containing metadata is well formed. The header
     * is checked as well as the individual records format. However, the
     * data inside the record is not checked because we do lazy access
     * (we check/unmarshall only data the user asks for.)
     *
     * Format of a metadata parcel:
     <pre>
                         1                   2                   3
      0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |                     metadata total size                       |
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |     'M'       |     'E'       |     'T'       |     'A'       |
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |                                                               |
      |                .... metadata records ....                     |
      |                                                               |
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     </pre>
     *
     * @param parcel With the serialized data. Metadata keeps a
     *               reference on it to access it later on. The caller
     *               should not modify the parcel after this call (and
     *               not call recycle on it.)
     * @return false if an error occurred.
     * {@hide}
     */
    public boolean parse(Parcel parcel) {
        if (parcel.dataAvail() < kMetaHeaderSize) {
            Log.e(TAG, "Not enough data " + parcel.dataAvail());
            return false;
        }

        final int pin = parcel.dataPosition();  // to roll back in case of errors.
        final int size = parcel.readInt();

        // The extra kInt32Size below is to account for the int32 'size' just read.
        if (parcel.dataAvail() + kInt32Size < size || size < kMetaHeaderSize) {
            Log.e(TAG, "Bad size " + size + " avail " + parcel.dataAvail() + " position " + pin);
            parcel.setDataPosition(pin);
            return false;
        }

        // Checks if the 'M' 'E' 'T' 'A' marker is present.
        final int kShouldBeMetaMarker = parcel.readInt();
        if (kShouldBeMetaMarker != kMetaMarker ) {
            Log.e(TAG, "Marker missing " + Integer.toHexString(kShouldBeMetaMarker));
            parcel.setDataPosition(pin);
            return false;
        }

        // Scan the records to collect metadata ids and offsets.
        if (!scanAllRecords(parcel, size - kMetaHeaderSize)) {
            parcel.setDataPosition(pin);
            return false;
        }
        mBegin = pin;
        mParcel = parcel;
        return true;
    }

    /**
     * @return The set of metadata ID found.
     */
    public Set<Integer> keySet() {
        return mKeyToPosMap.keySet();
    }

    /**
     * @return true if a value is present for the given key.
     */
    public boolean has(final int metadataId) {
        return mKeyToPosMap.containsKey(metadataId);
    }

    // Accessors.
    // Caller must make sure the key is present using the {@code has}
    // method otherwise a RuntimeException will occur.

    /**
     * {@hide}
     */
    public String getString(final int key) {
        checkType(key, STRING_VAL);
        return mParcel.readString();
    }

    /**
     * {@hide}
     */
    public int getInt(final int key) {
        checkType(key, INTEGER_VAL);
        return mParcel.readInt();
    }

    /**
     * Get the boolean value indicated by key
     */
    public boolean getBoolean(final int key) {
        checkType(key, BOOLEAN_VAL);
        return mParcel.readInt() == 1;
    }

    /**
     * {@hide}
     */
    public long getLong(final int key) {
        checkType(key, LONG_VAL);    /**
     * {@hide}
     */
        return mParcel.readLong();
    }

    /**
     * {@hide}
     */
    public double getDouble(final int key) {
        checkType(key, DOUBLE_VAL);
        return mParcel.readDouble();
    }

    /**
     * {@hide}
     */
    public byte[] getByteArray(final int key) {
        checkType(key, BYTE_ARRAY_VAL);
        return mParcel.createByteArray();
    }

    /**
     * {@hide}
     */
    public Date getDate(final int key) {
        checkType(key, DATE_VAL);
        final long timeSinceEpoch = mParcel.readLong();
        final String timeZone = mParcel.readString();

        if (timeZone.length() == 0) {
            return new Date(timeSinceEpoch);
        } else {
            TimeZone tz = TimeZone.getTimeZone(timeZone);
            Calendar cal = Calendar.getInstance(tz);

            cal.setTimeInMillis(timeSinceEpoch);
            return cal.getTime();
        }
    }

    /**
     * Check the type of the data match what is expected.
     */
    private void checkType(final int key, final int expectedType) {
        final int pos = mKeyToPosMap.get(key);

        mParcel.setDataPosition(pos);

        final int type = mParcel.readInt();
        if (type != expectedType) {
            throw new IllegalStateException("Wrong type " + expectedType + " but got " + type);
        }
    }

    public static final Parcelable.Creator<MediaMetadata> CREATOR
            = new Parcelable.Creator<MediaMetadata>() {
        public MediaMetadata createFromParcel(Parcel p) {
            MediaMetadata metadata = new MediaMetadata();
            Parcel data = Parcel.obtain();

            data.appendFrom(p, p.dataPosition(), p.dataAvail());
            data.setDataPosition(0);
            if (metadata.parse(data))
                return metadata;
            else
                return null;
        }
        public MediaMetadata[] newArray(int size) {
            return new MediaMetadata[size];
        }

    };

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        mParcel.setDataPosition(mBegin);
        dest.appendFrom(mParcel, 0, mParcel.dataAvail());
    }
}
