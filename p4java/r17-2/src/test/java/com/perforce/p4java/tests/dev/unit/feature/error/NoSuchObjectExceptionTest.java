/**
 * Copyright (c) 2010 Perforce Software.  All rights reserved.
 */
package com.perforce.p4java.tests.dev.unit.feature.error;

import com.perforce.p4java.exception.NoSuchObjectException;
import com.perforce.p4java.tests.dev.annotations.TestId;

/**
 * @author Kevin Sawicki (ksawicki@perforce.com)
 */
@TestId("NoSuchObjectExceptionTest")
public class NoSuchObjectExceptionTest extends BaseThrowableTestHelper {

    /**
     * @see com.perforce.p4java.tests.dev.unit.feature.error.BaseThrowableTestHelper#createThrowable()
     */
    protected Throwable createThrowable() {
	return new NoSuchObjectException();
    }

    /**
     * @see com.perforce.p4java.tests.dev.unit.feature.error.BaseThrowableTestHelper#createThrowable(java.lang.String)
     */
    protected Throwable createThrowable(String message) {
	return new NoSuchObjectException(message);
    }

    /**
     * @see com.perforce.p4java.tests.dev.unit.feature.error.BaseThrowableTestHelper#createThrowable(java.lang.Throwable)
     */
    protected Throwable createThrowable(Throwable cause) {
	return new NoSuchObjectException(cause);
    }

    /**
     * @see com.perforce.p4java.tests.dev.unit.feature.error.BaseThrowableTestHelper#createThrowable(java.lang.String,
     *      java.lang.Throwable)
     */
    protected Throwable createThrowable(String message, Throwable cause) {
	return new NoSuchObjectException(message, cause);
    }

}
