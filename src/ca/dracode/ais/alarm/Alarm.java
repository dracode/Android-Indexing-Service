/*
 * Copyright 2014 Dracode Software.
 *
 * This file is part of AIS.
 *
 * AIS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * AIS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AIS.  If not, see <http://www.gnu.org/licenses/>.
 */

package ca.dracode.ais.alarm;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import ca.dracode.ais.service.IndexService;

public class Alarm extends BroadcastReceiver {

    /**
     * Starts the wakeup that calls for the device to be re-indexed after a certain period
     * @param context
     */
    public static void SetAlarm(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(context, Alarm.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, 0);
        // sets the alarm to repeat every 10 minutes
        // TODO - Make alarm time change according to a user preference
        if(am != null)
            am.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(),
                    10 * 60000, pi);
    }

    /**
     * Cancels the alerm started in SetAlarm(context) if it has been set
     * @param context
     */
    public static void CancelAlarm(Context context) {
        Intent i = new Intent(context, Alarm.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, 0);
        AlarmManager am = (AlarmManager) context.getSystemService("Context.ALARM_SERVICE");
        if(am != null) {
            am.cancel(pi);
        }
    }

    /**
     * Starts the indexer if it is not already running
     * @param context
     * @param intent
     */
    public void onReceive(Context context, Intent intent) {
        // Starts the indexService
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        if(!this.isMyServiceRunning(context) && prefs.getBoolean("enabled", true)) {
            Intent serviceIntent = new Intent(context, IndexService.class);
            serviceIntent.putExtra("crawl", true);
            context.startService(serviceIntent);
        }
    }

    /**
     * Checks if the indexing service is running
     * @param context
     * @return true if the service was found; false otherwise
     */
    private boolean isMyServiceRunning(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context
                .ACTIVITY_SERVICE);
        for(ActivityManager.RunningServiceInfo service : manager
                .getRunningServices(Integer.MAX_VALUE)) {
            if(IndexService.class.getName().equals(
                    service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
