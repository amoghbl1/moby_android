package info.guardianproject.nearby;

import java.io.File;

/**
 * Created by n8fr8 on 9/1/16.
 */
public interface NearbyListener {

    public void transferComplete (Neighbor neighbor, NearbyMedia media);

    public void foundNeighbor (Neighbor neighbor);

    public void transferProgress (Neighbor neighbor, File fileMedia, String title, String mimeType, long transferred, long total);

    public void noNeighborsFound ();
}
