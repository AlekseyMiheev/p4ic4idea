/**
 * Copyright (c) 2011 Perforce Software.  All rights reserved.
 */
package com.perforce.p4java.impl.generic.admin;

import java.util.ArrayList;
import java.util.List;

import com.perforce.p4java.admin.ILogTail;
import com.perforce.p4java.util.compat.Jdk7Nonnull;
import com.perforce.p4java.util.compat.Validate;

/**
 * Default implementation of the ILogTail interface.
 */
public class LogTail implements ILogTail {

    /**
     * The log file path.
     */
    private String logFilePath = null;

    /**
     * The offset in bytes.
     */
    private long offset = -1;

    /**
     * The log file data.
     */
    private List<String> data = new ArrayList<String>();

    public LogTail(final String logFilePath, final long offset, @Jdk7Nonnull final List<String> data) {
        Validate.notBlank(logFilePath, "logFilePath shouldn't null or empty");
        Validate.isTrue(offset >= 0, "offset should be greater than or equal to 0");
        Validate.notNull(data, "No data passed to the LogTail constructor.");
        Validate.notEmpty(data, "No data passed to the LogTail constructor.");
        this.logFilePath = logFilePath;
        this.offset = offset;
        this.data.addAll(data);
    }

    @Override
    public String getLogFilePath() {
        return logFilePath;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public List<String> getData() {
        return data;
    }
}
