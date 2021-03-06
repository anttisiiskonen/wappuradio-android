package fi.wappuradio.wappuradio;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSource;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.NotificationUtil;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import okhttp3.OkHttpClient;

public class WappuradioActivity extends AppCompatActivity implements Player.EventListener {

    private static final String TAG = "WappuradioActivity";

    private static SimpleExoPlayer exoPlayer;

    private final String streamUrl = "http://stream.wappuradio.fi/icecast/wappuradio-legacy-streamer1.opus";

    private final String nowPlayingApiUrl = "https://www.wappuradio.fi/api/nowplaying";
    private final String programsApiUrl ="https://www.wappuradio.fi/api/programs";
    private final int pollingRateMilliseconds = 60000;

    private List<WappuradioProgram> programs;

    private Timer nowPlayingUpdateTimer;

    private Button playButton;
    private TextView nowPlayingTextView;
    private TextView nowPerformingTextView;
    private CircularProgressIndicator bufferingIndicator;

    private DescriptionAdapter descriptionAdapter;

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
    private MediaSessionCompat mediaSession;
    private MediaSessionConnector mediaSessionConnector;

    private enum WAPPURADIO_STATE {
        STOPPED,
        PAUSED,
        PLAYING
    }

    private static WAPPURADIO_STATE wappuradioState = WAPPURADIO_STATE.STOPPED;
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
        if (isPlaying) {
            Log.i(TAG, "onIsPlayingChanged to true");
            playButton.setText(R.string.stop_text);
            bufferingIndicator.setVisibility(View.INVISIBLE);
            playButton.setVisibility(View.VISIBLE);
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
            playButton.setVisibility(View.VISIBLE);
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        playButton = findViewById(R.id.playButton);
        nowPlayingTextView = findViewById(R.id.nowPlayingTextView);
        nowPerformingTextView = findViewById(R.id.nowPerformingTextView);
        bufferingIndicator = findViewById(R.id.bufferingIndicator);

        prepareExoPlayerFromURL(Uri.parse(streamUrl));

        mediaSession = new MediaSessionCompat(this, getPackageName());
        mediaSessionConnector = new MediaSessionConnector(mediaSession);

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationUtil.createNotificationChannel(
                    this,
                    PLAYBACK_CHANNEL_ID,
                    R.string.notification_channel_name,
                    R.string.notification_channel_description,
                    NotificationUtil.IMPORTANCE_LOW
            );

            descriptionAdapter = new DescriptionAdapter();
            descriptionAdapter.setContext(this);

            playerNotificationManager =
                    new PlayerNotificationManager(
                            this,
                            PLAYBACK_CHANNEL_ID,
                            PLAYBACK_NOTIFICATION_ID,
                            descriptionAdapter);

            playerNotificationManager.setColorized(true);
            playerNotificationManager.setColor(R.color.red);
            playerNotificationManager.setUseChronometer(false);
            playerNotificationManager.setUsePlayPauseActions(true);
            playerNotificationManager.setUseStopAction(false);

            playerNotificationManager.setUseNextAction(false);
            playerNotificationManager.setUsePreviousAction(false);

            playerNotificationManager.setUseNextActionInCompactView(false);
            playerNotificationManager.setUsePreviousActionInCompactView(false);

            playerNotificationManager.setPlayer(exoPlayer);
            playerNotificationManager.setMediaSessionToken(mediaSession.getSessionToken());
        }

        if (wappuradioState == WAPPURADIO_STATE.PLAYING) {
            playButton.setText(R.string.stop_text);
        } else {
            playButton.setText(R.string.play_text);
        }

        playButton.setOnClickListener(v -> {
            if (wappuradioState == WAPPURADIO_STATE.PLAYING) {
                Log.i(TAG, "Stop clicked.");
                stop();
            } else {
                Log.i(TAG, "Play clicked.");
                playButton.setText(R.string.buffering);
                playButton.setVisibility(View.INVISIBLE);
                bufferingIndicator.setVisibility(View.VISIBLE);
                play();
            }
        });

        programs = new ArrayList<>();
        updatePrograms();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (wappuradioState == WAPPURADIO_STATE.PLAYING) {
            playButton.setText(R.string.stop_text);
        } else {
            playButton.setText(R.string.play_text);
        }

        nowPlayingUpdateTimer = new Timer();
        nowPlayingUpdateTimer.scheduleAtFixedRate(new TimerTask() {
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void run() {
                updateNowPlaying();
                updateNowPerforming();
            }
        }, 0, pollingRateMilliseconds);

        mediaSessionConnector.setPlayer(exoPlayer);
        mediaSession.setActive(true);
    }

    private void prepareExoPlayerFromURL(Uri uri) {
        if (exoPlayer == null) {
            exoPlayer = new SimpleExoPlayer.Builder(this)
                    .setWakeMode(C.WAKE_MODE_NETWORK)
                    .setAudioAttributes(new AudioAttributes.Builder()
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
            MediaItem mediaItem = new MediaItem.Builder()
                    .setUri(uri)
                    .build();

            exoPlayer.setMediaItem(mediaItem);
            exoPlayer.setPlayWhenReady(true);
        }
        exoPlayer.addListener(this);
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

    @Override
    protected void onPause() {
        super.onPause();
        if(nowPlayingUpdateTimer != null) {
            nowPlayingUpdateTimer.cancel();
            nowPlayingUpdateTimer.purge();
            nowPlayingUpdateTimer = null;
        }

        mediaSessionConnector.setPlayer(null);
        mediaSession.setActive(false);
    }

    private void updateNowPlaying() {
        RequestQueue queue = Volley.newRequestQueue(this);
        JsonObjectRequest getNowPlayingRequest = new JsonObjectRequest(Request.Method.GET, nowPlayingApiUrl, null, response -> {
            try {
                String song = (String) response.get("song");
                Log.i("updateNowPlaying", song);
                nowPlayingTextView.setText(song);
                descriptionAdapter.setNowPlaying(song);
            } catch (JSONException e) {
                Log.e("updateNowPlaying", e.getMessage());
                Toast.makeText(getApplicationContext(), "Oops: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }, error -> {
            Log.e("updateNowPlaying", error.getMessage());
            Toast.makeText(getApplicationContext(), "Oops: " + error.getMessage(), Toast.LENGTH_LONG).show();
        });
        queue.add(getNowPlayingRequest);
    }

    private void updatePrograms() {
        Log.i("getPrograms", "Fetching programs...");
        RequestQueue queue = Volley.newRequestQueue(this);

        JsonArrayRequest getProgramsRequest = new JsonArrayRequest(Request.Method.GET, programsApiUrl, null, new Response.Listener<JSONArray>() {
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onResponse(JSONArray response) {
                try {
                    for (int i = 0; i < response.length(); i++) {
                        JSONObject object = response.getJSONObject(i);
                        Instant startTime = parseDatetime(object.getString("start"));
                        Instant endTime = parseDatetime(object.getString("end"));
                        String title = object.getString("title");
                        programs.add(new WappuradioProgram(startTime, endTime, title));
                    }
                    updateNowPerforming();
                    Log.i("getPrograms", "Programs fetched successfully");
                } catch (JSONException e) {
                    Log.e("getPrograms", "Unable to fetch programs: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }, error -> {
            Log.e("getPrograms", error.getMessage());
            Toast.makeText(getApplicationContext(), "Oops: " + error.getMessage(), Toast.LENGTH_LONG).show();
        });
        queue.add(getProgramsRequest);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void updateNowPerforming() {
        Instant now = Instant.now();
        WappuradioProgram currentProgram = programs.stream().filter(program -> program.getStartTime().isBefore(now) && program.getEndTime().isAfter(now)).findFirst().orElse(null);
        if (currentProgram != null) {
            descriptionAdapter.setNowPerforming(currentProgram.getTitle());

            this.runOnUiThread(() -> nowPerformingTextView.setText(currentProgram.getTitle()));
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private Instant parseDatetime(String datetime) {
        DateTimeFormatter timeFormatter = DateTimeFormatter.ISO_DATE_TIME;
        OffsetDateTime offsetDateTime = OffsetDateTime.parse(datetime);
        return Instant.from(offsetDateTime);
    }
}
