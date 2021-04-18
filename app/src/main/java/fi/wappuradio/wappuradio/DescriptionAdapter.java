package fi.wappuradio.wappuradio;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;
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

    @NonNull
    @Override
    public String getCurrentContentTitle(@NonNull Player player) {
        return nowPerforming;
    }

    @Nullable
    @Override
    public String getCurrentContentText(@NonNull Player player) {
        return nowPlaying;
    }

    @Nullable
    @Override
    public Bitmap getCurrentLargeIcon(@NonNull Player player,
                                      @NonNull PlayerNotificationManager.BitmapCallback callback) {
        return BitmapFactory.decodeResource(
                WappuradioActivity.getWappuradioApplicationContext().getResources(),
                R.drawable.tower);
    }

    @Nullable
    @Override
    public PendingIntent createCurrentContentIntent(@NonNull Player player) {
        Intent intent = new Intent(
                WappuradioActivity.getWappuradioApplicationContext(),
                WappuradioActivity.class);
        return PendingIntent.getActivity
                (WappuradioActivity.getWappuradioApplicationContext(), 0, intent, 0);
    }

}
