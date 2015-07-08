/**
 * 
 */
package com.perforce.p4java.impl.mapbased.rpc.sys;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;
import java.util.zip.Checksum;
import java.util.zip.Inflater;

import com.perforce.p4java.CharsetDefs;
import com.perforce.p4java.CharsetConverter;
import com.perforce.p4java.exception.P4JavaError;
import com.perforce.p4java.exception.NullPointerError;
import com.perforce.p4java.impl.generic.client.ClientLineEnding;
import com.perforce.p4java.impl.mapbased.rpc.RpcPropertyDefs;
import com.perforce.p4java.impl.mapbased.rpc.func.RpcFunctionMapKey;
import com.perforce.p4java.impl.mapbased.rpc.func.helper.MD5Digester;

/**
 * Provides a Perforce-specific extension to the basic Java OuputStream to allow
 * us to intercept methods and implement our own extensions. The two main aims
 * here are for the GKUNZIP file type to stream unzip on the fly, and to do line
 * end processing for text files where needed; everything else is just currently
 * handled in the superclass without real intervention.
 * <p>
 * 
 * Note that for the unzipping we use a contained file output stream rather than
 * ourself, mostly to avoid recursion...
 * 
 * Some of the raw GZUNZIP methods and definitions are copied pretty much as-is
 * from the original gkzip stuff.<p>
 * 
 * The 10.2 sync / transfer integrity checks are basically implemented here, with
 * help from the RpcInflaterOutputStream class. The way this is done is the MD5 hashing
 * has to be done against the incoming 'raw' bytes (i.e. the normalized server-side
 * form of the file) unless the incoming file type is compressed binary, in which case
 * we have to hash the uncompressed version. The non-binary hashing is done here; the
 * compressed stuff is done in RpcInflaterOutputStream.<p>
 */

public class RpcOutputStream extends FileOutputStream {

	/*
	 * GZIP header magic number.
	 */
	private final static int GZIP_MAGIC = 0x8b1f;

	/*
	 * GZIP File header flags.
	 */
	@SuppressWarnings("unused")
	private final static int FTEXT	= 1;	// Extra text
	private final static int FHCRC	= 2; // Header CRC
	private final static int FEXTRA	= 4; // Extra field
	private final static int FNAME	= 8; // File name
	private final static int FCOMMENT	= 16; // File comment

	private static final int TRAILER_SIZE = 8; // bytes

	private RpcPerforceFile file = null;
	private RpcPerforceFileType fileType = null;
	private RpcInflaterOutputStream outStream = null;
	private CheckedOutputStream checkedOutStream = null;
	private Inflater inflater = null;
	private RpcCRC32Checksum crc = null;
	private boolean headerRead = false;
	private byte[] footerBytes = null;
	private boolean closed = false;
	private ClientLineEnding lineEnding = null;
	private RpcLineEndFilterOutputStream lineEndStream = null;
	private Charset charset = null;
	private CharsetConverter converter;
	private String serverDigest = null;	// If given, the server-side MD5 digest
										// for this file. Used in the 10.2+ sync (etc.)
										// transfer integrity checks.
	private MD5Digester localDigester = null;	// Also used in the 10.2+ transfer
												// integrity checks.

	public RpcOutputStream(RpcPerforceFile file) throws IOException {
		this(file, null, false, false);
	}
	
	public RpcOutputStream(RpcPerforceFile file, boolean useLocalDigester) throws IOException {
		this(file, null, false, useLocalDigester);
	}

	public RpcOutputStream(RpcPerforceFile file, Charset charset, boolean isUnicodeServer,
										boolean useLocalDigester) throws IOException {
		super(file);
		if (file == null) {
			throw new NullPointerError(
				"Null RpcPerforceFile passed to RpcOutputStream constructor");
		}
		
		if (useLocalDigester) {
			this.localDigester = new MD5Digester();
		}
		
		if( charset != null && !charset.equals(CharsetDefs.UTF8) ) {
			this.charset = charset;
		}
		
		this.closed = false;
		this.file = file;
		this.fileType = file.getFileType();
		this.lineEnding = file.getLineEnding();

		if (this.fileType != null) {
			switch	(this.fileType) {
				case FST_UTF16:
					this.charset = CharsetDefs.UTF16;
				case FST_UNICODE:
					if ((this.charset != null) &&
								(isUnicodeServer || (this.charset == CharsetDefs.UTF16))) {
						this.converter = new CharsetConverter(CharsetDefs.UTF8, this.charset);
					}
				case FST_TEXT:
				case FST_XTEXT:
					if (ClientLineEnding.needsLineEndFiltering(lineEnding)) {
						this.lineEndStream = new RpcLineEndFilterOutputStream(
														this, this.lineEnding);
					}
					
					break;
					
				case FST_GUNZIP:
				case FST_XGUNZIP:
					this.inflater = new Inflater(true);
					this.crc = new RpcCRC32Checksum();
					this.checkedOutStream = new CheckedOutputStream(new BufferedOutputStream(this), this.crc);
					this.outStream = new RpcInflaterOutputStream(this.checkedOutStream, this.inflater,
															this.localDigester);
					this.headerRead = false;
					this.footerBytes = new byte[TRAILER_SIZE];
					break;
			}
		} else {
			this.fileType = RpcPerforceFileType.FST_TEXT;
		}
	}

	@Override
	public void close() throws IOException {
		if (!closed) {
			closed = true;
			switch (this.fileType) {
				case FST_TEXT:
				case FST_XTEXT:
					if (this.lineEndStream != null) {
						this.lineEndStream.close();
					}
					break;
				
				case FST_GUNZIP:
				case FST_XGUNZIP:			
					readTrailer(this.footerBytes);
					this.outStream.close();
					this.checkedOutStream.close();
					break;
			}
			super.close();
		}
	}

	/**
	 * @see java.io.OutputStream#flush()
	 */
	public void flush() throws IOException {
		super.flush();
		// Flush unicode chars
		if (this.converter != null) {
			byte[] underflow = this.converter.clearUnderflow();
			if( underflow != null) {
				writeConverted(underflow);
			}
		}
	}
	
	/**
	 * Specialized write method to write a map containing a byte array
	 * with the key RpcFunctionMapKey.DATA (all other fields are ignored).
	 * This map is assumed to have been constructed as part of the writeFile()
	 * method or something similar.
	 */
	
	public long write(Map<String, Object> map) throws IOException {
		if (map == null) {
			throw new NullPointerError(
				"Null map passed to RpcOutputStream.write(map)");
		}
		
		try {
			byte[] sourceBytes = (byte[]) map.get(RpcFunctionMapKey.DATA);
			return writeConverted(sourceBytes);
		} catch (ClassCastException exc) {
			throw new P4JavaError(
				"RpcFunctionMapKey.DATA value not byte[] type");
		}
	}

	@Override
	public void write(byte[] sourceBytes, int off, int len) throws IOException {
		if (sourceBytes == null) {
			throw new NullPointerError(
					"Null bytes passed to RpcOutputStream.write()");
		}
		if (off < 0) {
			throw new P4JavaError("Negative offset in RpcOutputStream.write()");
		}
		if (len < 0) {
			throw new P4JavaError("Negative length in RpcOutputStream.write()");
		}
		super.write(sourceBytes, off, len);
	}

	@Override
	public void write(byte[] b) throws IOException {
		if (b == null) {
			throw new NullPointerError(
					"Null bytes passed to RpcOutputStream.write()");
		}
		super.write(b, 0, b.length);
	}
	
	/**
	 * Write an array of bytes with being aware of encodings, line ending
	 * conversions, and compression. Any 10.2+ sync integrity checks are
	 * done either here or in the unzip's stream, but (definitely) not
	 * in both places.
	 * 
	 * @param sourceBytes
	 * @throws IOException
	 */
	public long writeConverted(byte[] sourceBytes) throws IOException {
		int len = sourceBytes.length;
		
		if (len <= 0) {
			return 0;
		}
		int start = 0;

		long bytesWritten = len - start;
		
		switch (this.fileType) {
			case FST_UNICODE:
			case FST_UTF16:
				
				// Convert to local charset if set
				if (this.converter != null) {
					if (this.localDigester != null) {
						this.localDigester.update(sourceBytes);
					}
					//Convert line endings before converting to unicode
					if (this.lineEndStream != null) {
						// Use intermediate buffer to hold line ending converted
						// source bytes
						ByteArrayOutputStream out = new ByteArrayOutputStream(
								RpcPropertyDefs.RPC_DEFAULT_FILE_BUF_SIZE);
						this.lineEndStream.write(out, sourceBytes, start, len);
	
						sourceBytes = out.toByteArray();
						len = sourceBytes.length;
						start = 0;
					}
				
					ByteBuffer sourceBuffer = ByteBuffer.wrap(sourceBytes);
					ByteBuffer converted = this.converter.convert(sourceBuffer);
					if (converted != null) {
						sourceBytes = converted.array();
						start = converted.position();
						len = converted.limit();
					} else {
						len = 0;
					}
	
					if (len <= 0) {
						return 0;
					}
					
					this.write(sourceBytes, start, len);
					bytesWritten = len - start;
					break;
				}
			
			case FST_TEXT:
			case FST_XTEXT:
				if (this.localDigester != null) {
					this.localDigester.update(sourceBytes, start, len);
				}
				if (this.lineEndStream != null) {
					this.lineEndStream.write(sourceBytes, start, len);
				} else {
					this.write(sourceBytes, start, len);
				}
				bytesWritten = len - start;
				break;
			
			case FST_GUNZIP:
			case FST_XGUNZIP:
				
				// NOTE: We always copy the last eight bytes of what's passed into
				// us here so we can use it for trailer / footer calculation
				// later, when we don't have direct access to it (we can't retrieve it
				// from the Inflater without a  bit of skulduggery, unfortunately).
				//
				// This is not as straightforward as it looks due to the endless
				// possibilities for cross-packet trailers, etc., so what's here
				// can almost certainly be reworked in a more efficient way
				// in later versions (using FilterOutputStream?) --- HR.
				
				long bytesWrittenPrior = this.outStream.getBytesWritten();

				if (!headerRead) {
					ByteArrayInputStream byteStream
							= new ByteArrayInputStream(sourceBytes, 0, len);
					readHeader(byteStream, new RpcCRC32Checksum());
					headerRead = true;
					int bytesAvailable = byteStream.available();
					if (bytesAvailable > 0) {
						this.outStream.write(sourceBytes, (len - bytesAvailable),
																			bytesAvailable);
						
						if (bytesAvailable >= TRAILER_SIZE) {							
							System.arraycopy(
									sourceBytes,
									(len - TRAILER_SIZE),
									this.footerBytes,
									0,
									TRAILER_SIZE);
						} else {
							// Copy what we can; if it turns out the rest was in the next
							// packet, we'll detect that then...
							
							System.arraycopy(
									sourceBytes,
									(len - bytesAvailable),
									this.footerBytes,
									0,
									bytesAvailable);
						}
					}
				} else {
					this.outStream.write(sourceBytes, 0, len);
						
					if (len >= TRAILER_SIZE) {
						System.arraycopy(
								sourceBytes,
								(len - TRAILER_SIZE),
								this.footerBytes,
								0,
								TRAILER_SIZE);
					} else {
						// first move the last (TRAILER_SIZE-len) bytes to the
				        // beginning of the footer
						
						System.arraycopy(
								this.footerBytes,
								len,
								this.footerBytes,
								0,
								TRAILER_SIZE - len );
					
						// Add to what's (hopefully) there already...
						
						System.arraycopy(
								sourceBytes,
								0,
								this.footerBytes,
								TRAILER_SIZE - len,
								len);
					}
				}
				
				bytesWritten = this.outStream.getBytesWritten() - bytesWrittenPrior;
				break;
			
			default:
				if (this.localDigester != null) {
					this.localDigester.update(sourceBytes, 0, len);
				}
				this.write(sourceBytes, 0, len);
				bytesWritten = len - 0;
		}

		return bytesWritten;
	}

	@Override
	public void write(int b) throws IOException {		
		super.write(b);
	}

	public RpcPerforceFile getFile() {
		return file;
	}

	private void readHeader(InputStream inStream, RpcCRC32Checksum crc) throws IOException {
		CheckedInputStream in = new CheckedInputStream(inStream, crc);
		crc.reset();
		// Check header magic
		if (readUShort(in) != GZIP_MAGIC) {
		    throw new IOException("Not in GZIP format");
		}
		// Check compression method
		if (readUByte(in) != 8) {
		    throw new IOException("Unsupported compression method");
		}
		// Read flags
		int flg = readUByte(in);
		// Skip MTIME, XFL, and OS fields
		skipBytes(in, 6);
		// Skip optional extra field
		if ((flg & FEXTRA) == FEXTRA) {
		    skipBytes(in, readUShort(in));
		}
		// Skip optional file name
		if ((flg & FNAME) == FNAME) {
		    while (readUByte(in) != 0) ;
		}
		// Skip optional file comment
		if ((flg & FCOMMENT) == FCOMMENT) {
		    while (readUByte(in) != 0) ;
		}
		// Check optional header CRC
		if ((flg & FHCRC) == FHCRC) {
		    int v = (int)crc.getValue() & 0xffff;
		    if (readUShort(in) != v) {
			throw new IOException("Corrupt GZIP header");
		    }
		}
	}

	private void readTrailer(byte[] bytes) throws IOException {
		InputStream in = new ByteArrayInputStream(bytes);
		
		// Uses left-to-right evaluation order
		long intIn = readUInt(in);
		long len = readUInt(in);
		Checksum checksum = this.checkedOutStream.getChecksum();

		if ((checksum != null) && (intIn != checksum.getValue())) {
			throw new IOException("Corrupt GZIP trailer (bad CRC value)");
		}
		
		if (len != (inflater.getBytesWritten() & 0xffffffffL)) {
		    throw new IOException("Corrupt GZIP trailer (bad bytes-written size)");
		}
	}

	/*
	 * Reads unsigned integer in Intel byte order.
	 */
	private long readUInt(InputStream in) throws IOException {
		long s = readUShort(in);
		return ((long)readUShort(in) << 16) | s;
    }

	/*
	 * Reads unsigned short in Intel byte order.
	 */
    private int readUShort(InputStream in) throws IOException {
		int b = readUByte(in);
		return ((int)readUByte(in) << 8) | b;
    }

	/*
	 * Reads unsigned byte.
	 */
    private int readUByte(InputStream in) throws IOException {
		int b = in.read();
		if (b == -1) {
		    throw new EOFException();
		}
	        if (b < -1 || b > 255) {
	            throw new IOException(".read() returned value out of range -1..255: " + b);
	    }
		return b;
    }

	private  void skipBytes(InputStream in, int n) throws IOException {
		byte[] tmpbuf = new byte[128];
		while (n > 0) {
		    int len = in.read(tmpbuf, 0, n < tmpbuf.length ? n : tmpbuf.length);
		    if (len == -1) {
		    	throw new EOFException("Unexpected EOF");
		    }
		    n -= len;
		}
	}

	public String getServerDigest() {
		return serverDigest;
	}

	public void setServerDigest(String serverDigest) {
		this.serverDigest = serverDigest;
	}

	public MD5Digester getLocalDigester() {
		return localDigester;
	}

	public void setLocalDigester(MD5Digester localDigester) {
		this.localDigester = localDigester;
	}
}
