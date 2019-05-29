package com.assortedsolutions.streaming.rtsp;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Request
{
    public static final String TAG = "Request";

    // Parse method & uri
    private static final Pattern regexMethod = Pattern.compile("(\\w+) (\\S+) RTSP",Pattern.CASE_INSENSITIVE);

    // Parse a request header
    private static final Pattern regexHeader = Pattern.compile("(\\S+):(.+)",Pattern.CASE_INSENSITIVE);

    String method;
    String uri;
    HashMap<String,String> headers = new HashMap<>();

    /**
     * Parse the method, uri and headers of a RTSP request.
     */
    static Request parse(BufferedReader input) throws IOException
    {
        Request request = new Request();
        String line;
        Matcher matcher;

        // Parsing request method & uri
        if ((line = input.readLine()) == null)
        {
            throw new SocketException("Client disconnected");
        }

        matcher = regexMethod.matcher(line);
        matcher.find();
        request.method = matcher.group(1);
        request.uri = matcher.group(2);

        // Parsing headers of the request
        while ((line = input.readLine()) != null && line.length() > 3)
        {
            matcher = regexHeader.matcher(line);
            matcher.find();
            request.headers.put(matcher.group(1).toLowerCase(Locale.US), matcher.group(2));
        }

        if (line == null)
        {
            throw new SocketException("Client disconnected");
        }

        return request;
    }
}
