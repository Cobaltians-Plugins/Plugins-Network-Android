package io.kristal.networkstatusplugin;


import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import org.cobaltians.cobalt.Cobalt;
import org.cobaltians.cobalt.fragments.CobaltFragment;
import org.cobaltians.cobalt.plugin.CobaltAbstractPlugin;
import org.cobaltians.cobalt.plugin.CobaltPluginWebContainer;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class NetworkStatusPlugin extends CobaltAbstractPlugin implements NetworkChangeReceiver.NetworkChangeListener
{
	// TAG
	private static final String TAG = NetworkStatusPlugin.class.getSimpleName();

	/***********************************************************************************************
	 *
	 * MEMBERS
	 *
	 **********************************************************************************************/

	private static final String JSPluginName = "networkStatus";

	private static final String JSActionQueryStatus = "getStatus";
	private static final String JSActionStartStatusMonitoring = "startStatusMonitoring";
	private static final String JSActionStopStatusMonitoring = "stopStatusMonitoring";
	private static final String JSActionOnNetworkChanged = "onStatusChanged";

	private static final String kJSStatus = "status";

	private static final String TYPE_WIFI = "wifi";
	private static final String TYPE_MOBILE = "mobile";
	private static final String TYPE_ETHERNET = "ethernet";
	private static final String TYPE_VPN = "vpn";
	private static final String TYPE_BLUETOOTH = "bluetooth";
	private static final String TYPE_UNKNOWN = "unknown";
	private static final String TYPE_NONE = "none";

	private NetworkChangeReceiver networkChangeReceiver;
	private List<WeakReference<CobaltFragment>> listeningFragments;

	private static NetworkStatusPlugin sInstance;

	public static CobaltAbstractPlugin getInstance(CobaltPluginWebContainer webContainer) {
		if (sInstance == null)
			sInstance = new NetworkStatusPlugin();

		sInstance.addWebContainer(webContainer);

		return sInstance;
	}

	public NetworkStatusPlugin() {
		listeningFragments = new ArrayList<>();
	}

	@Override
	public void onMessage(CobaltPluginWebContainer webContainer, JSONObject message) {
		try {
			String action = message.getString(Cobalt.kJSAction);

			switch (action) {
				case JSActionQueryStatus:
					sendStatusCallback(webContainer, message.getString(Cobalt.kJSCallback), getStatus(webContainer));
					break;

				case JSActionStartStatusMonitoring:
					startStatusMonitoring(webContainer);
					break;

				case JSActionStopStatusMonitoring:
					stopStatusMonitoring(webContainer);
					break;

				default:
					if (Cobalt.DEBUG)
						Log.d(TAG, "onMessage: unknown action " + action);
					break;
			}
		}
		catch (JSONException exception) {
			if (Cobalt.DEBUG)
				Log.d(TAG, "onMessage: action field missing or is not a string or data field is missing or is not an object");

			exception.printStackTrace();
		}
	}

	/***********************************************************************************************
	 *
	 * METHODS
	 *
	 **********************************************************************************************/

	private void sendStatusCallback(CobaltPluginWebContainer webContainer, String callback, String status) {
		CobaltFragment fragment = webContainer.getFragment();

		if  (fragment != null) {
			try {
				JSONObject data = new JSONObject();
				data.put(kJSStatus, status);
				fragment.sendCallback(callback, data);
			}
			catch (JSONException exception) {
				exception.printStackTrace();
			}
		}
	}

	private void sendStatusChangedCallback(String status) {
		if (listeningFragments.size() > 0) {
			try {
				JSONObject data = new JSONObject();
				data.put(kJSStatus, status);

				JSONObject message = new JSONObject();
				message.put(Cobalt.kJSType, Cobalt.JSTypePlugin);
				message.put(Cobalt.kJSPluginName, JSPluginName);
				message.put(Cobalt.kJSAction, JSActionOnNetworkChanged);
				message.put(Cobalt.kJSData, data);

				for (Iterator<WeakReference<CobaltFragment>> iterator = listeningFragments.iterator(); iterator.hasNext(); ) {
					WeakReference<CobaltFragment> fragmentReference = iterator.next();

					if (fragmentReference.get() == null)
						iterator.remove();
					else
						fragmentReference.get().sendMessage(message);
				}
			}
			catch (JSONException exception) {
				exception.printStackTrace();
			}
		}
	}

	private void startStatusMonitoring(CobaltPluginWebContainer webContainer) {
		CobaltFragment fragment = webContainer.getFragment();

		if (Cobalt.DEBUG)
			Log.d(TAG, "Fragment " + fragment + " started listening network changes");

		if (fragment != null && !containsReference(listeningFragments, fragment)) {
			listeningFragments.add(new WeakReference<>(fragment));

			if (networkChangeReceiver == null) {
				Context context = fragment.getActivity().getApplicationContext();

				if (checkNetworkChangePermission(context)) {
					networkChangeReceiver = new NetworkChangeReceiver(context, this);

					if (Cobalt.DEBUG)
						Log.d(TAG, "One fragment is listening ; starting NetworkChangeReceiver");
				}
				else if (Cobalt.DEBUG)
					Log.d(TAG, "Cannot start network monitoring: please make sure your AndroidManifest contains the CHANGE_NETWORK_STATE permission");
			}
		}
	}

	private void stopStatusMonitoring(CobaltPluginWebContainer webContainer) {
		removeReference(listeningFragments, webContainer.getFragment());

		if (Cobalt.DEBUG)
			Log.d(TAG, "Fragment " + webContainer.getFragment() + " stopped listening network status changes");

		if (listeningFragments.size() <= 0) {
			if (networkChangeReceiver != null) {
				networkChangeReceiver.remove();
				networkChangeReceiver = null;
			}

			if (Cobalt.DEBUG)
				Log.d(TAG, "No fragment listening ; shutting down NetworkChangeReceiver");
		}
	}

	private String getStatus(CobaltPluginWebContainer webContainer) {
		Activity activity = webContainer.getActivity();

		if (activity != null)
			return getStatus(activity.getApplicationContext());
		else
			return TYPE_UNKNOWN;
	}

	private String getStatus(Context context) {
		if (!checkNetworkStatePermission(context)) {
			if (Cobalt.DEBUG)
				Log.d(TAG, "Cannot get network status: please make sure your AndroidManifest contains the ACCESS_NETWORK_STATE permission");

			return TYPE_UNKNOWN;
		}

		ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo info = cm.getActiveNetworkInfo();

		if (info != null) {
			switch (info.getType())
			{
				case ConnectivityManager.TYPE_WIFI:
					return TYPE_WIFI;

				case ConnectivityManager.TYPE_MOBILE:
					return TYPE_MOBILE;

				case ConnectivityManager.TYPE_BLUETOOTH:
					return TYPE_BLUETOOTH;

				case ConnectivityManager.TYPE_ETHERNET:
					return TYPE_ETHERNET;

				case ConnectivityManager.TYPE_WIMAX:
					return TYPE_MOBILE;

				case ConnectivityManager.TYPE_MOBILE_DUN:
					return TYPE_MOBILE;

				case ConnectivityManager.TYPE_VPN:
					return TYPE_VPN;

				default:
					return TYPE_UNKNOWN;
			}
		}
		else {
			return TYPE_NONE;
		}
	}

	@Override
	public void onNetworkChange(Context context) {
		sendStatusChangedCallback(getStatus(context));
	}

	/***********************************************************************************************
	 *
	 * PERMISSIONS
	 *
	 **********************************************************************************************/

	private boolean checkPermission(Context context, String permission) {
		return ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
	}

	private boolean checkNetworkStatePermission(Context context) {
		return checkPermission(context, Manifest.permission.ACCESS_NETWORK_STATE);
	}

	private boolean checkNetworkChangePermission(Context context) {
		return checkPermission(context, Manifest.permission.CHANGE_NETWORK_STATE);
	}

	/***********************************************************************************************
	 *
	 * HELPERS
	 *
	 **********************************************************************************************/

	private static <T> boolean containsReference(List<WeakReference<T>> list, T reference) {
		for (Iterator<WeakReference<T>> iterator = list.iterator(); iterator.hasNext(); ) {
			WeakReference<T> ref = iterator.next();

			if (ref.get() == reference) {
				return true;
			}
		}

		return false;
	}

	private static <T> int removeReference(List<WeakReference<T>> list, T reference) {
		int removed = 0;

		for (Iterator<WeakReference<T>> iterator = list.iterator(); iterator.hasNext(); ) {
			WeakReference<T> ref = iterator.next();

			if (ref.get() == reference) {
				iterator.remove();
				removed++;
			}
		}

		return removed;
	}
}
