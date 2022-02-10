// Copyright 2020 Courville Software
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

package com.archos.mediaprovider.video;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class NetworkScannerUtil {

    private static final Logger log = LoggerFactory.getLogger(NetworkScannerUtil.class);

    private static final Integer JOBID_NETSCANNER = 0;

    // schedule the start of the service every 10 - 30 seconds
    public static void scheduleJob(Context context) {
        //reset job start time at boot
        int startingTime = PreferenceManager.getDefaultSharedPreferences(context).getInt(NetworkAutoRefresh.AUTO_RESCAN_STARTING_TIME_PREF, -1);
        int periode = PreferenceManager.getDefaultSharedPreferences(context).getInt(NetworkAutoRefresh.AUTO_RESCAN_PERIOD,-1);
        if(startingTime!=-1&&periode>0){
            scheduleNewRescan(context,startingTime,periode,false);
        }
    }

    public static void scheduleNewRescan(Context context, int startingTimeOfDay, int periode, boolean setPreference){
        log.debug("scheduleNewRescan: resetting alarm starting at " +
                String.valueOf(startingTimeOfDay / 1000 / 60 / 60) + "h" +
                ", period: " + String.valueOf(periode / 1000 / 60 / 60) + "h");

        //start rescan if lastscan + period < current time (when has booted after scheduled time)

        ComponentName serviceComponent = new ComponentName(context, NetworkRefreshJob.class);
        JobInfo.Builder jobBuilder = new JobInfo.Builder(JOBID_NETSCANNER, serviceComponent);

        JobScheduler jobScheduler;
        if (Build.VERSION.SDK_INT >= 23)
            jobScheduler = context.getSystemService(JobScheduler.class);
        else
            jobScheduler = (JobScheduler) context.getSystemService(context.JOB_SCHEDULER_SERVICE);

        if (startingTimeOfDay!=-1&&periode>0) {
            Calendar c = Calendar.getInstance();
            c.set(Calendar.HOUR_OF_DAY,0);
            c.set(Calendar.MINUTE,0);
            c.set(Calendar.SECOND,0);
            c.set(Calendar.MILLISECOND,0);
            long nextStart = c.getTimeInMillis()+startingTimeOfDay;
            while(nextStart < System.currentTimeMillis())
                nextStart+=periode;
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS");
            Date dt = new Date(nextStart);
            String S = sdf.format(dt);
            log.debug("scheduleNewRescan: periode "+periode/1000/60/60);
            log.debug("scheduleNewRescan: launching at "+S);
            log.debug("scheduleNewRescan: launching in " + (nextStart - System.currentTimeMillis()) / 1000 / 60 + " minutes");

            jobBuilder.setOverrideDeadline(nextStart - System.currentTimeMillis());

            // could be nice: trigger when device is idle but introduces some non deterministic behavior that could be understood as bug
            //jobBuilder.setRequiresDeviceIdle(true);
            // only when charging? Nope
            //jobBuilder.setRequiresCharging(false);
            // avoid cellular? Nope
            //jobBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED);
            jobScheduler.schedule(jobBuilder.build());
            log.debug("scheduleNewRescan: job scheduled");
        } else {
            jobScheduler.cancel(JOBID_NETSCANNER);
            log.debug("scheduleNewRescan: job canceled");
        }
        if(setPreference) {
            log.debug("scheduleNewRescan: preference");
            PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(NetworkAutoRefresh.AUTO_RESCAN_STARTING_TIME_PREF,startingTimeOfDay).apply();
            PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(NetworkAutoRefresh.AUTO_RESCAN_PERIOD, periode).apply();
        }
    }
}