package de.tum.in.cm.vodmeasurementtool;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DefaultAllocator;

/**
 * Behaves like DefaultLoadControl except the buffering parameters are changed
 */
public class CustomLoadControl implements LoadControl {
    private static final int MIN_BUFFER_MS = DefaultLoadControl.DEFAULT_MIN_BUFFER_MS;
    private static final int MAX_BUFFER_MS = 60_000; //TODO 120_000; //2 min to limit download volume on mobile device
    private static final int BUFFER_FOR_PLAYBACK_MS = 2000;  //2 sec
    private static final int BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 1000; //1 sec
    private static final int TARGET_BUFFER_BYTES = DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES;
    private static final boolean PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS = DefaultLoadControl.DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS;
    private static final int CUSTOM_BUFFER_SEGMENT_SIZE = 32 * 1024; // more precise measurements
    private boolean downloadSpeedEstimated = false;

    private DefaultLoadControl dlc;

    protected CustomLoadControl() {

        DefaultLoadControl.Builder builder = new DefaultLoadControl.Builder();
        builder.setBufferDurationsMs(MIN_BUFFER_MS, MAX_BUFFER_MS, BUFFER_FOR_PLAYBACK_MS, BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS);
        builder.setAllocator(new DefaultAllocator(false, CUSTOM_BUFFER_SEGMENT_SIZE));
        builder.setPrioritizeTimeOverSizeThresholds(PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS);
        builder.setTargetBufferBytes(TARGET_BUFFER_BYTES);
        dlc = builder.createDefaultLoadControl();
        //dlc = new DefaultLoadControl(new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE), MIN_BUFFER_MS, MAX_BUFFER_MS, BUFFER_FOR_PLAYBACK_MS, BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS, TARGET_BUFFER_BYTES, PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS);

    }

    @Override
    public void onPrepared() {
        dlc.onPrepared();
    }

    @Override
    public void onTracksSelected(Renderer[] renderers, TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        dlc.onTracksSelected(renderers, trackGroups, trackSelections);
    }

    @Override
    public void onStopped() {
        dlc.onStopped();
    }

    @Override
    public void onReleased() {
        dlc.onReleased();
    }

    @Override
    public Allocator getAllocator() {
        return dlc.getAllocator();
    }

    /**
     * is modified so that played buffer content isn't de-allocated otherwise bitrate estimation would be incorrect
     *
     * @return
     */
    @Override
    public long getBackBufferDurationUs() {
        //return dlc.getBackBufferDurationUs();
        return Long.MAX_VALUE;
    }

    @Override
    public boolean retainBackBufferFromKeyframe() {
        return dlc.retainBackBufferFromKeyframe();
    }

    @Override
    public boolean shouldContinueLoading(long bufferedDurationUs, float playbackSpeed) {
        boolean shouldContinueLoading = dlc.shouldContinueLoading(bufferedDurationUs, playbackSpeed);
        if (!shouldContinueLoading && !downloadSpeedEstimated) {
            downloadSpeedEstimated = true;
        }
        return shouldContinueLoading;
        // return true; //if endless buffering is needed
    }

    @Override
    public boolean shouldStartPlayback(long bufferedDurationUs, float playbackSpeed, boolean rebuffering) {
        return dlc.shouldStartPlayback(bufferedDurationUs, playbackSpeed, rebuffering);
    }
}
