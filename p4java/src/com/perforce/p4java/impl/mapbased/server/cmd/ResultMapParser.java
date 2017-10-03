package com.perforce.p4java.impl.mapbased.server.cmd;

import com.perforce.p4java.Log;
import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.exception.AccessException;
import com.perforce.p4java.exception.ConnectionException;
import com.perforce.p4java.exception.RequestException;
import com.perforce.p4java.impl.generic.core.file.FileSpec;
import com.perforce.p4java.impl.mapbased.MapKeys;
import com.perforce.p4java.impl.mapbased.rpc.msg.RpcMessage;
import com.perforce.p4java.impl.mapbased.rpc.msg.ServerMessage;
import com.perforce.p4java.server.IServer;
import com.perforce.p4java.server.IServerMessage;
import com.perforce.p4java.server.ISingleServerMessage;
import com.perforce.p4java.util.compat.Jdk7Nonnull;
import com.perforce.p4java.util.compat.Validate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.perforce.p4java.common.base.ObjectUtils.nonNull;
import static com.perforce.p4java.common.base.P4ResultMapUtils.parseString;
import static com.perforce.p4java.exception.MessageSeverityCode.E_FAILED;
import static com.perforce.p4java.exception.MessageSeverityCode.E_INFO;
import static com.perforce.p4java.impl.mapbased.rpc.func.RpcFunctionMapKey.COMMIT;
import static com.perforce.p4java.impl.mapbased.rpc.func.RpcFunctionMapKey.DEPOT_FILE;
import static com.perforce.p4java.impl.mapbased.rpc.func.RpcFunctionMapKey.REV;
import static com.perforce.p4java.impl.mapbased.rpc.func.RpcFunctionMapKey.TREE;
import static com.perforce.p4java.impl.mapbased.rpc.msg.RpcMessage.CODE;
import static com.perforce.p4java.util.compat.StringUtils.contains;

/**
 * Utility to parse a result map and test for info/error messages etc.
 */
public abstract class ResultMapParser {

	/**
	 * Default size to use creating string builders.
	 */
	private static final int INITIAL_STRING_BUILDER = 100;
	/**
	 * Signals access (login) needed.
	 */
	protected static final String CORE_AUTH_FAIL_STRING_1 = "Perforce password (P4PASSWD)";
	/**
	 * Signals access (login) needed.
	 */
	protected static final String CORE_AUTH_FAIL_STRING_2 = "Access for user";
	/**
	 * Signals ticket has expired.
	 */
	protected static final String CORE_AUTH_FAIL_STRING_3 = "Your session has expired";
	/**
	 * SSO failure.
	 */
	private static final String AUTH_FAIL_STRING_1 = "Single sign-on on client failed";
	/**
	 * Password failure error.
	 */
	private static final String AUTH_FAIL_STRING_2 = "Password invalid";
	/**
	 * Signals ticket has expired.
	 */
	protected static final String CORE_AUTH_FAIL_STRING_4 = "Your session was logged out";
	/**
	 * Signals access (login) needed.
	 */
	// p4ic4idea: extra type
	protected static final String CORE_AUTH_FAIL_STRING_5 = "Perforce password (%'P4PASSWD'%)";

	/**
	 * Array of access error messages.
	 */
	private static final String[] ACCESS_ERR_MSGS = {CORE_AUTH_FAIL_STRING_1,
			CORE_AUTH_FAIL_STRING_2, CORE_AUTH_FAIL_STRING_3, CORE_AUTH_FAIL_STRING_4,
			AUTH_FAIL_STRING_1, AUTH_FAIL_STRING_2,
			// p4ic4idea: extra type
			CORE_AUTH_FAIL_STRING_5};

	/**
	 * Parses the command result map to return a String of info messages. The
	 * messages are expected to be info and if they are not (i.e. they are error
	 * messages) an exception is thrown.
	 *
	 * @param resultMaps the result maps
	 * @return the string
	 * @throws AccessException  the access exception
	 * @throws RequestException the request exception
	 */
	public static String parseCommandResultMapIfIsInfoMessageAsString(
			final List<Map<String, Object>> resultMaps)
			throws AccessException, RequestException {
		StringBuilder retVal = new StringBuilder(INITIAL_STRING_BUILDER);
		if (nonNull(resultMaps)) {
			for (Map<String, Object> map : resultMaps) {
				handleErrorStr(map);
				if (isInfoMessage(map)) {
					if (retVal.length() != 0) {
						retVal.append("\n");
					}

					retVal.append(getInfoStr(map));
				}
			}
		}
		return retVal.toString();
	}

	/**
	 * Checks if is info message.
	 *
	 * @param map the map
	 * @return true, if is info message
	 */
	public static boolean isInfoMessage(final Map<String, Object> map) {
	    IServerMessage msg = getServerMessage(map, E_INFO);
	    return nonNull(msg) && msg.isInfo();
	}

	/**
	 * Tests the map for errors and throws an exception if found.
	 *
	 * @param map the map
	 * @return true, if successful
	 * @throws RequestException the request exception
	 * @throws AccessException  the access exception
	 */
	public static boolean handleErrorStr(final Map<String, Object> map)
			throws RequestException, AccessException {
        IServerMessage errStr = getErrorStr(map);

		if (nonNull(errStr)) {
			if (isAuthFail(errStr)) {
				throw new AccessException(errStr);
			} else {
				throw new RequestException(errStr);
			}
		}
		return false;
	}

	/**
	 * RPC impl errors come across the wire as a map in the form usually like
	 * this:
	 * <p>
	 * <pre>
	 * fmt0=Access for user '%user%' has not been enabled by 'p4 protect'.,
	 * func=client-Message, user=nouser, code0=822483067
	 * </pre>
	 * <p>
	 * With tags being used for non-error payloads, we can just basically pick
	 * up the presence of the code0 entry; if it's there, use fmt0 as the format
	 * and the other args as appropriate...
	 * <p>
	 * <p>
	 * FIXME: work with multiple code/fmt sets... -- HR.
	 *
	 * @param map the map
	 * @return the error string if found.
	 */
	public static IServerMessage getErrorStr(final Map<String, Object> map) {
		return getServerMessage(map, E_FAILED);
	}

	/**
	 * Checks to see if an error String is as a result of an auth fail.
	 *
	 * @param errStr the err str
	 * @return true, if is auth fail
	 */
	// p4ic4idea: takes IServerMessage instead of string
	public static boolean isAuthFail(final IServerMessage errStr) {
		if (nonNull(errStr)) {
			for (String str : ACCESS_ERR_MSGS) {
                for (ISingleServerMessage message : errStr.getAllMessages()) {
                    if (contains(message.getMessageFormat(), str)) {
                        return true;
                    }
                }
			}
		}
		return false;
	}

	/**
	 * Gets the info message from the passed-in Perforce command results map. If
	 * no info message found in the results map it returns null.
	 * <p>
	 * </p>
	 * <p>
	 * Note that the severity code is MessageSeverityCode.E_INFO. Therefore,
	 * only message with severity code = MessageSeverityCode.E_INFO will be
	 * returned.
	 * <p>
	 * <p>
	 * RPC impl errors come across the wire as a map in the form usually like
	 * this:
	 * <p>
	 * <pre>
	 * fmt0=Access for user '%user%' has not been enabled by 'p4 protect'.,
	 * func=client-Message, user=nouser, code0=822483067
	 * </pre>
	 * <p>
	 * Note that the code0 entry will be used to get the severity level; the
	 * fmt0 entry contains the message.
	 * <p>
	 *
	 * @param map Perforce command results map
	 * @return possibly-null info string
	 * @since 2011.2
	 */
	// p4ic4idea: return an IServerMessage
	public static IServerMessage getInfoStr(final Map<String, Object> map) {
        return getServerMessage(map, E_INFO);
	}

	/**
	 * Gets the string.
	 *
	 * @param map         the map
	 * @param minimumCode the minimum code
	 * @return the string
	 */
    // p4ic4idea change: returning an IServerMessage instead of a string.
    private static IServerMessage getServerMessage(Map<String, Object> map, int minimumCode ) {
        if (nonNull(map)) {
            int index = 0;
            String code = (String) map.get(CODE + index);

            List<ISingleServerMessage> singleMessages = new ArrayList<ISingleServerMessage>();

            while (code != null) {
                singleMessages.add(new ServerMessage.SingleServerMessage(code, index, map));
                index++;
                code = (String) map.get(RpcMessage.CODE + index);
            }

            // Only return a string if at least one severity code was found
            if (! singleMessages.isEmpty()) {
                final ServerMessage msg = new ServerMessage(singleMessages, map);
                if (msg.getSeverity() >= minimumCode) {
                    return msg;
                }
            }
        }
        return null;
    }

	/**
	 * Gets the info/warning/error/fatal message from the passed-in Perforce
	 * command results map. If no info/warning/error/fatal message found in the
	 * results map it returns null.
	 * <p>
	 * </p>
	 * <p>
	 * Note that the minimum severity code is MessageSeverityCode.E_INFO.
	 * Therefore, only message with severity code >= MessageSeverityCode.E_INFO
	 * will be returned.
	 * <p>
	 * <p>
	 * RPC impl errors come across the wire as a map in the form usually like
	 * this:
	 * <p>
	 * <pre>
	 * fmt0=Access for user '%user%' has not been enabled by 'p4 protect'.,
	 * func=client-Message, user=nouser, code0=822483067
	 * </pre>
	 * <p>
	 * Note that the code0 entry will be used to get the severity level; the
	 * fmt0 entry contains the message.
	 * <p>
	 *
	 * @param map Perforce command results map
	 * @return possibly-null info/warning/error/fatal string
	 * @since 2011.2
	 */
	// p4ic4idea: return IServerMessage
	public static IServerMessage getErrorOrInfoStr(final Map<String, Object> map) {
		return getServerMessage(map, E_INFO);
	}

	/**
	 * Throw request exception if error message found.
	 *
	 * @param map the map
	 * @throws RequestException the request exception
	 */
	public static void throwRequestExceptionIfErrorMessageFound(final Map<String, Object> map)
			throws RequestException {
        IServerMessage errStr = getErrorStr(map);
		if (nonNull(errStr)) {
			throw new RequestException(errStr);
		}
	}

	/**
	 * Handle error or info str.
	 *
	 * @param map the map
	 * @return true, if successful
	 * @throws RequestException the request exception
	 * @throws AccessException  the access exception
	 */
	public static boolean handleErrorOrInfoStr(final Map<String, Object> map)
			throws RequestException, AccessException {
        IServerMessage errStr = getErrorOrInfoStr(map);

		if (nonNull(errStr)) {
			if (isAuthFail(errStr)) {
				throw new AccessException(errStr);
			} else {
				throw new RequestException(errStr);
			}
		}
		return false;
	}

	/**
	 * Parses the command result map as string.
	 *
	 * @param resultMaps the result maps
	 * @return the string
	 * @throws AccessException  the access exception
	 * @throws RequestException the request exception
	 */
	public static String parseCommandResultMapAsString(
			@Jdk7Nonnull final List<Map<String, Object>> resultMaps)
			throws AccessException, RequestException {
		StringBuilder retVal = new StringBuilder();
		if (nonNull(resultMaps)) {
			for (Map<String, Object> map : resultMaps) {
				handleErrorStr(map);
				if (retVal.length() != 0) {
					retVal.append("\n");
				}

				retVal.append(getInfoStr(map));
			}
		} else {
			Log.warn("Null map array is returned when execute Helix command");
		}

		return retVal.toString();
	}

	/**
	 * Parses the command result map as file specs.
	 *
	 * @param id         the id
	 * @param server     the server
	 * @param resultMaps the result maps
	 * @return the list
	 */
	public static List<IFileSpec> parseCommandResultMapAsFileSpecs(final int id,
	                                                               final IServer server, final List<Map<String, Object>> resultMaps) {

		List<IFileSpec> fileList = new ArrayList<IFileSpec>();
		if (nonNull(resultMaps)) {
			// NOTE: all the results are returned in *one* map, not an array of
			// them...
			if (!resultMaps.isEmpty() && nonNull(resultMaps.get(0))) {
				Map<String, Object> map = resultMaps.get(0);

				for (int i = 0; nonNull(map.get(REV + i)); i++) {
					FileSpec fSpec = new FileSpec(map, server, i);
					fSpec.setChangelistId(id);
					fileList.add(fSpec);
				}
			}
		}
		return fileList;
	}

	/**
	 * Parses the graph command result map as file specs.
	 *
	 * @param server     the server
	 * @param resultMaps the result maps
	 * @return the list
	 */
	public static List<IFileSpec> parseGraphCommandResultMapAsFileSpecs(final IServer server, final List<Map<String, Object>> resultMaps) {

		List<IFileSpec> fileList = new ArrayList<IFileSpec>();
		if (nonNull(resultMaps)) {
			// NOTE: all the results are returned in *one* map, not an array of
			// them...
			if (!resultMaps.isEmpty() && nonNull(resultMaps.get(0))) {
				Map<String, Object> map = resultMaps.get(0);
				for (int i = 0; nonNull(map.get(DEPOT_FILE + i)); i++) {
					FileSpec fSpec = new FileSpec(map, server, i);
					fSpec.setCommitSha(parseString(map, COMMIT));
					fSpec.setTreeSha(parseString(map, TREE));
					fileList.add(fSpec);
				}
			}
		}
		return fileList;
	}

	/**
	 * Handle file error str.
	 *
	 * @param map the map
	 * @return the string
	 * @throws ConnectionException the connection exception
	 * @throws AccessException     the access exception
	 */
	// p4ic4idea: return IServerMessage
	public static IServerMessage handleFileErrorStr(final Map<String, Object> map)
			throws ConnectionException, AccessException {
		IServerMessage errStr = getErrorOrInfoStr(map);
		if (nonNull(errStr)) {
			if (isAuthFail(errStr)) {
				throw new AccessException(errStr);
			} else {
				return errStr;
			}
		}

		return null;
	}

	/**
	 * Unfortunately, the p4 command version returns a valid map for
	 * non-existent clients/labels/users; the only way we can detect that the
	 * client/label/user doesn't exist is to see if the Update or Access map
	 * entries exist -- if they do, the client/label/user is (most likely) a
	 * valid client/label/user on this server. This seems less than optimal to
	 * me... -- HR.
	 *
	 * @param map the map
	 * @return true, if is exist client or label or user
	 */
	public static boolean isExistClientOrLabelOrUser(final Map<String, Object> map) {
		Validate.notNull(map);
		return map.containsKey(MapKeys.UPDATE_KEY) || map.containsKey(MapKeys.ACCESS_KEY);
	}

	/**
	 * Unfortunately, the p4 command version returns a valid map for
	 * non-existent clients/labels/users; the only way we can detect that the
	 * client/label/user doesn't exist is to see if the Update or Access map
	 * entries exist -- if they do, the client/label/user is (most likely) a
	 * valid client/label/user on this server. This seems less than optimal to
	 * me... -- HR.
	 *
	 * @param map the map
	 * @return true, if is non exist client or label or user
	 */
	public static boolean isNonExistClientOrLabelOrUser(final Map<String, Object> map) {
		Validate.notNull(map);
		return !isExistClientOrLabelOrUser(map);
	}
}
