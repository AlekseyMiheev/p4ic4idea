package com.perforce.p4java.impl.mapbased.server.cmd;

import static com.perforce.p4java.common.base.ObjectUtils.nonNull;
import static com.perforce.p4java.server.CmdSpec.JOB;
import static com.perforce.p4java.util.compat.StringUtils.SPACE;
import static com.perforce.p4java.util.compat.StringUtils.contains;
import static com.perforce.p4java.util.compat.StringUtils.isNotBlank;
import static com.perforce.p4java.util.compat.StringUtils.split;

import java.util.List;
import java.util.Map;

import com.perforce.p4java.common.function.Function;
import com.perforce.p4java.core.IJob;
import com.perforce.p4java.exception.AccessException;
import com.perforce.p4java.exception.ConnectionException;
import com.perforce.p4java.exception.RequestException;
import com.perforce.p4java.impl.generic.core.Job;
import com.perforce.p4java.server.IOptionsServer;
import com.perforce.p4java.server.IServerMessage;
import com.perforce.p4java.server.delegator.IJobDelegator;
import com.perforce.p4java.util.compat.Jdk7Nonnull;
import com.perforce.p4java.util.compat.Validate;

/**
 * Implementation for 'p4 job'..
 */
public class JobDelegator extends BaseDelegator implements IJobDelegator {

    /**
     * Instantiates a new job delegator.
     *
     * @param server
     *            the server
     */
    public JobDelegator(final IOptionsServer server) {
        super(server);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.perforce.p4java.server.delegator.IJobDelegator#createJob(java.util.
     * Map)
     */
    @Override
    public IJob createJob(@Jdk7Nonnull final Map<String, Object> fieldMap)
            throws ConnectionException, RequestException, AccessException {
        Validate.notNull(fieldMap);
        final int wordLength = 3;
        List<Map<String, Object>> resultMaps = execMapCmdList(JOB, new String[] { "-i" }, fieldMap);

        if (nonNull(resultMaps)) {
            // What comes back is a simple info message that contains the
            // job ID, a trigger output info message, or an error message; in
            // the first instance we retrieve
            // the new ID then get the job; otherwise we throw the error.
            for (Map<String, Object> map : resultMaps) {
                ResultMapParser.handleErrorStr(map);
                // p4ic4idea: need to get the actual message
                IServerMessage infoStrMsg = ResultMapParser.getInfoStr(map);
                String infoStr = infoStrMsg == null ? null : infoStrMsg.getLocalizedMessage();

                if (contains(infoStr, "Job ") && contains(infoStr, " saved")) {
                    // usually in format "Job jobid saved"
                    String[] words = split(infoStr, SPACE);
                    if (words.length == wordLength) {
                        String potentialJobId = words[1];
                        if (isNotBlank(potentialJobId)) {
                            return getJob(potentialJobId);
                        }
                    }
                }
            }
        }

        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.perforce.p4java.server.delegator.IJobDelegator#deleteJob(java.lang.
     * String)
     */
    @Override
    public String deleteJob(final String jobId)
            throws ConnectionException, RequestException, AccessException {
        Validate.notBlank(jobId, "JobId shouldn't null or empty");

        List<Map<String, Object>> resultMaps = execMapCmdList(JOB, new String[] { "-d", jobId },
                null);

        return ResultMapParser.parseCommandResultMapIfIsInfoMessageAsString(resultMaps);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.perforce.p4java.server.delegator.IJobDelegator#getJob(java.lang.
     * String)
     */
    @SuppressWarnings("unchecked")
    @Override
    public IJob getJob(final String jobId)
            throws ConnectionException, RequestException, AccessException {
        Validate.notBlank(jobId, "JobId shouldn't null or empty");

        List<Map<String, Object>> resultMaps = execMapCmdList(JOB, new String[] { "-o", jobId },
                null);

        return ResultListBuilder.buildNullableObjectFromNonInfoMessageCommandResultMaps(
                resultMaps,
                new Function<Map<String, Object>, IJob>() {
                    @Override
                    public IJob apply(Map<String, Object> map) {
                        return new Job(server, map);
                    }
                }
        );
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.perforce.p4java.server.delegator.IJobDelegator#updateJob(com.perforce
     * .p4java.core.IJob)
     */
    @Override
    public String updateJob(@Jdk7Nonnull final IJob job)
            throws ConnectionException, RequestException, AccessException {
        Validate.notNull(job);
        List<Map<String, Object>> resultMaps = execMapCmdList(JOB, new String[] { "-i" },
                job.getRawFields());

        return ResultMapParser.parseCommandResultMapIfIsInfoMessageAsString(resultMaps);
    }
}
