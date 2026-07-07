package net.kaaass.zerotierfix.service;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.preference.PreferenceManager;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;
import android.widget.Toast;

import net.kaaass.zerotierfix.R;
import net.kaaass.zerotierfix.ZerotierFixApplication;
import net.kaaass.zerotierfix.events.ManualDisconnectEvent;
import net.kaaass.zerotierfix.model.NetworkDao;
import net.kaaass.zerotierfix.ui.NetworkListActivity;
import net.kaaass.zerotierfix.util.DatabaseUtils;

import org.greenrobot.eventbus.EventBus;

public class QuickSettingsTileService extends TileService {
    private static final String TAG = "ZerotierFixTile";

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        Tile tile = getQsTile();
        if (tile == null) return;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean isRunning = prefs.getBoolean("service_running", false);

        if (isRunning) {
            EventBus.getDefault().post(new ManualDisconnectEvent());
            Intent intent = new Intent(this, ZeroTierOneService.class);
            stopService(intent);
            prefs.edit().putBoolean("service_running", false).apply();
            tile.setState(Tile.STATE_INACTIVE);
            tile.updateTile();
        } else {
            long networkId = getLastActivatedNetworkId();
            if (networkId == 0) {
                Toast.makeText(this, R.string.toast_no_network, Toast.LENGTH_SHORT).show();
                return;
            }

            Intent prepare = VpnService.prepare(this);
            if (prepare != null) {
                Intent launchIntent = new Intent(this, NetworkListActivity.class);
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivityAndCollapse(launchIntent);
                return;
            }

            Intent intent = new Intent(this, ZeroTierOneService.class);
            intent.putExtra(ZeroTierOneService.ZT1_NETWORK_ID, networkId);
            startService(intent);
            tile.setState(Tile.STATE_ACTIVE);
            tile.updateTile();
        }
    }

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        updateTile();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean isRunning = prefs.getBoolean("service_running", false);

        tile.setState(isRunning ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }

    private long getLastActivatedNetworkId() {
        DatabaseUtils.readLock.lock();
        try {
            var daoSession = ((ZerotierFixApplication) getApplication()).getDaoSession();
            daoSession.clear();
            var networks = daoSession.getNetworkDao().queryBuilder()
                    .where(NetworkDao.Properties.LastActivated.eq(true))
                    .list();
            if (networks != null && !networks.isEmpty()) {
                return networks.get(0).getNetworkId();
            }
        } finally {
            DatabaseUtils.readLock.unlock();
        }
        return 0;
    }
}
