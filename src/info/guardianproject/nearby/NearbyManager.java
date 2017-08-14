package info.guardianproject.nearby;

import java.io.File;

/**
 * Created by n8fr8 on 9/2/16.
 */
public abstract class NearbyManager {

    public abstract void setNearbyListener (NearbyListener nearbyListener);

    public abstract void findNeighbors ();

    public abstract void shareFile (File fileMedia, String mimeType);

    public abstract void receiveFile ();

    public abstract void receiveFileFromNeighbor (Neighbor neighbor);
}
