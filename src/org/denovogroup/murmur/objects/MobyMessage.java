package org.denovogroup.murmur.objects;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by amoghbl1 on 04/09/17.
 */

public class MobyMessage {

    public static final String TIMESTAMP   = "timestamp";
    public static final String SOURCE      = "source";
    public static final String DESTINATION = "destination";
    public static final String PAYLOAD     = "payload";

    // Message timestamp.
    private final long timestamp;

    // Message source identifier, a string identifier which only the destination can verify to identify who sent this message.
    private final String source;

    // Message destination identifier, a string identifier which only the destination can use to verify to make sure the message is meant for it.
    private final String destination;

    // Message payload, an encrypted string that only the destination can decrypt, given that it knows who sent it.
    private final String payload;

    public MobyMessage(long timestamp, String source, String destination, String payload) {
        this.timestamp   = timestamp;
        this.source      = source;
        this.destination = destination;
        this.payload     = payload;
    }

    public static MobyMessage fromJSON(JSONObject json) {
        return new MobyMessage(
                json.optLong(TIMESTAMP, -1L),
                json.optString(SOURCE, null),
                json.optString(DESTINATION, null),
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
            result.put(SOURCE, this.source);
            result.put(DESTINATION, this.destination);
            result.put(PAYLOAD, this.payload);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return result;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getSource() {
        return source;
    }

    public String getDestination() {
        return destination;
    }

    public String getPayload() {
        return payload;
    }

}
