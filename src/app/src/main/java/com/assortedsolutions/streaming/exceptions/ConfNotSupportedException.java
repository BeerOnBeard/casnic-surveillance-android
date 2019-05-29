package com.assortedsolutions.streaming.exceptions;

public class ConfNotSupportedException extends RuntimeException
{
    public ConfNotSupportedException(String message, Throwable throwable)
    {
        super(message, throwable);
    }

    private static final long serialVersionUID = 5876298277802827615L;
}
