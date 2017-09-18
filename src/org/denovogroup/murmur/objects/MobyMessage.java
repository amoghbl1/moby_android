package org.denovogroup.murmur.objects;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by amoghbl1 on 04/09/17.
 */

public class MobyMessage {

    public static final String TIMESTAMP   = "timestamp";
    public static final String TTL         = "time_to_live";
    public static final String MOBYTAG     = "moby_tag";
    public static final String PAYLOAD     = "payload";

    // Message timestamp.
    private final long timestamp;

    // Message TTL.
    private final long timeToLive;

    // Message moby tag.
    private final String mobyTag;

    // Message payload, an encrypted string that only the destination can decrypt, given that it knows who sent it.
    private final String payload;

    public MobyMessage(long timestamp, long timeToLive, String mobyTag, String payload) {
        this.timestamp   = timestamp;
        this.timeToLive  = timeToLive;
        this.mobyTag     = mobyTag;
        this.payload     = payload;
    }

    public static MobyMessage fromJSON(JSONObject json) {
        return new MobyMessage(
                json.optLong(TIMESTAMP, -1L),
                json.optLong(TTL, -1L),
                json.optString(MOBYTAG, null),
                json.optString(PAYLOAD, null)
        );
    }

    public static MobyMessage fromJSON(String jsonString){
        JSONObject json;
        try {
            json = new JSONObject(jsonString);
        } catch (JSONException e) {
            e.printStackTrace();
            json = new JSONObject();
        }
        return fromJSON(json);
    }

    public JSONObject toJSON(){
        JSONObject result = new JSONObject();
        try {
            result.put(TIMESTAMP, this.timestamp);
            result.put(TTL, this.timeToLive);
            result.put(MOBYTAG, this.mobyTag);
            result.put(PAYLOAD, this.payload);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return result;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getTimeToLive() {
        return timeToLive;
    }

    public String getMobyTag() {
        return mobyTag;
    }

    public String getPayload() {
        return payload;
    }

}
