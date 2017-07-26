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

import java.util.LinkedList;
import java.util.concurrent.locks.ReentrantLock;

/** fixed size pool base class - threadsafe */
public abstract class ObjectPool<T> {

    private final LinkedList<T> mList;
    private final ReentrantLock mLock;
    private final int mMax;

    public ObjectPool(int poolSize) {
        mMax = poolSize;
        mList = new LinkedList<T>();
        mLock = new ReentrantLock();
    }

    /** clean the object for reuse */
    protected abstract void cleanup(T object);

    public final void clearPool() {
        mLock.lock();
        try {
            mList.clear();
        } finally {
            mLock.unlock();
        }
    }

    /** Create a new object here */
    protected abstract T create();

    public final T obtain() {
        T result;
        mLock.lock();
        try {
            result = mList.poll();
        } finally {
            mLock.unlock();
        }
        if (result == null) {
            result = create();
        }
        return result;
    }

    public final void putBack(T object) {
        mLock.lock();
        try {
            if (mList.size() <= mMax) {
                cleanup(object);
                mList.push(object);
            }
        } finally {
            mLock.unlock();
        }
    }
}
