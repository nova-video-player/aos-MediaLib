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

import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

public class SQLiteDbProxy {
    protected static final String TAG = SQLiteDbProxy.class.getSimpleName();
    private static final boolean DBG = false;

    public interface CustomFunction {
        public void callback(String[] args);
    }

    /**
     * @return SQLiteDatabase.CustomFunction.class
     */
    private static Class<?> getInterface() {
        Class<?>[] classes = SQLiteDatabase.class.getDeclaredClasses();
        for (Class<?> cls : classes) {
            if ("android.database.sqlite.SQLiteDatabase$CustomFunction".equals(cls.getName())) {
                return cls;
            }
        }
        Log.e(TAG, "did not find SQLiteDatabase$CustomFunction");
        return null;
    }

    /**
     * @return Object that implements SQLiteDatabase.CustomFunction
     */
    private static Object createProxyInstance(Class<?> iface, CustomFunction function) {
        return Proxy.newProxyInstance(iface.getClassLoader(),
                new Class[] { iface },
                new InterfaceWrapper(function));
    }

    /**
     * calls SQLiteDatabase.addCustomFunction(String name, int numArgs, CustomFunction function)
     * @return true if that succeeded
     */
    private static boolean invoke(Class<?> iface, SQLiteDatabase db, String name, int numArgs, Object function) {
        Class<?>[] parameterTypes = {
                String.class, // name
                int.class,    // numArgs
                iface         // function
        };
        try {
            Method method = SQLiteDatabase.class.getMethod("addCustomFunction", parameterTypes);
            method.invoke(db, name, Integer.valueOf(numArgs), function);
            return true;
        } catch (NoSuchMethodException e) {
            if (DBG) Log.e(TAG, e.toString(), e);
        } catch (IllegalArgumentException e) {
            if (DBG) Log.e(TAG, e.toString(), e);
        } catch (IllegalAccessException e) {
            if (DBG) Log.e(TAG, e.toString(), e);
        } catch (InvocationTargetException e) {
            if (DBG) Log.e(TAG, e.toString(), e);
        }
        Log.e(TAG, "invoke addCustomFunction() failed");
        return false;
    }

    /**
     * Reflection Wrapper for SQLiteDatabase.addCustomFunction(String name, int numArgs, CustomFunction function)
     * @return true if succeeded
     */
    public static boolean installCustomFunction(SQLiteDatabase db, String name, int numArgs, CustomFunction function) {
        Class<?> iface = getInterface();
        if (iface == null) {
            return false;
        }

        Object instance = createProxyInstance(iface, function);
        if (instance == null) {
            Log.e(TAG, "could not create proxy instance");
            return false;
        }

        boolean success = invoke(iface, db, name, numArgs, instance);
        if (DBG && success) {
            Log.d(TAG, "Successfully installed CustomFunction");
        }
        return success;
    }

    /**
     * InvocationHandler that acts as implementing class at runtime. Forwards
     * SQLiteDatabase.CustomFunction#callback(String[] args) to our implementation
     */
    private static class InterfaceWrapper implements InvocationHandler {

        private final CustomFunction mCallback;

        /** callback to forward to */
        public InterfaceWrapper(CustomFunction callback) {
            mCallback = callback;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            Class<?>[] paramTypes = method.getParameterTypes();
            int paramNum = paramTypes != null ? paramTypes.length : 0;
            Object arg0 = args != null && args.length > 0 ? args[0] : null;
            // check if "callback(String[] args)" is called
            if ("callback".equals(methodName) && paramNum == 1 && arg0 instanceof String[]) {
                String[] stringArg = (String[]) arg0;
                if (DBG) Log.d(TAG, "callback: " + Arrays.toString(stringArg));
                mCallback.callback(stringArg);
                return null;
            }
            Log.e(TAG, "unknown invokation: " + methodName + Arrays.toString(args));
            return null;
        }
    }

    private SQLiteDbProxy() {
        // no instance pls
    }
}
