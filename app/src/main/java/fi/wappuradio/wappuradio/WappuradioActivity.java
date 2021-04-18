package fi.wappuradio.wappuradio;

import android.Manifest;
import android.app.ActionBar;
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
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSource;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Timer;
import java.util.TimerTask;

import okhttp3.OkHttpClient;

public class WappuradioActivity extends AppCompatActivity {

    private static final String TAG = "WappuradioActivity";

    private SimpleExoPlayer exoPlayer;

    private final String streamUrl =
            "http://stream.wappuradio.fi/icecast/wappuradio-legacy-streamer1.opus";

    private final String nowPlayingApiUrl = "https://www.wappuradio.fi/api/nowplaying";

    private Timer nowPlayingUpdateTimer;

    private final ExoPlayer.EventListener eventListener = new ExoPlayer.EventListener() {
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
            stop();
        }
    };

    private boolean isPlaying = false;

    private void play() {
        prepareExoPlayerFromURL(Uri.parse(streamUrl));
        exoPlayer.setPlayWhenReady(true);
        exoPlayer.prepare();
        Button playButton = findViewById(R.id.playButton);
        playButton.setText(R.string.stop_text);
        isPlaying = true;
    }

    private void stop() {
        if (exoPlayer != null) {
            exoPlayer.stop();
            exoPlayer.release();
            exoPlayer = null;
        }
        Button playButton = findViewById(R.id.playButton);
        playButton.setText(R.string.play_text);
        isPlaying = false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }

        setContentView(R.layout.activity_main);

        Button playButton = findViewById(R.id.playButton);
        playButton.setOnClickListener(v -> {
            if (isPlaying) {
                Log.i(TAG, "Stop clicked.");
                stop();
            } else {
                Log.i(TAG, "Play clicked.");
                play();
            }
        });
        nowPlayingUpdateTimer = new Timer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Button playButton = findViewById(R.id.playButton);
        if (isPlaying) {
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
                                        .setLiveTargetOffsetMs(5000)
                        )
                        .build();
        MediaItem mediaItem = new MediaItem.Builder()
                .setUri(uri)
                .build();

        exoPlayer.addListener(eventListener);
        exoPlayer.setMediaItem(mediaItem);
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
