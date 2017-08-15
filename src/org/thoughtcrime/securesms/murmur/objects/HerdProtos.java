// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: Herd.proto

package org.thoughtcrime.securesms.murmur.objects;

public final class HerdProtos {
  private HerdProtos() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistry registry) {
  }
  public interface HandshakeMessageOrBuilder
      extends com.google.protobuf.MessageOrBuilder {

    // optional string publicDevieID = 1;
    /**
     * <code>optional string publicDevieID = 1;</code>
     */
    boolean hasPublicDevieID();
    /**
     * <code>optional string publicDevieID = 1;</code>
     */
    java.lang.String getPublicDevieID();
    /**
     * <code>optional string publicDevieID = 1;</code>
     */
    com.google.protobuf.ByteString
        getPublicDevieIDBytes();
  }
  /**
   * Protobuf type {@code HandshakeMessage}
   */
  public static final class HandshakeMessage extends
      com.google.protobuf.GeneratedMessage
      implements HandshakeMessageOrBuilder {
    // Use HandshakeMessage.newBuilder() to construct.
    private HandshakeMessage(com.google.protobuf.GeneratedMessage.Builder<?> builder) {
      super(builder);
      this.unknownFields = builder.getUnknownFields();
    }
    private HandshakeMessage(boolean noInit) { this.unknownFields = com.google.protobuf.UnknownFieldSet.getDefaultInstance(); }

    private static final HandshakeMessage defaultInstance;
    public static HandshakeMessage getDefaultInstance() {
      return defaultInstance;
    }

    public HandshakeMessage getDefaultInstanceForType() {
      return defaultInstance;
    }

    private final com.google.protobuf.UnknownFieldSet unknownFields;
    @java.lang.Override
    public final com.google.protobuf.UnknownFieldSet
        getUnknownFields() {
      return this.unknownFields;
    }
    private HandshakeMessage(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      initFields();
      int mutable_bitField0_ = 0;
      com.google.protobuf.UnknownFieldSet.Builder unknownFields =
          com.google.protobuf.UnknownFieldSet.newBuilder();
      try {
        boolean done = false;
        while (!done) {
          int tag = input.readTag();
          switch (tag) {
            case 0:
              done = true;
              break;
            default: {
              if (!parseUnknownField(input, unknownFields,
                                     extensionRegistry, tag)) {
                done = true;
              }
              break;
            }
            case 10: {
              bitField0_ |= 0x00000001;
              publicDevieID_ = input.readBytes();
              break;
            }
          }
        }
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        throw e.setUnfinishedMessage(this);
      } catch (java.io.IOException e) {
        throw new com.google.protobuf.InvalidProtocolBufferException(
            e.getMessage()).setUnfinishedMessage(this);
      } finally {
        this.unknownFields = unknownFields.build();
        makeExtensionsImmutable();
      }
    }
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return org.thoughtcrime.securesms.murmur.objects.HerdProtos.internal_static_HandshakeMessage_descriptor;
    }

    protected com.google.protobuf.GeneratedMessage.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return org.thoughtcrime.securesms.murmur.objects.HerdProtos.internal_static_HandshakeMessage_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              org.thoughtcrime.securesms.murmur.objects.HerdProtos.HandshakeMessage.class, org.thoughtcrime.securesms.murmur.objects.HerdProtos.HandshakeMessage.Builder.class);
    }

    public static com.google.protobuf.Parser<HandshakeMessage> PARSER =
        new com.google.protobuf.AbstractParser<HandshakeMessage>() {
      public HandshakeMessage parsePartialFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws com.google.protobuf.InvalidProtocolBufferException {
        return new HandshakeMessage(input, extensionRegistry);
      }
    };

    @java.lang.Override
    public com.google.protobuf.Parser<HandshakeMessage> getParserForType() {
      return PARSER;
    }

    private int bitField0_;
    // optional string publicDevieID = 1;
    public static final int PUBLICDEVIEID_FIELD_NUMBER = 1;
    private java.lang.Object publicDevieID_;
    /**
     * <code>optional string publicDevieID = 1;</code>
     */
    public boolean hasPublicDevieID() {
      return ((bitField0_ & 0x00000001) == 0x00000001);
    }
    /**
     * <code>optional string publicDevieID = 1;</code>
     */
    public java.lang.String getPublicDevieID() {
      java.lang.Object ref = publicDevieID_;
      if (ref instanceof java.lang.String) {
        return (java.lang.String) ref;
      } else {
        com.google.protobuf.ByteString bs = 
            (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        if (bs.isValidUtf8()) {
          publicDevieID_ = s;
        }
        return s;
      }
    }
    /**
     * <code>optional string publicDevieID = 1;</code>
     */
    public com.google.protobuf.ByteString
        getPublicDevieIDBytes() {
      java.lang.Object ref = publicDevieID_;
      if (ref instanceof java.lang.String) {
        com.google.protobuf.ByteString b = 
            com.google.protobuf.ByteString.copyFromUtf8(
                (java.lang.String) ref);
        publicDevieID_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }

    private void initFields() {
      publicDevieID_ = "";
    }
    private byte memoizedIsInitialized = -1;
    public final boolean isInitialized() {
      byte isInitialized = memoizedIsInitialized;
      if (isInitialized != -1) return isInitialized == 1;

      memoizedIsInitialized = 1;
      return true;
    }

    public void writeTo(com.google.protobuf.CodedOutputStream output)
                        throws java.io.IOException {
      getSerializedSize();
      if (((bitField0_ & 0x00000001) == 0x00000001)) {
        output.writeBytes(1, getPublicDevieIDBytes());
      }
      getUnknownFields().writeTo(output);
    }

    private int memoizedSerializedSize = -1;
    public int getSerializedSize() {
      int size = memoizedSerializedSize;
      if (size != -1) return size;

      size = 0;
      if (((bitField0_ & 0x00000001) == 0x00000001)) {
        size += com.google.protobuf.CodedOutputStream
          .computeBytesSize(1, getPublicDevieIDBytes());
      }
      size += getUnknownFields().getSerializedSize();
      memoizedSerializedSize = size;
      return size;
    }

    private static final long serialVersionUID = 0L;
    @java.lang.Override
    protected java.lang.Object writeReplace()
        throws java.io.ObjectStreamException {
      return super.writeReplace();
    }

    public static org.thoughtcrime.securesms.murmur.objects.HerdProtos.HandshakeMessage parseFrom(
        com.google.protobuf.ByteString data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static org.thoughtcrime.securesms.murmur.objects.HerdProtos.HandshakeMessage parseFrom(
        com.google.protobuf.ByteString data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static org.thoughtcrime.securesms.murmur.objects.HerdProtos.HandshakeMessage parseFrom(byte[] data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static org.thoughtcrime.securesms.murmur.objects.HerdProtos.HandshakeMessage parseFrom(
        byte[] data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static org.thoughtcrime.securesms.murmur.objects.HerdProtos.HandshakeMessage parseFrom(java.io.InputStream input)
        throws java.io.IOException {
      return PARSER.parseFrom(input);
    }
    public static org.thoughtcrime.securesms.murmur.objects.HerdProtos.HandshakeMessage parseFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return PARSER.parseFrom(input, extensionRegistry);
    }
    public static org.thoughtcrime.securesms.murmur.objects.HerdProtos.HandshakeMessage parseDelimitedFrom(java.io.InputStream input)
        throws java.io.IOException {
      return PARSER.parseDelimitedFrom(input);
    }
    public static org.thoughtcrime.securesms.murmur.objects.HerdProtos.HandshakeMessage parseDelimitedFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return PARSER.parseDelimitedFrom(input, extensionRegistry);
    }
    public static org.thoughtcrime.securesms.murmur.objects.HerdProtos.HandshakeMessage parseFrom(
        com.google.protobuf.CodedInputStream input)
        throws java.io.IOException {
      return PARSER.parseFrom(input);
    }
    public static org.thoughtcrime.securesms.murmur.objects.HerdProtos.HandshakeMessage parseFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return PARSER.parseFrom(input, extensionRegistry);
    }

    public static Builder newBuilder() { return Builder.create(); }
    public Builder newBuilderForType() { return newBuilder(); }
    public static Builder newBuilder(org.thoughtcrime.securesms.murmur.objects.HerdProtos.HandshakeMessage prototype) {
      return newBuilder().mergeFrom(prototype);
    }
    public Builder toBuilder() { return newBuilder(this); }

    @java.lang.Override
    protected Builder newBuilderForType(
        com.google.protobuf.GeneratedMessage.BuilderParent parent) {
      Builder builder = new Builder(parent);
      return builder;
    }
    /**
     * Protobuf type {@code HandshakeMessage}
     */
    public static final class Builder extends
        com.google.protobuf.GeneratedMessage.Builder<Builder>
       implements org.thoughtcrime.securesms.murmur.objects.HerdProtos.HandshakeMessageOrBuilder {
      public static final com.google.protobuf.Descriptors.Descriptor
          getDescriptor() {
        return org.thoughtcrime.securesms.murmur.objects.HerdProtos.internal_static_HandshakeMessage_descriptor;
      }

      protected com.google.protobuf.GeneratedMessage.FieldAccessorTable
          internalGetFieldAccessorTable() {
        return org.thoughtcrime.securesms.murmur.objects.HerdProtos.internal_static_HandshakeMessage_fieldAccessorTable
            .ensureFieldAccessorsInitialized(
                org.thoughtcrime.securesms.murmur.objects.HerdProtos.HandshakeMessage.class, org.thoughtcrime.securesms.murmur.objects.HerdProtos.HandshakeMessage.Builder.class);
      }

      // Construct using org.thoughtcrime.securesms.murmur.objects.HerdProtos.HandshakeMessage.newBuilder()
      private Builder() {
        maybeForceBuilderInitialization();
      }

      private Builder(
          com.google.protobuf.GeneratedMessage.BuilderParent parent) {
        super(parent);
        maybeForceBuilderInitialization();
      }
      private void maybeForceBuilderInitialization() {
        if (com.google.protobuf.GeneratedMessage.alwaysUseFieldBuilders) {
        }
      }
      private static Builder create() {
        return new Builder();
      }

      public Builder clear() {
        super.clear();
        publicDevieID_ = "";
        bitField0_ = (bitField0_ & ~0x00000001);
        return this;
      }

      public Builder clone() {
        return create().mergeFrom(buildPartial());
      }

      public com.google.protobuf.Descriptors.Descriptor
          getDescriptorForType() {
        return org.thoughtcrime.securesms.murmur.objects.HerdProtos.internal_static_HandshakeMessage_descriptor;
      }

      public org.thoughtcrime.securesms.murmur.objects.HerdProtos.HandshakeMessage getDefaultInstanceForType() {
        return org.thoughtcrime.securesms.murmur.objects.HerdProtos.HandshakeMessage.getDefaultInstance();
      }

      public org.thoughtcrime.securesms.murmur.objects.HerdProtos.HandshakeMessage build() {
        org.thoughtcrime.securesms.murmur.objects.HerdProtos.HandshakeMessage result = buildPartial();
        if (!result.isInitialized()) {
          throw newUninitializedMessageException(result);
        }
        return result;
      }

      public org.thoughtcrime.securesms.murmur.objects.HerdProtos.HandshakeMessage buildPartial() {
        org.thoughtcrime.securesms.murmur.objects.HerdProtos.HandshakeMessage result = new org.thoughtcrime.securesms.murmur.objects.HerdProtos.HandshakeMessage(this);
        int from_bitField0_ = bitField0_;
        int to_bitField0_ = 0;
        if (((from_bitField0_ & 0x00000001) == 0x00000001)) {
          to_bitField0_ |= 0x00000001;
        }
        result.publicDevieID_ = publicDevieID_;
        result.bitField0_ = to_bitField0_;
        onBuilt();
        return result;
      }

      public Builder mergeFrom(com.google.protobuf.Message other) {
        if (other instanceof org.thoughtcrime.securesms.murmur.objects.HerdProtos.HandshakeMessage) {
          return mergeFrom((org.thoughtcrime.securesms.murmur.objects.HerdProtos.HandshakeMessage)other);
        } else {
          super.mergeFrom(other);
          return this;
        }
      }

      public Builder mergeFrom(org.thoughtcrime.securesms.murmur.objects.HerdProtos.HandshakeMessage other) {
        if (other == org.thoughtcrime.securesms.murmur.objects.HerdProtos.HandshakeMessage.getDefaultInstance()) return this;
        if (other.hasPublicDevieID()) {
          bitField0_ |= 0x00000001;
          publicDevieID_ = other.publicDevieID_;
          onChanged();
        }
        this.mergeUnknownFields(other.getUnknownFields());
        return this;
      }

      public final boolean isInitialized() {
        return true;
      }

      public Builder mergeFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws java.io.IOException {
        org.thoughtcrime.securesms.murmur.objects.HerdProtos.HandshakeMessage parsedMessage = null;
        try {
          parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
          parsedMessage = (org.thoughtcrime.securesms.murmur.objects.HerdProtos.HandshakeMessage) e.getUnfinishedMessage();
          throw e;
        } finally {
          if (parsedMessage != null) {
            mergeFrom(parsedMessage);
          }
        }
        return this;
      }
      private int bitField0_;

      // optional string publicDevieID = 1;
      private java.lang.Object publicDevieID_ = "";
      /**
       * <code>optional string publicDevieID = 1;</code>
       */
      public boolean hasPublicDevieID() {
        return ((bitField0_ & 0x00000001) == 0x00000001);
      }
      /**
       * <code>optional string publicDevieID = 1;</code>
       */
      public java.lang.String getPublicDevieID() {
        java.lang.Object ref = publicDevieID_;
        if (!(ref instanceof java.lang.String)) {
          java.lang.String s = ((com.google.protobuf.ByteString) ref)
              .toStringUtf8();
          publicDevieID_ = s;
          return s;
        } else {
          return (java.lang.String) ref;
        }
      }
      /**
       * <code>optional string publicDevieID = 1;</code>
       */
      public com.google.protobuf.ByteString
          getPublicDevieIDBytes() {
        java.lang.Object ref = publicDevieID_;
        if (ref instanceof String) {
          com.google.protobuf.ByteString b = 
              com.google.protobuf.ByteString.copyFromUtf8(
                  (java.lang.String) ref);
          publicDevieID_ = b;
          return b;
        } else {
          return (com.google.protobuf.ByteString) ref;
        }
      }
      /**
       * <code>optional string publicDevieID = 1;</code>
       */
      public Builder setPublicDevieID(
          java.lang.String value) {
        if (value == null) {
    throw new NullPointerException();
  }
  bitField0_ |= 0x00000001;
        publicDevieID_ = value;
        onChanged();
        return this;
      }
      /**
       * <code>optional string publicDevieID = 1;</code>
       */
      public Builder clearPublicDevieID() {
        bitField0_ = (bitField0_ & ~0x00000001);
        publicDevieID_ = getDefaultInstance().getPublicDevieID();
        onChanged();
        return this;
      }
      /**
       * <code>optional string publicDevieID = 1;</code>
       */
      public Builder setPublicDevieIDBytes(
          com.google.protobuf.ByteString value) {
        if (value == null) {
    throw new NullPointerException();
  }
  bitField0_ |= 0x00000001;
        publicDevieID_ = value;
        onChanged();
        return this;
      }

      // @@protoc_insertion_point(builder_scope:HandshakeMessage)
    }

    static {
      defaultInstance = new HandshakeMessage(true);
      defaultInstance.initFields();
    }

    // @@protoc_insertion_point(class_scope:HandshakeMessage)
  }

  private static com.google.protobuf.Descriptors.Descriptor
    internal_static_HandshakeMessage_descriptor;
  private static
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_HandshakeMessage_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    java.lang.String[] descriptorData = {
      "\n\nHerd.proto\")\n\020HandshakeMessage\022\025\n\rpubl" +
      "icDevieID\030\001 \001(\tB7\n)org.thoughtcrime.secu" +
      "resms.murmur.objectsB\nHerdProtos"
    };
    com.google.protobuf.Descriptors.FileDescriptor.InternalDescriptorAssigner assigner =
      new com.google.protobuf.Descriptors.FileDescriptor.InternalDescriptorAssigner() {
        public com.google.protobuf.ExtensionRegistry assignDescriptors(
            com.google.protobuf.Descriptors.FileDescriptor root) {
          descriptor = root;
          internal_static_HandshakeMessage_descriptor =
            getDescriptor().getMessageTypes().get(0);
          internal_static_HandshakeMessage_fieldAccessorTable = new
            com.google.protobuf.GeneratedMessage.FieldAccessorTable(
              internal_static_HandshakeMessage_descriptor,
              new java.lang.String[] { "PublicDevieID", });
          return null;
        }
      };
    com.google.protobuf.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new com.google.protobuf.Descriptors.FileDescriptor[] {
        }, assigner);
  }

  // @@protoc_insertion_point(outer_class_scope)
}
