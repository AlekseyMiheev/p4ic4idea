package com.perforce.p4java.common.base;

import com.perforce.p4java.util.compat.Jdk7Nonnull;
import com.perforce.p4java.util.compat.Jdk7Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.perforce.p4java.common.base.ObjectUtils.isNull;
import static com.perforce.p4java.common.base.ObjectUtils.nonNull;
import static com.perforce.p4java.impl.mapbased.rpc.func.RpcFunctionMapKey.CODE0;
import static com.perforce.p4java.impl.mapbased.rpc.func.RpcFunctionMapKey.DEPOT_FILE;
import static com.perforce.p4java.impl.mapbased.rpc.func.RpcFunctionMapKey.DESC;

/**
 * @author Sean Shou
 * @since 23/09/2016
 */
public final class P4ResultMapUtils {
	private P4ResultMapUtils() { /* util */ }

	@Jdk7Nullable
	public static String parseString(@Jdk7Nonnull Map<String, Object> map, String key) {
		Object value = map.get(key);
		if (isNull(value)) {
			return null;
		} else {
			return String.valueOf(value);
		}
	}

	// p4ic4idea: should instead be part of an IServerMessage
        //@Nullable
	//public static String parseCode0ErrorString(@Nonnull Map<String, Object> map) {
	//	return parseString(map, CODE0);
	//}

	/**
	 * @throws NumberFormatException if the string does not contain a parsable integer.
	 */
	public static int parseInt(@Jdk7Nonnull Map<String, Object> map, String key) {
		return Integer.parseInt(String.valueOf(map.get(key)));
	}

	/**
	 * @throws NumberFormatException if the string does not contain a parsable integer.
	 */
	public static long parseLong(@Jdk7Nonnull Map<String, Object> map, String key) {
		return Long.parseLong(String.valueOf(map.get(key)));
	}

	public static boolean hasValidDepotPath(@Jdk7Nonnull final Map<String, Object> map) {
		return map.containsKey(DEPOT_FILE);
	}

	public static boolean containsDescriptionField(@Jdk7Nonnull final Map<String, Object> map) {
		return map.containsKey(DESC);
	}

	public static boolean isContainsValidRevisionSpecificInformation(@Jdk7Nonnull final Map<String, Object> map) {
		return hasValidDepotPath(map) && !containsDescriptionField(map);
	}

	public static List<String> parseDataList(@Jdk7Nonnull final Map<String, Object> map, String key) {
		List<String> dataList = new ArrayList<String>();
		int i = 0;
		while (nonNull(map.get(key + i))) {
			dataList.add(String.valueOf(map.get(key + i)));
			i++;
		}
		return dataList;
	}
}
