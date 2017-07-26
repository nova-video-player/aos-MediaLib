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

import java.util.HashSet;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Lock that locks per key only. Different keys can be processed in parallel.
 * Does not eat thread interrupts.
 * @param <T> Some class working with HashSets as keys, e.g. {@link String}
 */
public class MultiLock<T> {
    private final HashSet<T> mLockMap = new HashSet<T>();
    private final ReentrantLock mLock = new ReentrantLock();
    private final Condition mCondition = mLock.newCondition();

    /**
     * Locks. Waits until lock for <i>key</i> is available.
     */
    public void lock(T key) {
        mLock.lock();
        try {
            while (!mLockMap.add(key)) {
                mCondition.awaitUninterruptibly();
            }
        } finally {
            mLock.unlock();
        }
    }

    /**
     * Unlocks. Other threads can now lock <i>key</i> again.
     */
    public void unlock(T key) {
        mLock.lock();
        try {
            mLockMap.remove(key);
            mCondition.signalAll();
        } finally {
            mLock.unlock();
        }
    }
}
