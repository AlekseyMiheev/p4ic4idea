package com.perforce.p4java.impl.mapbased.server.cmd;

import static com.perforce.p4java.common.base.ObjectUtils.isNull;
import static com.perforce.p4java.common.base.ObjectUtils.nonNull;
import static com.perforce.p4java.common.base.P4ResultMapUtils.parseString;
import static com.perforce.p4java.common.base.StringHelper.format;
import static com.perforce.p4java.impl.mapbased.MapKeys.ACTION_KEY;
import static com.perforce.p4java.impl.mapbased.MapKeys.NAME_KEY;
import static com.perforce.p4java.impl.mapbased.MapKeys.SERVER_NAME_KEY;
import static com.perforce.p4java.impl.mapbased.MapKeys.VALUE_KEY;
import static com.perforce.p4java.impl.mapbased.rpc.func.RpcFunctionMapKey.SET;
import static com.perforce.p4java.impl.mapbased.rpc.func.RpcFunctionMapKey.UNSET;
import static com.perforce.p4java.server.CmdSpec.CONFIGURE;
import static com.perforce.p4java.util.compat.StringUtils.isNotBlank;

import java.util.List;
import java.util.Map;

import com.perforce.p4java.admin.ServerConfigurationValue;
import com.perforce.p4java.common.function.Function;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.server.IOptionsServer;
import com.perforce.p4java.server.IServerMessage;
import com.perforce.p4java.server.delegator.IConfigureDelegator;
import com.perforce.p4java.util.compat.Jdk7Nonnull;
import com.perforce.p4java.util.compat.Jdk7Nullable;
import com.perforce.p4java.util.compat.Validate;

/**
 * Handles configure commands.
 */
public class ConfigureDelegator extends BaseDelegator implements IConfigureDelegator {

    /**
     * Size to initialize String builders.
     */
    private static final int STRING_BUILDER_SIZE = 100;

    /**
     * The command for show.
     */
    private static final String SHOW_CMD = "show";

    /**
     * Instantiates a new configure delegator.
     *
     * @param server the server
     */
    public ConfigureDelegator(final IOptionsServer server) {
        super(server);
    }

    @Override
    public String setOrUnsetServerConfigurationValue(@Jdk7Nonnull final String name,
                                                     @Jdk7Nullable final String value) throws P4JavaException {

        Validate.notBlank(name, "Config name shouldn't null or empty");
        String[] args = new String[]{UNSET, name};
        if (isNotBlank(value)) {
            args = new String[]{SET, name + "=" + value};
        }

        List<Map<String, Object>> resultMaps = execMapCmdList(CONFIGURE, args, null);

        StringBuilder messageBuilder = new StringBuilder(STRING_BUILDER_SIZE);
        if (nonNull(resultMaps)) {
            for (Map<String, Object> map : resultMaps) {
                if (nonNull(map)) {
                    IServerMessage str = ResultMapParser.getErrorOrInfoStr(map);
                    if (isNull(str)) {
                        // Handling the new message format for Perforce server
                        // version 2011.1; also maintain backward compatibility.
                        if (map.containsKey(NAME_KEY)) {
                            if (map.containsKey(ACTION_KEY) && nonNull(map.get(ACTION_KEY))) {
                                String message = null;
                                String serverName = parseString(map, SERVER_NAME_KEY);
                                String configureName = parseString(map, NAME_KEY);
                                String configureValue = parseString(map, VALUE_KEY);
                                String action = parseString(map, ACTION_KEY);
                                if (SET.equalsIgnoreCase(action)) {
                                    message = format(
                                            "For server '%s', configuration variable '%s' "
                                                    + "set to '%s'",
                                            serverName, configureName, configureValue);
                                } else if (UNSET.equalsIgnoreCase(action)) {
                                    message = format(
                                            "For server '%s', configuration variable '%s' removed.",
                                            serverName, configureName);
                                }

                                if (isNotBlank(message)) {
                                    messageBuilder.append(message).append("\n");
                                }
                            }
                        }
                    } else {
                        // p4ic4idea: shouldn't this return the error?
                        return str.getLocalizedMessage();
                    }
                }
            }
        }
        return messageBuilder.toString();
    }

    @Override
    public List<ServerConfigurationValue> showServerConfiguration(final String serverName,
                                                                  final String variableName) throws P4JavaException {
        String[] args = new String[]{SHOW_CMD};
        // Only one of serverName or variableName should be set:
        if (isNotBlank(serverName)) {
            args = new String[]{SHOW_CMD, serverName};
        } else if (isNotBlank(variableName)) {
            args = new String[]{SHOW_CMD, variableName};
        }

        List<Map<String, Object>> resultMaps = execMapCmdList(CONFIGURE, args, null);

        return ResultListBuilder.buildNonNullObjectListFromCommandResultMaps(resultMaps,
                new Function<Map<String, Object>, ServerConfigurationValue>() {
                    @Override
                    public ServerConfigurationValue apply(Map<String, Object> map) {
                        return new ServerConfigurationValue(map);
                    }
                });
    }
}
