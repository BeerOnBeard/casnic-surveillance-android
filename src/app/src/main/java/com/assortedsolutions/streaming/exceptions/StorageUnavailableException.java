package com.assortedsolutions.streaming.exceptions;

import java.io.IOException;

public class StorageUnavailableException extends IOException
{
    public StorageUnavailableException(String message) {
        super(message);
    }

    private static final long serialVersionUID = -7537890350373995089L;
}
