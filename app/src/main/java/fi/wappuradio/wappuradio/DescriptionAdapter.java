package fi.wappuradio.wappuradio;

import android.app.PendingIntent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;

public class DescriptionAdapter implements PlayerNotificationManager.MediaDescriptionAdapter {

    private String nowPerforming;
    private String nowPlaying;

    public void setNowPerforming(String performing) {
        nowPerforming = performing;
    }

    public void setNowPlaying(String playing) {
        nowPlaying = playing;
    }

    @Override
    public String getCurrentContentTitle(Player player) {
        return nowPerforming;
    }

    @Nullable
    @Override
    public String getCurrentContentText(Player player) {
        return nowPlaying;
    }

    @Nullable
    @Override
    public Bitmap getCurrentLargeIcon(Player player,
                                      PlayerNotificationManager.BitmapCallback callback) {
        return BitmapFactory.decodeResource(
                WappuradioActivity.getWappuradioApplicationContext().getResources(),
                R.drawable.tower);
    }

    @Nullable
    @Override
    public PendingIntent createCurrentContentIntent(Player player) {
        return null;
    }

}
