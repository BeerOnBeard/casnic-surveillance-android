package com.assortedsolutions.streaming.exceptions;

public class InvalidSurfaceException extends RuntimeException
{
    private static final long serialVersionUID = -7238661340093544496L;

    public InvalidSurfaceException(String message, Throwable throwable)
    {
        super(message, throwable);
    }
}
