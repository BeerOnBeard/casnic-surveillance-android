package com.assortedsolutions.streaming.rtsp;

import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;

class Response
{
    public static final String TAG = "Response";
    public static final String SERVER_NAME = "Casnic Surveillance RTSP";

    // Status code definitions
    public static final String STATUS_OK = "200 OK";
    public static final String STATUS_BAD_REQUEST = "400 Bad Request";
    public static final String STATUS_UNAUTHORIZED = "401 Unauthorized";
    public static final String STATUS_NOT_FOUND = "404 Not Found";
    public static final String STATUS_INTERNAL_SERVER_ERROR = "500 Internal Server Error";

    public String status = STATUS_INTERNAL_SERVER_ERROR;
    public String content = "";
    public String attributes = "";

    private final Request request;

    public Response(Request request)
    {
        this.request = request;
    }

    public Response()
    {
        // Be careful if you modify the send() method because request might be null!
        request = null;
    }

    public void send(OutputStream output) throws IOException
    {
        int seqid = -1;

        try
        {
            seqid = Integer.parseInt(request.headers.get("cseq").replace(" ",""));
        }
        catch (Exception e)
        {
            Log.e(TAG,"Error parsing CSeq: " + (e.getMessage() != null ? e.getMessage() : ""));
        }

        String response = "RTSP/1.0 " + status + "\r\n" +
                "Server: " + SERVER_NAME + "\r\n" +
                (seqid >= 0 ? ("Cseq: " + seqid + "\r\n") : "") +
                "Content-Length: " + content.length() + "\r\n" +
                attributes + "\r\n" +
                content;

        Log.d(TAG,response.replace("\r", ""));

        output.write(response.getBytes());
    }
}
