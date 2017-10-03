package com.perforce.p4java.impl.mapbased.server.cmd;

import static com.perforce.p4java.common.base.ObjectUtils.nonNull;
import static com.perforce.p4java.server.CmdSpec.GROUP;

import java.util.List;
import java.util.Map;

import com.perforce.p4java.core.IUserGroup;
import com.perforce.p4java.exception.AccessException;
import com.perforce.p4java.exception.ConnectionException;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.exception.RequestException;
import com.perforce.p4java.impl.generic.core.InputMapper;
import com.perforce.p4java.impl.generic.core.UserGroup;
import com.perforce.p4java.impl.mapbased.server.Parameters;
import com.perforce.p4java.option.server.UpdateUserGroupOptions;
import com.perforce.p4java.server.IOptionsServer;
import com.perforce.p4java.server.delegator.IGroupDelegator;
import com.perforce.p4java.util.compat.Jdk7Nonnull;
import com.perforce.p4java.util.compat.Jdk7Nullable;
import com.perforce.p4java.util.compat.Validate;

/**
 * @author Sean Shou
 * @since 12/09/2016
 */
public class GroupDelegator extends BaseDelegator implements IGroupDelegator {

    /**
     * Instantiate a new GroupDelegator for the given server implementation.
     * 
     * @param server
     *            the server to delegate for
     */
    public GroupDelegator(IOptionsServer server) {
        super(server);
    }

    @Override
    public IUserGroup getUserGroup(@Jdk7Nonnull final String name)
            throws ConnectionException, RequestException, AccessException {
        Validate.notBlank(name, "Group name shouldn't use null or empty");

        List<Map<String, Object>> resultMaps = execMapCmdList(GROUP, new String[] { "-o", name },
                null);
        UserGroup ugImpl = null;

        if (nonNull(resultMaps)) {
            Map<String, Object> firstResultMap = resultMaps.get(0);
            if (nonNull(firstResultMap)) {
                ResultMapParser.handleErrorStr(firstResultMap);
                ugImpl = new UserGroup(firstResultMap);
            }
        }

        return ugImpl;
    }

    @Override
    public String deleteUserGroup(@Jdk7Nonnull IUserGroup group, @Jdk7Nullable UpdateUserGroupOptions opts)
            throws P4JavaException {

        Validate.notNull(group);
        Validate.notBlank(group.getName(), "Group name shouldn't a null or empty.");

        List<Map<String, Object>> resultMaps = execMapCmdList(GROUP, Parameters
                .processParameters(opts, null, new String[] { "-d", group.getName() }, server), null);

        return ResultMapParser.parseCommandResultMapAsString(resultMaps);
    }

    @Override
    public String createUserGroup(@Jdk7Nonnull final IUserGroup group,
            @Jdk7Nullable final UpdateUserGroupOptions opts) throws P4JavaException {
        return updateUserGroup(group, opts);
    }

    @Override
    public String updateUserGroup(@Jdk7Nonnull final IUserGroup group,
            @Jdk7Nullable final UpdateUserGroupOptions opts) throws P4JavaException {

        Validate.notNull(group);

        List<Map<String, Object>> resultMaps = execMapCmdList(GROUP,
                Parameters.processParameters(opts, null, "-i", server), InputMapper.map(group));

        return ResultMapParser.parseCommandResultMapAsString(resultMaps);
    }

    /**
     * Implemented on behalf of IServer for backwards compatibility.
     * 
     */
    public String createUserGroup(@Jdk7Nonnull final IUserGroup group)
            throws ConnectionException, RequestException, AccessException {
        Validate.notNull(group);
        return updateUserGroup(group, false);
    }

    /**
     * Implemented on behalf of IServer for backwards compatibility.
     * 
     */
    public String updateUserGroup(@Jdk7Nonnull final IUserGroup group, final boolean updateIfOwner)
            throws ConnectionException, RequestException, AccessException {
        try {
            return updateUserGroup(group,
                    new UpdateUserGroupOptions().setUpdateIfOwner(updateIfOwner));
        } catch (final ConnectionException exc) {
            throw exc;
        } catch (AccessException exc) {
            throw exc;
        } catch (P4JavaException exc) {
            throw new RequestException(exc);
        }
    }

    /**
     * Implemented on behalf of IServer for backwards compatibility.
     * 
     */
    public String deleteUserGroup(@Jdk7Nonnull final IUserGroup group)
            throws ConnectionException, RequestException, AccessException {
        try {
            return deleteUserGroup(group, new UpdateUserGroupOptions());
        } catch (final ConnectionException exc) {
            throw exc;
        } catch (AccessException exc) {
            throw exc;
        } catch (P4JavaException exc) {
            throw new RequestException(exc);
        }
    }
}
