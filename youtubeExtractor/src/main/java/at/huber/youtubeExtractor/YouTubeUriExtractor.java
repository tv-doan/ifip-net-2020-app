package at.huber.youtubeExtractor;

import android.content.Context;
import android.util.SparseArray;

@Deprecated
public abstract class YouTubeUriExtractor extends YouTubeExtractor {

    public YouTubeUriExtractor(Context con) {
        super(con);
    }

    @Override
    protected void onExtractionComplete(YoutubeMediaUrlInfo ytMediaUrlInfo, VideoMeta videoMeta) {
        onUrisAvailable(videoMeta.getVideoId(), videoMeta.getTitle(), ytMediaUrlInfo);
    }

    public abstract void onUrisAvailable(String videoId, String videoTitle, YoutubeMediaUrlInfo ytMediaUrlInfo);
}
