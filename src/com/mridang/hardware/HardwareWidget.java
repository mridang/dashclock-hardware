package com.mridang.hardware;

import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.util.Log;

import com.bugsense.trace.BugSenseHandler;
import com.google.android.apps.dashclock.api.DashClockExtension;
import com.google.android.apps.dashclock.api.ExtensionData;

/*
 * This class is the main class that provides the widget
 */
public class HardwareWidget extends DashClockExtension {

	/* This is the processor utilization since the the last update */
	Long lngPreviousTotal = 0L;
	/* This is the processor utilization since the the last update */
	Long lngPreviousIdle = 0L;

	/*
	 * @see com.google.android.apps.dashclock.api.DashClockExtension#onCreate()
	 */
	public void onCreate() {

		super.onCreate();
		Log.d("HardwareWidget", "Created");
		BugSenseHandler.initAndStartSession(this, getString(R.string.bugsense));

	}

	/*
	 * @see
	 * com.google.android.apps.dashclock.api.DashClockExtension#onUpdateData
	 * (int)
	 */
	@Override
	protected void onUpdateData(int intReason) {

		Log.d("HardwareWidget", "Fetching processor and memory utilisation information");
		final ExtensionData edtInformation = new ExtensionData();
		setUpdateWhenScreenOn(true);

		try {

			ActivityManager mgrActivity = ((ActivityManager) getSystemService(Context.ACTIVITY_SERVICE));
			RandomAccessFile rafProcessor = new RandomAccessFile("/proc/stat", "r");

			try {

				List<String> lstColumns = Arrays.asList(rafProcessor.readLine().split(" "));

				Long lngCurrentIdle = Long.parseLong(lstColumns.get(5));
				Long lngCurrentTotal = 0L;
				for (String strStatistic : lstColumns.subList(2, lstColumns.size())) {
					lngCurrentTotal = lngCurrentTotal + Integer.parseInt(strStatistic);
				}
				Long lngDifferenceIdle = lngCurrentIdle - lngPreviousIdle;
				Long lngDifferenceTotal = lngCurrentTotal - lngPreviousTotal;

				lngPreviousIdle = lngCurrentIdle;
				lngPreviousTotal = lngCurrentTotal;

				Long lngUsageDelta = lngDifferenceTotal - lngDifferenceIdle;
				MemoryInfo memInformation = new MemoryInfo(); 
				mgrActivity.getMemoryInfo(memInformation);

				edtInformation.expandedTitle(getString(R.string.processor, lngDifferenceTotal > 0 ? (100L * lngUsageDelta / lngDifferenceTotal) : 0L));
				edtInformation.expandedBody(getString(R.string.memory, memInformation.availMem / 1048576L, memInformation.totalMem / 1048576L));
				edtInformation.visible(true);

			} finally {
				rafProcessor.close();
			}

			if (new Random().nextInt(5) == 0) {

				PackageManager mgrPackages = getApplicationContext().getPackageManager();

				try {

					mgrPackages.getPackageInfo("com.mridang.donate", PackageManager.GET_META_DATA);

				} catch (NameNotFoundException e) {

					Integer intExtensions = 0;
					Intent ittFilter = new Intent("com.google.android.apps.dashclock.Extension");
					String strPackage;

					for (ResolveInfo info : mgrPackages.queryIntentServices(ittFilter, 0)) {

						strPackage = info.serviceInfo.applicationInfo.packageName;
						intExtensions = intExtensions + (strPackage.startsWith("com.mridang.") ? 1 : 0); 

					}

					if (intExtensions > 1) {

						edtInformation.visible(true);
						edtInformation.clickIntent(new Intent(Intent.ACTION_VIEW).setData(Uri.parse("market://details?id=com.mridang.donate")));
						edtInformation.expandedTitle("Please consider a one time purchase to unlock.");
						edtInformation.expandedBody("Thank you for using " + intExtensions + " extensions of mine. Click this to make a one-time purchase or use just one extension to make this disappear.");
						setUpdateWhenScreenOn(true);

					}

				}

			} else {
				setUpdateWhenScreenOn(true);
			}

		} catch (Exception e) {
			edtInformation.visible(false);
			Log.e("HardwareWidget", "Encountered an error", e);
			BugSenseHandler.sendException(e);
		}

		edtInformation.icon(R.drawable.ic_dashclock);
		publishUpdate(edtInformation);
		Log.d("HardwareWidget", "Done");

	}

	/*
	 * @see com.google.android.apps.dashclock.api.DashClockExtension#onDestroy()
	 */
	public void onDestroy() {

		super.onDestroy();
		Log.d("HardwareWidget", "Destroyed");
		BugSenseHandler.closeSession(this);

	}

}