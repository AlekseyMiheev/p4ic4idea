package com.perforce.p4java.impl.mapbased.rpc.sys.helper;

import static com.perforce.p4java.common.base.ObjectUtils.nonNull;
import static com.perforce.p4java.util.compat.StringUtils.isNotBlank;

import java.io.File;
import java.io.IOException;

import com.perforce.p4java.Log;
import com.perforce.p4java.impl.generic.sys.ISystemFileCommandsHelper;
import com.perforce.p4java.util.compat.Jdk7Nonnull;
import com.perforce.p4java.util.compat.NioFiles;

/**
 * Abstract helper class for dynamically determine and use symbolic link support
 * in the Java NIO package (JDK 7 or above).<p>
 *
 * Note that for Windows systems, hard links are available as of Windows 2000,
 * and symbolic links as of Windows Vista. Therefore, for symbolic link support
 * the Windows version needs to be Windows Vista or above.<p>
 *
 * The creation of symbolic links during the sync operation requires the link
 * path and target path to be valid on the operating platform.<p>
 *
 * If a file changes its type to a symlink in Perforce, the content (data) of
 * the file will be used as the link target. In this case, most likely the
 * content (string representation) would not be a valid path.<p>
 *
 * As of this writing, the Perforce server and client treat hard links as normal
 * files/dirs (Perforce cannot tell the difference).
 */
public abstract class SymbolicLinkHelper implements ISystemFileCommandsHelper {

  /**
   * Checks if is symbolic link capable.
   *
   * @return true, if is symbolic link capable
   */
  public static boolean isSymbolicLinkCapable() {
    return true;
  }

  /**
   * Tests whether a file is a symbolic link.
   *
   * @param path the path of the symbolic link
   * @return true if the file is a symbolic link; false if the file does not exist, is not a
   * symbolic link, or it cannot be determined if the file is a symbolic link or not.
   */
  public static boolean isSymbolicLink(String path) {
    if (nonNull(path)) {
      try {
        File filePath = NioFiles.get(path);
        if (nonNull(filePath)) {
          return NioFiles.isSymbolicLink(filePath);
        }
      } catch (Throwable thr) {
        Log.exception(thr);
      }
    }

    return false;
  }

  /**
   * Reads the target path of a symbolic link.
   *
   * @param link the path to the symbolic link
   * @return path the target path of the symbolic link
   */
  public static String readSymbolicLink(String link) {
    if (nonNull(link)) {
      try {
        File linkPath = NioFiles.get(link);
        if (nonNull(linkPath)) {
          File pathObject = NioFiles.readSymbolicLink(linkPath);
          if (nonNull(pathObject)) {
            return pathObject.toString();
          }
        }
        // p4ic4idea: never, never, never catch Throwable unless you make all kinds of special checks.
        //} catch (Throwable thr) {
      } catch (Exception thr) {
        Log.error("Unexpected exception invoking method: %s", thr.getLocalizedMessage());
        Log.exception(thr);
      }
    }

    return null;
  }

  /**
   * Gets the last modified time for a symbolic link.
   *
   * Note: symbolic links are not followed (NOFOLLOW_LINKS LinkOption)
   *
   * @param link the path to the symbolic link
   * @return last modified time of the symbolic link
   */
  public static long getLastModifiedTime(String link) {
    if (nonNull(link)) {
      try {
        File linkPath = NioFiles.get(link);
        if (nonNull(linkPath)) {
          NioFiles.FileTime fileTimeObject = NioFiles.getLastModifiedTime(linkPath, NioFiles.LinkOption.NOFOLLOW_LINKS);
          if (nonNull(fileTimeObject)) {
            return fileTimeObject.toMillis();
          }
        }
      } catch (Throwable thr) {
        Log.error("Unexpected exception invoking method: %s", thr.getLocalizedMessage());
        Log.exception(thr);
      }
    }
    return 0L;
  }

  /**
   * Tests whether a file is a symbolic link.
   *
   * Note: symbolic links are not followed (NOFOLLOW_LINKS LinkOption).
   *
   * @param path the path of the file or symbolic link
   * @return true if the file or symbolic link exists; false if it does not exist, or it cannot be
   * determined.
   */
  public static boolean exists(String path) {
    if (nonNull(path)) {
      try {
        File filePath = NioFiles.get(path);
        if (nonNull(filePath)) {
          return NioFiles.exists(filePath, NioFiles.LinkOption.NOFOLLOW_LINKS);
        }
      // p4ic4idea: never, never, never catch Throwable unless you make all kinds of special checks.
      // } catch (Throwable thr) {
      } catch (Exception thr) {
        Log.error("Unexpected exception invoking method: %s", thr.getLocalizedMessage());
        Log.exception(thr);
      }
    }
    return false;
  }

  /**
   * Creates a symbolic link to a target.
   *
   * @param source the path of the path to the file to move
   * @param target the path to the target file
   * @return the path to the target file
   */
  public static String move(String source, String target) {
    return createSymbolicLink(source, target);
  }

  /**
   * Creates a symbolic link to a target.
   *
   * @param link   the path of the symbolic link to create
   * @param target the target of the symbolic link
   * @return path the path to the symbolic link
   */
  public static String createSymbolicLink(String link, String target) {
    if (isNotBlank(link) && isNotBlank(target)) {
      try {
        File linkPath = NioFiles.get(link);
        File targetPath = NioFiles.get(target);
        if (nonNull(linkPath) && nonNull(targetPath)) {
          createParentDirsIfNotExist(linkPath);
          File pathObject = NioFiles.createSymbolicLink(linkPath, targetPath);
          return pathObject.toString();
        }
      // p4ic4idea: never, never, never catch Throwable unless you make all kinds of special checks.
      // } catch (Throwable thr) {
      } catch (Exception thr) {
        thr.printStackTrace();
        Log.error("Unexpected exception invoking method: %s", thr.getLocalizedMessage());
        Log.exception(thr);
      }
    }

    return null;
  }

  private static void createParentDirsIfNotExist(@Jdk7Nonnull final File filePath) throws IOException {
    File parent = filePath.getParentFile();
    if (NioFiles.notExists(parent)) {
      NioFiles.createDirectories(parent);
    }
  }
}
