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

package com.archos.mediaprovider;

import android.util.Log;

import java.util.concurrent.locks.ReentrantLock;

/** Represents the state of the database importing mechanism */
public enum ImportState {
    VIDEO;

    private static final String TAG = ArchosMediaCommon.TAG_PREFIX + ImportState.class.getSimpleName();
    private static final boolean DBG = false;

    public enum State {
        UNKNOWN,
        INITIAL_IMPORT,
        REGULAR_IMPORT,
        IDLE
    }

    private State mState = State.UNKNOWN;
    private int mNumberOfFilesRemainingToImport = 0;
    private boolean mAndroidScanning = false;
    private final ReentrantLock mLock = new ReentrantLock();
    private volatile boolean mDbDirty = false;

    /** INTERNAL setter only */
    public void setState(State state) {
        mLock.lock();
        try {
            if (mAndroidScanning) {
                // ignore
                if (DBG) Log.d(TAG, "Android scanning: not setting " + state.name());
            } else {
                setStateLocked(state);
            }
        } finally {
            mLock.unlock();
        }
    }

    /** INTERNAL setter only */
    public void setRemainingCount(int numberOfFilesRemainingToImport) {
        mLock.lock();
        try {
            setNumberOfFilesRemainingToImportLocked(numberOfFilesRemainingToImport);
        } finally {
            mLock.unlock();
        }
    }

    /** INTERNAL setter only */
    public void setAndroidScanning(boolean running) {
        mLock.lock();
        try {
            if (running) {
                mAndroidScanning = true;
                // whi
                setStateLocked(State.INITIAL_IMPORT);
            } else {
                mAndroidScanning = false;
                // not setting back state until done via setState
            }
        } finally {
            mLock.unlock();
        }
    }

    /** INTERNAL setter only - db import state not up to date */
    public void setDirty(boolean dirty) {
        mDbDirty = dirty;
    }

    /** Db import state not up to date */
    public boolean isDirty() {
        return mDbDirty;
    }

    private void setStateLocked(State state) {
        if (DBG && mState != state) {
            Log.d(TAG, "State " + mState.name() + " -> " + state.name());
        }
        mState = state;
    }

    private void setNumberOfFilesRemainingToImportLocked(int count) {
        mNumberOfFilesRemainingToImport = count;
    }

    /** returns state of Database import */
    public State getState() {
        mLock.lock();
        try {
            if (DBG) Log.d(TAG, "getState=" + mState.name());
            return mState;
        } finally {
            mLock.unlock();
        }
    }

    /**
     * returns true if big initial import is running,
     *  Android actively scanning is considered initial import too.
     **/
    public boolean isInitialImport() {
        mLock.lock();
        try {
            if (DBG) Log.d(TAG, "isInitialImport=" + (mState == State.INITIAL_IMPORT));
            return mState == State.INITIAL_IMPORT;
        } finally {
            mLock.unlock();
        }
    }

    /**
     * @return the number of files found in the Android DB that are still pending for import
     */
    public int getNumberOfFilesRemainingToImport() {
        mLock.lock();
        try {
            if (DBG) Log.d(TAG, "mNumberOfFilesRemainingToImport=" +mNumberOfFilesRemainingToImport);
            return mNumberOfFilesRemainingToImport;
        } finally {
            mLock.unlock();
        }
    }
}
