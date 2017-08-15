package info.guardianproject.nearby;

/**
 * Created by n8fr8 on 9/1/16.
 */
public class Neighbor {

    public String mId;
    public String mName;
    public int mType;

    public final static int TYPE_BLUETOOTH = 1;
    public final static int TYPE_WIFI_NSD = 2;
    public final static int TYPE_WIFI_P2P = 1;

    public Neighbor (String id, String name, int type)
    {
        mId = id;
        mName = name;
        mType = type;
    }

}
