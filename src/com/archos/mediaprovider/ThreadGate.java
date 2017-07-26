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

import android.support.annotation.NonNull;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A Gate that can be opened / closed by any thread without blocking.
 * Threads trying to pass the gate will need to wait when it's closed.
 *
 * Also has a final "broken" state that counts as open so threads can be unblocked.
 * Adds overrideable "actions" that are executed in certain cases. Those are executed
 * atomically within e.g. open.
 */
public class ThreadGate {

    /**
     * Gate can be open/closed until it's broken. Once broken it's dead and stays in that state.
     */
    public static final int CLOSED = 0;
    public static final int OPEN = 1;
    public static final int BROKEN = 2;


    private final ReentrantLock mLock = new ReentrantLock();
    private final Condition mOpenState = mLock.newCondition();

    private int mState;

    public ThreadGate(boolean initiallyOpen) {
        mState = initiallyOpen ? OPEN : CLOSED;
    }

    private void throwOnBroken() {
        if (mState == BROKEN)
            throw new IllegalStateException("Can't do this anymore. Gate is broken.");
    }

    /**
     * @return true if state changed
     */
    public final boolean open() {
        mLock.lock();
        try {
            throwOnBroken();
            if (mState == CLOSED) {
                mState = OPEN;
                mOpenState.signalAll();
                openAction();
                return true;
            }
            return false;
        } finally {
            mLock.unlock();
        }
    }

    /**
     * @return true if state changed
     */
    public final boolean close() {
        mLock.lock();
        try {
            throwOnBroken();
            if (mState == OPEN) {
                mState = CLOSED;
                closeAction();
                return true;
            }
            return false;
        } finally {
            mLock.unlock();
        }

    }

    /** Breaks the gate - counts as open */
    public final void breakGate() {
        mLock.lock();
        try {
            // could allow this here but it's inconsistent if we don't throw
            throwOnBroken();
            mState = BROKEN;
            mOpenState.signalAll();
            breakAction();
        } finally {
            mLock.unlock();
        }
    }

    /** returns the current state */
    public int getState() {
        mLock.lock();
        try {
            return mState;
        } finally {
            mLock.unlock();
        }
    }

    /**
     * returns the state in which the gate was passed: broken or open
     */
    public int pass() throws InterruptedException {
        mLock.lock();
        try {
            if (mState == CLOSED)
                waitAction();
            // wait for either OPEN or BROKEN
            while (mState == CLOSED) {
                mOpenState.await();
            }

            // not returning mState here since actions can potentially change mState
            switch (mState) {
                case OPEN:
                    passOpenAction();
                    return OPEN;
                case BROKEN:
                    passBrokenAction();
                    return BROKEN;
                default:
                    throw new IllegalStateException("This is supposed to be unreachable.");
            }

        } finally {
            mLock.unlock();
        }

    }

    /**
     * @return OPEN or BROKEN if gate was passed in specified time. CLOSED if gate didn't
     * open in time. And finally InterruptedException in case the thread was interrupted
     * during waiting (which also means that the gate was not passed).
     */
    public int pass(long time, @NonNull TimeUnit unit) throws InterruptedException {
        mLock.lock();
        try {
            if (mState == CLOSED) {
                // even for 0 / negative time.. ?!
                waitAction();

                // wait for either OPEN or BROKEN, see doc on spurious wakeups why this is in a loop
                long remainingNanos = unit.toNanos(time);
                while (mState == CLOSED) {
                    if (remainingNanos <= 0) {
                        break;
                    }
                    remainingNanos = mOpenState.awaitNanos(remainingNanos);
                }
            }

            // not returning mState here since actions can potentially change mState
            switch (mState) {
                case OPEN:
                    passOpenAction();
                    return OPEN;
                case BROKEN:
                    passBrokenAction();
                    return BROKEN;
                case CLOSED:
                    // no action?
                    return CLOSED;
                default:
                    throw new IllegalStateException("This is supposed to be unreachable.");
            }
        } finally {
            mLock.unlock();
        }

    }

    /**
     * Override to do something while closing Gate
     */
    protected void closeAction() {
        // nothing
    }

    /**
     * Override to do something while opening Gate
     */
    protected void openAction() {
        // nothing
    }

    /**
     * Override to do something while breaking Gate
     */
    protected void breakAction() {
        // nothing
    }

    /**
     * Override to do something each time a thread has to wait when passing the gate
     */
    protected void waitAction() {

    }

    /**
     * Override to do something each time a thread passes the gate in open state
     */
    protected void passOpenAction() {
        // nothing
    }

    /**
     * Override to do something each time a thread passes the gate in broken state
     */
    protected void passBrokenAction() {
        // nothing
    }

}
