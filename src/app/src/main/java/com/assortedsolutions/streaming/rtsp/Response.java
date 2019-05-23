package com.assortedsolutions.streaming.rtsp;

import android.util.Log;

class Response
{
    public static final String TAG = "Response";

    // Status code definitions
    static final String STATUS_OK = "200 OK";
    static final String STATUS_BAD_REQUEST = "400 Bad Request";
    static final String STATUS_UNAUTHORIZED = "401 Unauthorized";
    static final String STATUS_NOT_FOUND = "404 Not Found";
    static final String STATUS_INTERNAL_SERVER_ERROR = "500 Internal Server Error";

    private static final String SERVER_NAME = "Casnic Surveillance RTSP Server";

    private final String status;
    private final Request request;
    private final String content;
    private final String attributes;

    Response(String status)
    {
        // Be careful if you modify the send() method because request might be null!
        this(null, status);
    }

    Response(Request request)
    {
        this(request, STATUS_INTERNAL_SERVER_ERROR);
    }

    Response(Request request, String status)
    {
        // default attributes to empty string
        this(request, status, "");
    }

    Response(Request request, String status, String attributes)
    {
        // default content to empty string
        this(request, status, attributes, "");
    }

    Response(Request request, String status, String attributes, String content)
    {
        this.request = request;
        this.status = status;
        this.attributes = attributes;
        this.content = content;
    }

    public byte[] getBytes()
    {
        int seqid = -1;

        try
        {
            seqid = Integer.parseInt(request.headers.get("cseq").trim());
        }
        catch (Exception e)
        {
            Log.e(TAG,"Error parsing CSeq", e);
        }

        String response = "RTSP/1.0 " + status + "\r\n" +
            "Server: " + SERVER_NAME + "\r\n" +
            (seqid >= 0 ? ("Cseq: " + seqid + "\r\n") : "") +
            "Content-Length: " + content.length() + "\r\n" +
            attributes + "\r\n" +
            content;

        Log.d(TAG, response.replace("\r", ""));
        return response.getBytes();
    }
}
