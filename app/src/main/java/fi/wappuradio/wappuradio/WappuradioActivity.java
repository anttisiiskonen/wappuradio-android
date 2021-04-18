package fi.wappuradio.wappuradio;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSource;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.NotificationUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Timer;
import java.util.TimerTask;

import okhttp3.OkHttpClient;

public class WappuradioActivity extends AppCompatActivity implements Player.EventListener {

    private static final String TAG = "WappuradioActivity";

    private SimpleExoPlayer exoPlayer;

    private final String streamUrl =
            "http://stream.wappuradio.fi/icecast/wappuradio-legacy-streamer1.opus";


    private final String nowPlayingApiUrl = "https://www.wappuradio.fi/api/nowplaying";

    private Timer nowPlayingUpdateTimer;

    private final ExoPlayer.EventListener eventListener = new ExoPlayer.EventListener() {
        @Override
        public void onPlayerError(ExoPlaybackException error) {
            switch (error.type) {
                case ExoPlaybackException.TYPE_REMOTE:
                    Log.e(TAG, "TYPE_REMOTE exception.");
                    break;
                case ExoPlaybackException.TYPE_SOURCE:
                    Log.e(TAG, "TYPE_SOURCE: " +
                            error.getSourceException().getMessage());
                    break;
                case ExoPlaybackException.TYPE_RENDERER:
                    Log.e(TAG, "TYPE_RENDERER: " +
                            error.getRendererException().getMessage());
                    break;
                case ExoPlaybackException.TYPE_UNEXPECTED:
                    Log.e(TAG, "TYPE_UNEXPECTED: " +
                            error.getUnexpectedException().getMessage());
                    break;
            }
        }
    };

    private final String PLAYBACK_CHANNEL_ID = "fi.wappuradio.wappuradio.playback_channel";
    private final int PLAYBACK_NOTIFICATION_ID = 666;

    private final int RETRY_DELAY_MS = 1000;
    private static Context context;

    private enum WAPPURADIO_STATE {
        STOPPED,
        PAUSED,
        PLAYING
    }

    private WAPPURADIO_STATE wappuradioState = WAPPURADIO_STATE.STOPPED;
    private PlayerNotificationManager playerNotificationManager;

    private void play() {
        if (wappuradioState == WAPPURADIO_STATE.PAUSED) {
            exoPlayer.play();
        } else {
            exoPlayer.prepare();
        }
    }

    private void stop() {
        if (exoPlayer != null) {
            exoPlayer.stop();
        }
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        Button playButton = findViewById(R.id.playButton);
        if (isPlaying) {
            Log.i(TAG, "onIsPlayingChanged to true");
            playButton.setText(R.string.stop_text);
            wappuradioState = WAPPURADIO_STATE.PLAYING;
            Log.i(TAG, "Set state to PLAYING");
        } else {
            Log.i(TAG, "onIsPlayingChanged to false");
            if (exoPlayer.getPlaybackState() == Player.STATE_READY) {
                /* Player is not playing, but reports STATE_READY,
                 *  so we interpret the player being paused via notification
                 *  controls. */
                wappuradioState = WAPPURADIO_STATE.PAUSED;
                Log.i(TAG, "Set state to PAUSED");
            } else {
                wappuradioState = WAPPURADIO_STATE.STOPPED;
                Log.i(TAG, "Set state to STOPPED");
            }
            playButton.setText(R.string.play_text);
        }
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        switch(error.type) {
            case ExoPlaybackException.TYPE_REMOTE:
                Log.e(TAG, "TYPE_REMOTE exception.");
                break;
            case ExoPlaybackException.TYPE_SOURCE:
                Log.e(TAG, "TYPE_SOURCE: " +
                        error.getSourceException().getMessage());
                break;
            case ExoPlaybackException.TYPE_RENDERER:
                Log.e(TAG, "TYPE_RENDERER: " +
                        error.getRendererException().getMessage());
                break;
            case ExoPlaybackException.TYPE_UNEXPECTED:
                Log.e(TAG, "TYPE_UNEXPECTED: " +
                        error.getUnexpectedException().getMessage());
                break;
        }

        if (error.type == ExoPlaybackException.TYPE_SOURCE &&
                error.getSourceException() instanceof HttpDataSource.InvalidResponseCodeException) {
            Toast.makeText(getApplicationContext(), R.string.unable, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(getApplicationContext(), "Oops: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public static Context getWappuradioApplicationContext() {
        return context;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        context = getApplicationContext();

        prepareExoPlayerFromURL(Uri.parse(streamUrl));

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationUtil.createNotificationChannel(
                    this,
                    PLAYBACK_CHANNEL_ID,
                    R.string.notification_channel_name,
                    R.string.notification_channel_description,
                    NotificationUtil.IMPORTANCE_LOW
            );

            playerNotificationManager =
                    new PlayerNotificationManager(
                            this,
                            PLAYBACK_CHANNEL_ID,
                            PLAYBACK_NOTIFICATION_ID,
                            new DescriptionAdapter());

            playerNotificationManager.setColorized(true);
            playerNotificationManager.setColor(R.color.red);
            playerNotificationManager.setUseChronometer(false);
            playerNotificationManager.setUsePlayPauseActions(true);
            playerNotificationManager.setUseStopAction(false);
            playerNotificationManager.setPlayer(exoPlayer);
        }

        setContentView(R.layout.activity_main);

        Button playButton = findViewById(R.id.playButton);
        playButton.setOnClickListener(v -> {
            if (wappuradioState == WAPPURADIO_STATE.PLAYING) {
                Log.i(TAG, "Stop clicked.");
                stop();
            } else {
                Log.i(TAG, "Play clicked.");
                playButton.setText(R.string.wait_text);
                play();
            }
        });
        nowPlayingUpdateTimer = new Timer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Button playButton = findViewById(R.id.playButton);
        if (wappuradioState == WAPPURADIO_STATE.PLAYING) {
            playButton.setText(R.string.stop_text);
        } else {
            playButton.setText(R.string.play_text);
        }
        nowPlayingUpdateTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updateNowPlaying();
            }
        }, 0, 5000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        nowPlayingUpdateTimer.cancel();
    }

    private void prepareExoPlayerFromURL(Uri uri) {
        exoPlayer =
                new SimpleExoPlayer.Builder(this)
                        .setWakeMode(C.WAKE_MODE_NETWORK)
                        .setAudioAttributes(
                                new AudioAttributes.Builder()
                                        .setContentType(C.CONTENT_TYPE_MUSIC)
                                        .setUsage(C.USAGE_MEDIA)
                                        .build(),
                                true
                        )
                        .setMediaSourceFactory(
                                new DefaultMediaSourceFactory(new OkHttpDataSource.Factory(new OkHttpClient()))
                                        .setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy() {
                                            @Override
                                            public long getRetryDelayMsFor(LoadErrorInfo loadErrorInfo) {
                                                return RETRY_DELAY_MS;
                                            }

                                            @Override
                                            public int getMinimumLoadableRetryCount(int dataType) {
                                                return Integer.MAX_VALUE;
                                            }
                                        })
                                        .setLiveTargetOffsetMs(5000)
                        )
                        .build();
        exoPlayer.addListener(this);
        MediaItem mediaItem = new MediaItem.Builder()
                .setUri(uri)
                .build();
        exoPlayer.setMediaItem(mediaItem);
        exoPlayer.setPlayWhenReady(true);
    }

    private final int REQUEST_CODE_INTERNET = 0;
    private final int REQUEST_CODE_WAKE_LOCK = 1;

    @Override
    protected void onStart() {
        super.onStart();

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.INTERNET},
                    REQUEST_CODE_INTERNET);
        }

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WAKE_LOCK) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WAKE_LOCK},
                    REQUEST_CODE_WAKE_LOCK);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent();
            String packageName = getPackageName();
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                startActivity(intent);
            }
        }

    }

    private void updateNowPlaying() {
        RequestQueue queue = Volley.newRequestQueue(this);
        TextView nowPlayingView = findViewById(R.id.nowPlayingTextView);

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, nowPlayingApiUrl, null, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                Log.i("updateNowPlaying", "updating...");
                try {
                    String song = (String) response.get("song");
                    Log.i("updateNowPlaying", song);
                    nowPlayingView.setText(song);
                } catch (JSONException e) {
                    Log.e("updateNowPlaying", e.getMessage());
                    Toast.makeText(getApplicationContext(), "Oops: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("updateNowPlaying", error.getMessage());
                Toast.makeText(getApplicationContext(), "Oops: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        queue.add(jsonObjectRequest);
    }

}
