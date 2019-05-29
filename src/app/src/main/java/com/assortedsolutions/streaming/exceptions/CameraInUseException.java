package com.assortedsolutions.streaming.exceptions;

public class CameraInUseException extends RuntimeException
{
    public CameraInUseException(String message) {
        super(message);
    }

    private static final long serialVersionUID = -1866132102949435675L;
}
