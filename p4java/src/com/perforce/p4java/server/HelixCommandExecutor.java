package com.perforce.p4java.server;

import com.perforce.p4java.exception.AccessException;
import com.perforce.p4java.exception.ConnectionException;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.exception.RequestException;
import com.perforce.p4java.impl.mapbased.server.cmd.ResultMapParser;
import com.perforce.p4java.util.compat.Jdk7Nonnull;
import com.perforce.p4java.util.compat.Validate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class HelixCommandExecutor implements IHelixCommandExecutor {

    public List<Map<String, Object>> execMapCmdList(
            @Jdk7Nonnull final CmdSpec cmdSpec,
            String[] cmdArgs,
            Map<String, Object> inMap) throws ConnectionException, AccessException {

        Validate.notNull(cmdSpec);
        try {
            return execMapCmdList(cmdSpec.toString(), cmdArgs, inMap);
        } catch (final ConnectionException exc) {
            throw exc;
        } catch (AccessException exc) {
            throw exc;
        } catch (P4JavaException exc) {
            return Collections.emptyList();
        }
    }

    /**
     * @deprecated use {@link com.perforce.p4java.impl.mapbased.server.cmd.ResultMapParser#handleFileErrorStr(Map)}
     */
    @Deprecated
    public IServerMessage handleFileErrorStr(final Map<String, Object> map)
            throws ConnectionException, AccessException {
        return ResultMapParser.handleFileErrorStr(map);
    }

    /**
     * @deprecated use {@link com.perforce.p4java.impl.mapbased.server.cmd.ResultMapParser#handleErrorStr(Map)}
     */
    @Deprecated
    public boolean handleErrorStr(Map<String, Object> map)
            throws RequestException, AccessException {
        return ResultMapParser.handleErrorStr(map);
    }
}
