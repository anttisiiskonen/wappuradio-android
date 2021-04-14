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

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import okhttp3.OkHttpClient;

public class WappuradioActivity extends AppCompatActivity {

    private static final String TAG = "WappuradioActivity";

    private SimpleExoPlayer exoPlayer;

    // private final String streamUrl = "https://www.siiskoset.fi/files/trololo.mp3";
    private final String streamUrl = "http://stream.wappuradio.fi:8000/wappuradio.opus";

    private final ExoPlayer.EventListener eventListener = new ExoPlayer.EventListener() {
        @Override
        public void onPlayerError(ExoPlaybackException error) {
            Log.i(TAG, "onPlaybackError: " + error.getMessage());
            Toast.makeText(getApplicationContext(), "onPlaybackError: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    };

    private boolean isPlaying = false;

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
                exoPlayer.stop();
                exoPlayer.release();
                exoPlayer = null;
                playButton.setText(R.string.play_text);
            } else {
                prepareExoPlayerFromURL(Uri.parse(streamUrl));
                playButton.setText(R.string.buffering_text);
                exoPlayer.prepare();
                playButton.setText(R.string.prepared_text);
                exoPlayer.play();
                playButton.setText(R.string.stop_text);
            }
            isPlaying = !isPlaying;
        });

    }

    private void prepareExoPlayerFromURL(Uri uri) {
        exoPlayer =
                new SimpleExoPlayer.Builder(this)
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

    @Override
    protected void onStart() {
        super.onStart();

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.INTERNET},
                    REQUEST_CODE_INTERNET);
        }

    }
}
