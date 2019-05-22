package com.assortedsolutions.streaming.rtsp;

import android.util.Base64;
import android.util.Log;

import com.assortedsolutions.streaming.Session;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class WorkerThread extends Thread implements Runnable
{
    public static final String TAG = "WorkerThread";
    private static final String SERVER_NAME = "Casnic Surveillance RTSP Server";

    private final String username;
    private final String password;

    private final Socket socket;
    private final OutputStream outputStream;
    private final BufferedReader inputStreamReader;

    // Each client has an associated session
    private Session session;

    WorkerThread(final Socket socket, final String username, final String password) throws IOException
    {
        this.username = username;
        this.password = password;

        this.socket = socket;
        outputStream = socket.getOutputStream();
        inputStreamReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        session = new Session();
    }

    // TODO: Move all request instantiation and status handling into the parseRequest method?
    public void run()
    {
        Request request;
        Response response;

        Log.i(TAG, "Connection from " + socket.getInetAddress().getHostAddress());

        while (!Thread.interrupted())
        {
            request = null;
            response = null;

            // Parse the request
            try
            {
                request = Request.parseRequest(inputStreamReader);
            }
            catch (SocketException e)
            {
                // Client has left
                break;
            }
            catch (Exception e)
            {
                // We don't understand the request :/
                response = new Response();
                response.status = Response.STATUS_BAD_REQUEST;
            }

            // Do something accordingly like starting the streams, sending a session description
            if (request != null)
            {
                try
                {
                    response = processRequest(request);
                }
                catch (Exception e)
                {
                    Log.e(TAG, e.getMessage() != null ? e.getMessage() : "An error occurred", e);
                    response = new Response(request);
                }
            }

            // We always send a response
            // The client will receive an "INTERNAL SERVER ERROR" if an exception has been thrown at some point
            try
            {
                response.send(outputStream);
            }
            catch (IOException e)
            {
                Log.e(TAG,"Response was not sent properly");
                break;
            }
        }

        session.syncStop();
        session.release();

        try
        {
            socket.close();
        }
        catch (IOException e)
        {
            Log.e(TAG, "Closing the client threw", e);
        }

        Log.i(TAG, "Client disconnected");
    }

    private Response processRequest(Request request) throws IllegalStateException, IOException
    {
        Response response = new Response(request);

        //Ask for authorization unless this is an OPTIONS request
        if(!isAuthorized(request) && !request.method.equalsIgnoreCase("OPTIONS"))
        {
            response.attributes = "WWW-Authenticate: Basic realm=\"" + SERVER_NAME + "\"\r\n";
            response.status = Response.STATUS_UNAUTHORIZED;
        }
        else
        {
            /* ********************************************************************************** */
            /* ********************************* Method DESCRIBE ******************************** */
            /* ********************************************************************************** */
            if (request.method.equalsIgnoreCase("DESCRIBE"))
            {
                // Parse the requested URI and configure the session
                session = handleRequest(request.uri, socket);
                session.syncConfigure();

                String requestContent = session.getSessionDescription();
                String requestAttributes =
                    "Content-Base: " + socket.getLocalAddress().getHostAddress() + ":" + socket.getLocalPort() + "/\r\n" +
                    "Content-Type: application/sdp\r\n";

                response.attributes = requestAttributes;
                response.content = requestContent;

                // If no exception has been thrown, we reply with OK
                response.status = Response.STATUS_OK;
            }

            /* ********************************************************************************** */
            /* ********************************* Method OPTIONS ********************************* */
            /* ********************************************************************************** */
            else if (request.method.equalsIgnoreCase("OPTIONS"))
            {
                response.status = Response.STATUS_OK;
                response.attributes = "Public: DESCRIBE,SETUP,TEARDOWN,PLAY,PAUSE\r\n";
                response.status = Response.STATUS_OK;
            }

            /* ********************************************************************************** */
            /* ********************************** Method SETUP ********************************** */
            /* ********************************************************************************** */
            else if (request.method.equalsIgnoreCase("SETUP"))
            {
                Pattern p;
                Matcher m;
                int p2;
                int p1;
                int ssrc;
                int trackId;
                int[] src;
                String destination;

                p = Pattern.compile("trackID=(\\w+)", Pattern.CASE_INSENSITIVE);
                m = p.matcher(request.uri);

                if (!m.find())
                {
                    response.status = Response.STATUS_BAD_REQUEST;
                    return response;
                }

                trackId = Integer.parseInt(m.group(1));

                if (!session.trackExists(trackId))
                {
                    response.status = Response.STATUS_NOT_FOUND;
                    return response;
                }

                p = Pattern.compile("client_port=(\\d+)(?:-(\\d+))?", Pattern.CASE_INSENSITIVE);
                m = p.matcher(request.headers.get("transport"));

                if (!m.find())
                {
                    int[] ports = session.getTrack(trackId).getDestinationPorts();
                    p1 = ports[0];
                    p2 = ports[1];
                }
                else
                {
                    p1 = Integer.parseInt(m.group(1));
                    if (m.group(2) == null)
                    {
                        p2 = p1+1;
                    }
                    else
                    {
                        p2 = Integer.parseInt(m.group(2));
                    }
                }

                ssrc = session.getTrack(trackId).getSSRC();
                src = session.getTrack(trackId).getLocalPorts();
                destination = session.getDestination();

                session.getTrack(trackId).setDestinationPorts(p1, p2);
                session.syncStart(trackId);

                response.attributes = "Transport: RTP/AVP/UDP;" + (InetAddress.getByName(destination).isMulticastAddress() ? "multicast" : "unicast") +
                        ";destination=" + session.getDestination() +
                        ";client_port=" + p1 + "-" + p2 +
                        ";server_port=" + src[0] + "-" + src[1] +
                        ";ssrc=" + Integer.toHexString(ssrc) +
                        ";mode=play\r\n" +
                        "Session: " + "1185d20035702ca\r\n" +
                        "Cache-Control: no-cache\r\n";

                response.status = Response.STATUS_OK;

                // If no exception has been thrown, we reply with OK
                response.status = Response.STATUS_OK;
            }

            /* ********************************************************************************** */
            /* ********************************** Method PLAY *********************************** */
            /* ********************************************************************************** */
            else if (request.method.equalsIgnoreCase("PLAY"))
            {
                String requestAttributes = "RTP-Info: ";
                if (session.trackExists(0))
                {
                    requestAttributes += "url=rtsp://" + socket.getLocalAddress().getHostAddress() + ":" + socket.getLocalPort() + "/trackID=0;seq=0,";
                }

                if (session.trackExists(1))
                {
                    requestAttributes += "url=rtsp://" + socket.getLocalAddress().getHostAddress() + ":" + socket.getLocalPort() + "/trackID=1;seq=0,";
                }

                requestAttributes = requestAttributes.substring(0, requestAttributes.length() - 1) + "\r\nSession: 1185d20035702ca\r\n";

                response.attributes = requestAttributes;

                // If no exception has been thrown, we reply with OK
                response.status = Response.STATUS_OK;
            }

            /* ********************************************************************************** */
            /* ********************************** Method PAUSE ********************************** */
            /* ********************************************************************************** */
            else if (request.method.equalsIgnoreCase("PAUSE"))
            {
                response.status = Response.STATUS_OK;
            }

            /* ********************************************************************************** */
            /* ********************************* Method TEARDOWN ******************************** */
            /* ********************************************************************************** */
            else if (request.method.equalsIgnoreCase("TEARDOWN"))
            {
                response.status = Response.STATUS_OK;
            }

            /* ********************************************************************************** */
            /* ********************************* Unknown method ? ******************************* */
            /* ********************************************************************************** */
            else
            {
                Log.e(TAG, "Command unknown: " + request);
                response.status = Response.STATUS_BAD_REQUEST;
            }
        }

        return response;
    }

    /**
     * By default the RTSP uses {@link UriParser} to parse the URI requested by the client
     * but you can change that behavior by override this method.
     * @param uri The uri that the client has requested
     * @param client The socket associated to the client
     * @return A proper session
     */
    private Session handleRequest(String uri, Socket client) throws IllegalStateException, IOException
    {
        Log.v(TAG, "Handling request...");

        Session session = UriParser.parse(uri);
        session.setOrigin(client.getLocalAddress().getHostAddress());
        if (session.getDestination() == null)
        {
            session.setDestination(client.getInetAddress().getHostAddress());
        }

        return session;
    }

    /**
     * Check if the request is authorized
     * @param request
     * @return true or false
     */
    private boolean isAuthorized(Request request)
    {
        String auth = request.headers.get("authorization");
        if(username == null || password == null || username.isEmpty())
        {
            return true;
        }

        if(auth != null && !auth.isEmpty())
        {
            String received = auth.substring(auth.lastIndexOf(" ")+1);
            String local = username + ":" + password;
            String localEncoded = Base64.encodeToString(local.getBytes(),Base64.NO_WRAP);
            if(localEncoded.equals(received))
            {
                return true;
            }
        }

        return false;
    }
}
