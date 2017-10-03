package com.perforce.p4java.impl.mapbased.server.cmd;

import static com.perforce.p4java.common.base.ObjectUtils.nonNull;
import static com.perforce.p4java.server.CmdSpec.FIX;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.perforce.p4java.core.IChangelist;
import com.perforce.p4java.core.IFix;
import com.perforce.p4java.exception.AccessException;
import com.perforce.p4java.exception.ConnectionException;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.exception.RequestException;
import com.perforce.p4java.impl.generic.core.Fix;
import com.perforce.p4java.impl.mapbased.server.Parameters;
import com.perforce.p4java.option.server.FixJobsOptions;
import com.perforce.p4java.server.IOptionsServer;
import com.perforce.p4java.server.delegator.IFixDelegator;
import com.perforce.p4java.util.compat.Jdk7Nonnull;
import com.perforce.p4java.util.compat.Validate;

/**
 * Implementation for 'p4 fix'.
 */
public class FixDelegator extends BaseDelegator implements IFixDelegator {

    /**
     * Instantiates a new fix delegator.
     *
     * @param server
     *            the server
     */
    public FixDelegator(final IOptionsServer server) {
        super(server);
    }

    @Override
    public List<IFix> fixJobs(final List<String> jobIds, final int changeListId,
            final String status, final boolean delete)
            throws ConnectionException, RequestException, AccessException {

        try {
            FixJobsOptions fixJobsOptions = new FixJobsOptions().setDelete(delete)
                    .setStatus(status);

            return fixJobs(jobIds, changeListId, fixJobsOptions);
            // TODO Why are P4JavaException and RequestException handled 
            // differently for each method?
        } catch (final ConnectionException exc) {
            throw exc;
        } catch (AccessException exc) {
            throw exc;
        } catch (RequestException exc) {
            throw exc;
        } catch (P4JavaException exc) {
            throw new RequestException(exc);
        }
    }

    @Override
    public List<IFix> fixJobs(@Jdk7Nonnull final List<String> jobIds, final int changeListId,
            final FixJobsOptions opts) throws P4JavaException {

        Validate.notNull(jobIds);

        String actualChangeId = "default";
        if (changeListId != IChangelist.DEFAULT) {
            actualChangeId = String.valueOf(changeListId);
        }

        List<String> args = new ArrayList<String>();
        args.add("-c" + actualChangeId);

        args.addAll(jobIds);
        List<IFix> fixList = new ArrayList<IFix>();
        List<Map<String, Object>> resultMaps = execMapCmdList(FIX, Parameters.processParameters(
                opts, null, args.toArray(new String[args.size()]), server), null);

        if (nonNull(resultMaps)) {
            for (Map<String, Object> map : resultMaps) {
                ResultMapParser.throwRequestExceptionIfErrorMessageFound(map);
                fixList.add(new Fix(map));
            }
        }

        return fixList;
    }
}
