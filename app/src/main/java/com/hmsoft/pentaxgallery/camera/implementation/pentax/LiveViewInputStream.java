package com.hmsoft.pentaxgallery.camera.implementation.pentax;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

class LiveViewInputStream extends DataInputStream {

    static final int MAX_FRAME_LENGTH = 204800;

    private final byte[] SOI_MARKER = {-1, -40};
    private final byte[] EOF_MARKER = {-1, -39};
    private final static String CONTENT_LENGTH = "Content-Length";

    private byte[] header = null;

    private int headerLen = -1;
    private int headerLenPrev = -1;
    private int count;

    private int contentLength = -1;

    LiveViewInputStream(InputStream in) {
        super(new BufferedInputStream(in, MAX_FRAME_LENGTH));
        count = 0;
    }

    private int getEndOfSequence(byte[] sequence) throws IOException {
        int seqIndex = 0;
        for (int i = 0; i < MAX_FRAME_LENGTH; i++) {
            byte c = (byte) this.readUnsignedByte();
            if (c == sequence[seqIndex]) {
                if (++seqIndex == sequence.length) {
                    return i + 1;
                }
            } else {
                seqIndex = 0;
            }
        }
        return -1;
    }

    private int getStartOfSequence(byte[] sequence) throws IOException {
        int end = getEndOfSequence(sequence);
        return end < 0 ? -1 : end - sequence.length;
    }

    private int getEndOfSequenceSimplified(byte[] sequence) throws IOException {
        int startPos = this.contentLength / 2;
        int endPos = 3 * this.contentLength / 2;

        skipBytes(this.headerLen + startPos);

        int seqIndex = 0;
        for (int i = 0; i < endPos - startPos; i++) {
            byte c = (byte) this.readUnsignedByte();
            if (c == sequence[seqIndex]) {
                if (++seqIndex == sequence.length) {
                    return this.headerLen + startPos + i + 1;
                }
            } else {
                seqIndex = 0;
            }
        }
        return -1;
    }

    private int getContentLength(byte[] headerBytes) throws IOException, IllegalArgumentException {
        ByteArrayInputStream headerIn = new ByteArrayInputStream(headerBytes);
        Properties props = new Properties();
        props.load(headerIn);
        String contentLength = props.getProperty(CONTENT_LENGTH);
        return contentLength != null ? Integer.parseInt(props.getProperty(CONTENT_LENGTH)) : getContentLength();
    }

    private int getContentLength() throws IOException {
        int cl = getEndOfSequenceSimplified(this.EOF_MARKER);
        if (cl < 0) {
            reset();
            cl = getEndOfSequence(this.EOF_MARKER);
        }
        return cl;
    }

    int getFrame(byte[] buf) throws IOException {

        mark(MAX_FRAME_LENGTH);

        try {
            headerLen = getStartOfSequence(this.SOI_MARKER);
        } catch (IOException e) {
            reset();
            return 0;
        }

        reset();
        if ((this.header == null) || (headerLen != this.headerLenPrev)) {
            this.header = new byte[headerLen];
        }

        this.headerLenPrev = headerLen;
        readFully(this.header);

        int contentLengthNew;
        try {
            contentLengthNew = getContentLength(this.header);
        } catch (IllegalArgumentException nfe) {
            contentLengthNew = getContentLength();
        } catch (IOException e) {
            reset();
            return 0;
        }

        this.contentLength = contentLengthNew;

        reset();

        skipBytes(headerLen);

        readFully(buf, 0, this.contentLength);

        return this.contentLength;
    }
}
