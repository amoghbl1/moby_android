package info.guardianproject.nearby;

import java.io.File;

import info.guardianproject.nearby.bluetooth.roles.Utils;

/**
 * Created by n8fr8 on 9/12/16.
 */
public class NearbyMedia {

    public String mTitle;
    public String mMimeType;
    public String mMetadataJson;
    public File mFileMedia;
    public byte[] mDigest;
    public long mLength;

    public void setFileMedia (File fileMedia)
    {
        mFileMedia = fileMedia;
        mDigest = Utils.getDigest(mFileMedia);
    }

    public void setTitle (String title)
    {
        mTitle = title;
    }

    public String getTitle() {
        return mTitle;
    }

    public String getmMimeType() {
        return mMimeType;
    }

    public void setMimeType(String mimeType) {
        this.mMimeType = mimeType;
    }

    public String getMetadataJson() {
        return mMetadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.mMetadataJson = metadataJson;
    }

    public File getFileMedia() {
        return mFileMedia;
    }

    public byte[] getDigest() {
        return mDigest;
    }

    public void setDigest(byte[] digest) {
        this.mDigest = digest;
    }

    public long getLength() {
        return mLength;
    }

    public void setLength(long fileMediaLength) {
        this.mLength = fileMediaLength;
    }
}
