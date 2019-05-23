package com.assortedsolutions.streaming.rtsp;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;

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
    private final Map<String, String> attributes;
    private final String content;

    Response(String status)
    {
        // Be careful if you modify the send() method because request might be null!
        this(null, status);
    }

    Response(Request request, String status)
    {
        // default attributes to empty string
        this(request, status, new HashMap<String, String>());
    }

    Response(Request request, String status, Map<String, String> attributes)
    {
        // default content to empty string
        this(request, status, attributes, "");
    }

    Response(Request request, String status, Map<String, String> attributes, String content)
    {
        this.request = request;
        this.status = status;
        this.attributes = attributes;
        this.content = content;

        this.attributes.put("Server", SERVER_NAME);
        this.attributes.put("Content-Length", String.valueOf(this.content.length()));

        int sequenceNumber = getSequenceNumber(this.request);
        if (sequenceNumber >= 0)
        {
            this.attributes.put("CSeq", String.valueOf(sequenceNumber));
        }
    }

    byte[] getBytes()
    {
        StringBuilder responseBuilder = new StringBuilder("RTSP/1.0 " + status + "\r\n");
        for (String key : attributes.keySet())
        {
            responseBuilder.append(key).append(": ").append(attributes.get(key)).append("\r\n");
        }

        responseBuilder.append("\r\n").append(content).append("\r\n");

        String response = responseBuilder.toString();
        Log.d(TAG, response.replace("\r", ""));
        return response.getBytes();
    }

    private static int getSequenceNumber(Request request)
    {
        try
        {
            return Integer.parseInt(request.headers.get("cseq").trim());
        }
        catch (Exception e)
        {
            Log.e(TAG,"Error parsing CSeq", e);
        }

        return -1;
    }
}
