package io.kristal.networkstatusplugin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;

public class NetworkChangeReceiver extends BroadcastReceiver
{
	private final NetworkChangeListener listener;
	private final Context context;

	public NetworkChangeReceiver(Context context, NetworkChangeListener listener)
	{
		this.listener = listener;
		this.context = context;

		context.registerReceiver(this, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
	}

	public void remove() {
		context.unregisterReceiver(this);
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		if (this.listener != null)
			this.listener.onNetworkChange(context);
	}

	public interface NetworkChangeListener
	{
		void onNetworkChange(Context context);
	}
}
