package fi.wappuradio.wappuradio;

import android.Manifest;
import android.app.ActionBar;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.audio.MediaCodecAudioRenderer;
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSource;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.HttpDataSource;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import okhttp3.OkHttpClient;

public class WappuradioActivity extends AppCompatActivity {

    private static final String TAG = "WappuradioActivity";

    private SimpleExoPlayer exoPlayer;

    private final String streamUrl = "https://stream.wappuradio.fi/wappuradio.opus";

    private final ExoPlayer.EventListener eventListener = new ExoPlayer.EventListener() {
        @Override
        public void onPlayerError(ExoPlaybackException error) {
            switch(error.type) {
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
                Toast.makeText(getApplicationContext(), "Ei pysty, sori!", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(getApplicationContext(), "Oops: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
            stop();
        }
    };

    private boolean isPlaying = false;

    private void play() {
        prepareExoPlayerFromURL(Uri.parse(streamUrl));
        Button playButton = (Button)findViewById(R.id.playButton);
        playButton.setText(R.string.buffering_text);
        exoPlayer.prepare();
        playButton.setText(R.string.prepared_text);
        exoPlayer.play();
        playButton.setText(R.string.stop_text);
        isPlaying = true;
    }

    private void stop() {
        if (exoPlayer != null) {
            exoPlayer.stop();
            exoPlayer.release();
            exoPlayer = null;
        }
        Button playButton = (Button)findViewById(R.id.playButton);
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

        Button playButton = (Button)findViewById(R.id.playButton);
        playButton.setOnClickListener(v -> {
            if (isPlaying) {
                stop();
            } else {
                play();
            }
        });
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

    }
}
