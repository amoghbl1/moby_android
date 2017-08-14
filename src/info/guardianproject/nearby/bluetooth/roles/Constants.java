package info.guardianproject.nearby.bluetooth.roles;

public class Constants {
    public static final int CHUNK_SIZE = 4192/2;
    public static final int HEADER_MSB = 0x10;
    public static final int HEADER_LSB = 0x55;
    public static final String NAME = "ANDROID-BTXFR";
    public static final String UUID_STRING = "00001101-0000-1000-8000-00805F9B34AC";

    public class MessageType {
        public static final int DATA_SENT_OK = 0x00;
        public static final int READY_FOR_DATA = 0x01;
        public static final int DATA_RECEIVED = 0x02;
        public static final int DATA_PROGRESS_UPDATE = 0x03;
        public static final int SENDING_DATA = 0x04;

        public static final int DIGEST_DID_NOT_MATCH = 0x50;
        public static final int COULD_NOT_CONNECT = 0x51;
        public static final int INVALID_HEADER = 0x52;
    }

}
