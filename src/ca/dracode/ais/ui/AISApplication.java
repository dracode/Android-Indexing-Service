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

package ca.dracode.ais.ui;

import android.app.Application;
import android.util.Log;

/*
 * AISApplication.java
 * 
 * This file currently starts the IndexService upon application launch if it is not already started.
 * 
 * v0.3
 * Implement alarmManager to start the service at user-defined intervals
 */

public class AISApplication extends Application {

    private final static String TAG = "ca.dracode.ais.ais";

    public void onCreate() {
        super.onCreate();
    }

    /**
     * Called by system when low on memory.
     * Currently only logs.
     */
    public void onLowMemory() {
        super.onLowMemory();
        Log.w(TAG, "onLowMemory");
    }


}
