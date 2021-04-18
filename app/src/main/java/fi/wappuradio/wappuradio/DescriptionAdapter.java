package fi.wappuradio.wappuradio;

import android.app.PendingIntent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;

public class DescriptionAdapter implements
        PlayerNotificationManager.MediaDescriptionAdapter {

    private final WappuradioActivity wappuradioActivity;

    public DescriptionAdapter(WappuradioActivity wappuradioActivity) {
        this.wappuradioActivity = wappuradioActivity;
    }

    @Override
    public String getCurrentContentTitle(Player player) {
        return null;
    }

    @Nullable
    @Override
    public String getCurrentContentText(Player player) {
        return null;
    }

    @Nullable
    @Override
    public Bitmap getCurrentLargeIcon(Player player,
                                      PlayerNotificationManager.BitmapCallback callback) {
        return BitmapFactory.decodeResource(
                wappuradioActivity.getResources(),
                R.drawable.tower);
    }

    @Nullable
    @Override
    public PendingIntent createCurrentContentIntent(Player player) {
        return null;
    }

}
