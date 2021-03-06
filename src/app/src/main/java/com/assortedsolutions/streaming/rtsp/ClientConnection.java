package com.assortedsolutions.streaming.rtsp;

import android.util.Base64;
import android.util.Log;

import com.assortedsolutions.streaming.session.Session;
import com.assortedsolutions.streaming.session.SessionBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ClientConnection extends Thread implements Runnable
{
    public static final String TAG = "ClientConnection";
    private static final String SERVER_NAME = "Casnic Surveillance RTSP Server";

    private final String remoteHostAddress;
    private final String localHostAddress;
    private final int localHostPort;
    private final String username;
    private final String password;

    private final Socket socket;
    private final OutputStream outputStream;
    private final BufferedReader inputStreamReader;

    // Each client has an associated session
    private Session session;

    ClientConnection(final Socket socket, final String username, final String password) throws IOException
    {
        this.username = username;
        this.password = password;
        this.socket = socket;

        remoteHostAddress = socket.getInetAddress().getHostAddress();
        localHostAddress = socket.getLocalAddress().getHostAddress();
        localHostPort = socket.getLocalPort();
        outputStream = socket.getOutputStream();
        inputStreamReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    public void run()
    {
        Log.i(TAG, "Connection from " + remoteHostAddress);

        while (!Thread.interrupted())
        {
            Request request = null;
            Response response;

            // Parse the request
            try
            {
                request = Request.parse(inputStreamReader);
            }
            catch (SocketException e)
            {
                // Client has left
                break;
            }
            catch (Exception e)
            {
                Log.e(TAG, "Parsing the request threw", e);
            }

            // Create the response
            if (request == null)
            {
                response = new Response(Response.STATUS_INTERNAL_SERVER_ERROR);
            }
            else
            {
                try
                {
                    response = processRequest(request);
                }
                catch (Exception e)
                {
                    Log.e(TAG, "Processing the request threw", e);
                    response = new Response(request, Response.STATUS_INTERNAL_SERVER_ERROR);
                }
            }

            // Send the response
            try
            {
                outputStream.write(response.getBytes());
            }
            catch (IOException e)
            {
                Log.e(TAG,"Response was not sent properly", e);
                break;
            }
        }

        session.stop();
        session.release();

        try
        {
            socket.close();
        }
        catch (IOException e)
        {
            Log.e(TAG, "Closing the client threw", e);
        }

        Log.i(TAG, "Client at " + remoteHostAddress + " disconnected");
    }

    private Response processRequest(Request request) throws IllegalStateException, IOException
    {
        if(!isAuthorized(request) && !request.method.equalsIgnoreCase("OPTIONS"))
        {
            Map<String, String> attributes = new HashMap<>();
            attributes.put("WWW-Authenticate", "Basic realm=\"" + SERVER_NAME + "\"");
            return new Response(request, Response.STATUS_UNAUTHORIZED, attributes);
        }

        switch(request.method.toUpperCase())
        {
            case "DESCRIBE":
                Log.v(TAG, "Request describe");
                return describe(request);

            case "OPTIONS":
                Log.v(TAG, "Request options");
                Map<String, String> attributes = new HashMap<>();
                attributes.put("Public", "DESCRIBE,SETUP,TEARDOWN,PLAY,PAUSE");
                return new Response(request, Response.STATUS_OK, attributes);

            case "SETUP":
                Log.v(TAG, "Request setup");
                return setup(request);

            case "PLAY":
                Log.v(TAG, "Request play");
                return play(request);

            case "PAUSE":
                Log.i(TAG, "Request to pause but no support");
                return new Response(request, Response.STATUS_OK);

            case "TEARDOWN":
                Log.i(TAG, "Request to teardown but no support");
                return new Response(request, Response.STATUS_OK);

            default:
                Log.e(TAG, "Command unknown: " + request);
                return new Response(request, Response.STATUS_BAD_REQUEST);
        }
    }

    private boolean isAuthorized(Request request)
    {
        if(username == null || password == null || username.isEmpty())
        {
            Log.v(TAG, "Skipping authorization");
            return true;
        }

        String auth = request.headers.get("authorization");
        if (auth == null || auth.isEmpty())
        {
            return false;
        }

        String received = auth.substring(auth.lastIndexOf(" ") + 1);
        String local = username + ":" + password;
        String localEncoded = Base64.encodeToString(local.getBytes(), Base64.NO_WRAP);

        return localEncoded.equals(received);
    }

    private Response describe(Request request) throws IOException
    {
        session = SessionBuilder.getInstance().build();
        session.setOrigin(localHostAddress);
        if (session.getDestination() == null)
        {
            session.setDestination(remoteHostAddress);
        }

        session.configure();

        String content = session.getSessionDescription();

        Map<String, String> attributes = new HashMap<>();
        attributes.put("Content-Base", localHostAddress + ":" + localHostPort);
        attributes.put("Content-Type", "application/sdp");

        return new Response(request, Response.STATUS_OK, attributes, content);
    }

    private Response setup(Request request) throws IOException
    {
        Pattern pattern;
        Matcher matcher;
        int destinationPortOne;
        int destinationPortTwo;
        int ssrc;
        int trackId;
        int[] src;
        String destination;

        pattern = Pattern.compile("trackID=(\\w+)", Pattern.CASE_INSENSITIVE);
        matcher = pattern.matcher(request.uri);

        if (!matcher.find())
        {
            return new Response(Response.STATUS_BAD_REQUEST);
        }

        trackId = Integer.parseInt(matcher.group(1));

        if (!session.streamExists(trackId))
        {
            return new Response(Response.STATUS_NOT_FOUND);
        }

        pattern = Pattern.compile("client_port=(\\d+)(?:-(\\d+))?", Pattern.CASE_INSENSITIVE);
        matcher = pattern.matcher(request.headers.get("transport"));

        if (!matcher.find())
        {
            int[] ports = session.getStream(trackId).getDestinationPorts();
            destinationPortOne = ports[0];
            destinationPortTwo = ports[1];
        }
        else
        {
            destinationPortOne = Integer.parseInt(matcher.group(1));
            if (matcher.group(2) == null)
            {
                destinationPortTwo = destinationPortOne + 1;
            }
            else
            {
                destinationPortTwo = Integer.parseInt(matcher.group(2));
            }
        }

        ssrc = session.getStream(trackId).getSSRC();
        src = session.getStream(trackId).getLocalPorts();
        destination = session.getDestination();

        session.getStream(trackId).setDestinationPorts(destinationPortOne, destinationPortTwo);
        session.start(trackId);

        Map<String, String> attributes = new HashMap<>();

        String transport =
            "RTP/AVP/UDP;" + (InetAddress.getByName(destination).isMulticastAddress() ? "multicast" : "unicast") +
            ";destination=" + session.getDestination() +
            ";client_port=" + destinationPortOne + "-" + destinationPortTwo +
            ";server_port=" + src[0] + "-" + src[1] +
            ";ssrc=" + Integer.toHexString(ssrc) +
            ";mode=play";

        attributes.put("Transport", transport);
        attributes.put("Session", "1185d20035702ca"); // TODO: Session is hard-coded?
        attributes.put("Cache-Control", "no-cache");

        return new Response(request, Response.STATUS_OK, attributes);
    }

    private Response play(Request request)
    {
        String rtpInfo = "";
        if (session.streamExists(0))
        {
            rtpInfo += "url=rtsp://" + localHostAddress + ":" + localHostPort + "/trackID=0;seq=0,";
        }

        if (session.streamExists(1))
        {
            rtpInfo += "url=rtsp://" + localHostAddress + ":" + localHostPort + "/trackID=1;seq=0,";
        }

        // remove trailing comma
        rtpInfo = rtpInfo.substring(0, rtpInfo.length() - 1);

        Map<String, String> attributes = new HashMap<>();
        attributes.put("RTP-Info", rtpInfo);
        attributes.put("Session", "1185d20035702ca"); // TODO: Session is hard-coded?

        return new Response(request, Response.STATUS_OK, attributes);
    }
}
