package com.mridang.hardware;

import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.apps.dashclock.api.ExtensionData;

import org.acra.ACRA;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.List;

/*
 * This class is the main class that provides the widget
 */
public class HardwareWidget extends ImprovedExtension {

	/**
	 * The boolean value indicating whether to show the notification or not
	 */
	private static boolean booNotification = false;

	/**
	 * The handler class that runs every second to update the notification with the processor usage.
	 */
	private class NotificationHandler extends Handler {

		/**
		 * The processor statistics file from which the figures should be read repeatedly
		 */
		private RandomAccessFile rafProcessor;
		/**
		 * The value of the total processor utilization since the last update
		 */
		private Long lngPreviousTotal = 0L;
		/**
		 * The value of the idle processor utilization since the last update
		 */
		private Long lngPreviousIdle = 0L;
		/**
		 * The percentage of the processor utilization since the last update
		 */
		private Double dblPercent = 0D;

		/**
		 * Simple constructor to initialize the initial value of the previous
		 */
		public NotificationHandler(Looper looLooper) {

			super(looLooper);
			try {
				rafProcessor = new RandomAccessFile("/proc/stat", "r");
			} catch (FileNotFoundException e) {
				throw new RuntimeException("Unable to open the /proc/stat file");
			}

		}

		/**
		 * Handler method that updates the notification icon with the current processor usage. It
		 * does this by reading the /proc/stat file and specifically the of the first CPU row as
		 * are only concerned with the cumulative processor utilization.
		 */
		@Override
		public void handleMessage(Message msgMessage) {

			HardwareWidget.hndNotifier.sendEmptyMessageDelayed(1, 2000L);

			try {

				rafProcessor.seek(0);
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
				dblPercent = 100.0 * (lngUsageDelta / (lngDifferenceTotal + 0.01));
				notBuilder.setSmallIcon(R.drawable.i0 + (int) (dblPercent / 10));

				if (HardwareWidget.booNotification) {
					HardwareWidget.mgrNotifications.notify(111, HardwareWidget.notBuilder.build());
				} else {
					HardwareWidget.mgrNotifications.cancel(111);
				}

				Log.v(HardwareWidget.this.getTag(), "Current processor usage is " + dblPercent.toString());

			} catch (Exception e) {
				Log.e(HardwareWidget.this.getTag(), "Error creating notification for usage " + dblPercent);
			}

		}

		/**
		 * Closes the processor statistics file from which the figures are be read repeatedly
		 */
		public void closeFile() {

			if (rafProcessor != null) {
				try {
					rafProcessor.close();
				} catch (IOException e) {
					Log.w(HardwareWidget.this.getTag(), "Unable to successfully close the file");
				}
			}

		}

		/**
		 * Returns the current processor utilization from the processor statistics file
		 *
		 * @return The double value representing the percentage of processor utilization
		 */
		public Integer getUsagePercentage() {
			return dblPercent.intValue();
		}

	}

	/**
	 * The instance of the handler that updates the notification
	 */
	private static NotificationHandler hndNotifier;
	/**
	 * The instance of the manager of the notification services
	 */
	private static NotificationManager mgrNotifications;
	/**
	 * The instance of the notification builder to rebuild the notification
	 */
	private static NotificationCompat.Builder notBuilder;

	/*
	 * (non-Javadoc)
	 * @see com.mridang.hardware.ImprovedExtension#getIntents()
	 */
	@Override
	protected IntentFilter getIntents() {

		IntentFilter itfScreen = new IntentFilter();
		itfScreen.addAction(Intent.ACTION_SCREEN_ON);
		itfScreen.addAction(Intent.ACTION_SCREEN_OFF);
		itfScreen.addAction("com.mridang.hardware.ACTION_REFRESH");
		return itfScreen;

	}

	/*
	 * (non-Javadoc)
	 * @see com.mridang.hardware.ImprovedExtension#getTag()
	 */
	@Override
	protected String getTag() {
		return getClass().getSimpleName();
	}

	/*
	 * (non-Javadoc)
	 * @see com.mridang.hardware.ImprovedExtension#getUris()
	 */
	@Override
	protected String[] getUris() {
		return null;
	}

	/*
	 * @see
	 * com.google.android.apps.dashclock.api.DashClockExtension#onUpdateData
	 * (int)
	 */
	@Override
	protected void onUpdateData(int intReason) {

		Log.d(getTag(), "Fetching processor and memory utilisation information");
		final ExtensionData edtInformation = new ExtensionData();
		setUpdateWhenScreenOn(true);

		try {

			ActivityManager mgrActivity = ((ActivityManager) getSystemService(Context.ACTIVITY_SERVICE));

			MemoryInfo memInformation = new MemoryInfo();
			mgrActivity.getMemoryInfo(memInformation);
			Long lngFree = memInformation.availMem / 1048576L;
			Long lngTotal = memInformation.totalMem / 1048576L;
			Integer intLevel = (int) ((100.0 * (lngFree / (lngTotal + 0.01))) / 25);

			edtInformation.status(hndNotifier.getUsagePercentage().toString());
			edtInformation.expandedTitle(getString(R.string.processor, hndNotifier.getUsagePercentage()));
			edtInformation.expandedBody(getString(R.string.memory, lngFree, lngTotal));
			edtInformation.visible(true);

			notBuilder = notBuilder.setContentTitle(getResources().getStringArray(R.array.usage)[intLevel]);
			notBuilder = notBuilder.setContentText(getString(R.string.memory, lngFree, lngTotal));

		} catch (Exception e) {
			edtInformation.visible(false);
			Log.e(getTag(), "Encountered an error", e);
			ACRA.getErrorReporter().handleSilentException(e);
		}

		edtInformation.icon(R.drawable.ic_dashclock);
		doUpdate(edtInformation);

	}

   /*
	* (non-Javadoc)
	* @see com.mridang.hardware.ImprovedExtension#onInitialize(java.lang.Boolean)
	*/
	@Override
	protected void onInitialize(boolean booReconnect) {

		super.onInitialize(booReconnect);
		mgrNotifications = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

		Intent ittSettings = new Intent();
		ittSettings.setComponent(new ComponentName("com.android.settings",
				"com.android.settings.Settings$DevelopmentSettingsActivity"));
		PendingIntent pitSettings = PendingIntent.getActivity(this, 0, ittSettings, 0);
		notBuilder = new NotificationCompat.Builder(this);
		notBuilder = notBuilder.setSmallIcon(R.drawable.ic_dashclock);
		notBuilder = notBuilder.setContentIntent(pitSettings);
		notBuilder = notBuilder.setOngoing(true);
		notBuilder = notBuilder.setWhen(0);
		notBuilder = notBuilder.setOnlyAlertOnce(true);
		notBuilder = notBuilder.setPriority(Integer.MAX_VALUE);
		notBuilder = notBuilder.setVisibility(NotificationCompat.VISIBILITY_SECRET);
		notBuilder = notBuilder.setCategory(NotificationCompat.CATEGORY_SERVICE);

		booNotification = getBoolean("notification", true);
		hndNotifier = new NotificationHandler(Looper.getMainLooper());
		if (booNotification) {
			hndNotifier.sendEmptyMessage(1);
		}

	}

	/*
	 * (non-Javadoc)
	 * @see com.mridang.hardware.ImprovedExtension#onDestroy()
	 */
	@Override
	public void onDestroy() {

		hndNotifier.removeMessages(1);
		hndNotifier.closeFile();
		mgrNotifications.cancel(111);
		super.onDestroy();

	}

	/*
	 * (non-Javadoc)
	 * @see com.mridang.hardware.ImprovedExtension#onReceiveIntent(android.content.Context, android.content.Intent)
	 */
	@Override
	protected void onReceiveIntent(Context ctxContext, Intent ittIntent) {

		if (ittIntent.getAction().equalsIgnoreCase(Intent.ACTION_SCREEN_OFF)) {

			Log.d(getTag(), "Screen off; hiding the notification");
			hndNotifier.removeMessages(1);
			mgrNotifications.cancel(111);

		} else if (ittIntent.getAction().equalsIgnoreCase(Intent.ACTION_SCREEN_ON)) {

			Log.d(getTag(), "Screen on; showing the notification");
			hndNotifier.sendEmptyMessage(1);


		} else {

			booNotification = getBoolean("notification", true);
			onUpdateData(UPDATE_REASON_MANUAL);
			if (booNotification) {
				hndNotifier.sendEmptyMessage(1);
			} else {

				hndNotifier.removeMessages(1);
				mgrNotifications.cancel(111);

			}

		}

	}

}