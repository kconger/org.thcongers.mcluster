/*
Copyright (C) 2014 Keith Conger <keith.conger@gmail.com>

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.thecongers.mcluster;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;


class LogData {
    private static PrintWriter outFile = null;
    private static final String TAG = "mCluster_Log";

    private static void initialize()
    {
        try {
            File root = new File(Environment.getExternalStorageDirectory() + "/mCluster/");
            if(!root.exists()) {
                if(!root.mkdirs()){
                    Log.d(TAG,"Unable to create directory: " + root);
                }
            }
            if(root.canWrite())
            {
                Log.d(TAG,"Initialize Logging");
                // Get current time in UTC
                Calendar cal = Calendar.getInstance();
                Date date = cal.getTime();
                SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd-HH:mm:ss");
                formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
                String curdatetime = formatter.format(date);

                File logFile = new File( root, "mCluster-" + curdatetime + ".csv" );
                FileWriter logWriter = new FileWriter( logFile );
                outFile = new PrintWriter( logWriter );
                outFile.write( "Time(UTC),Message ID,Byte1,Byte2,Byte3,Byte4,Byte5,Byte6,Byte7,Byte8\n" );
            }
        } catch (IOException e) {
            Log.d(TAG, "Could not write to file: " + e.getMessage());
        }
    }

    public void write(String message)
    {
        if(outFile == null)
            initialize();

        // Get current time in UTC
        Calendar cal = Calendar.getInstance();
        Date date = cal.getTime();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        String curdatetime = formatter.format(date);

        outFile.write( curdatetime + "," + message + "\n" );
        outFile.flush();
    }

    public void shutdown()
    {
        if(outFile != null)
            outFile.close();
    }
}
