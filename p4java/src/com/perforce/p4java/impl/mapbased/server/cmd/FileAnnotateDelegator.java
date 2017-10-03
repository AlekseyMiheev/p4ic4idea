package com.perforce.p4java.impl.mapbased.server.cmd;

import static com.perforce.p4java.common.base.ObjectUtils.isNull;
import static com.perforce.p4java.common.base.ObjectUtils.nonNull;
import static com.perforce.p4java.common.base.P4JavaExceptions.throwRequestExceptionOnError;
import static com.perforce.p4java.common.base.P4ResultMapUtils.parseInt;
import static com.perforce.p4java.common.base.P4ResultMapUtils.parseString;
import static com.perforce.p4java.impl.mapbased.rpc.func.RpcFunctionMapKey.DEPOT_FILE;
import static com.perforce.p4java.impl.mapbased.server.Parameters.processParameters;
import static com.perforce.p4java.impl.mapbased.server.cmd.ResultMapParser.getErrorStr;
import static com.perforce.p4java.server.CmdSpec.ANNOTATE;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.perforce.p4java.Log;
import com.perforce.p4java.client.IClient;
import com.perforce.p4java.client.IClientSummary;
import com.perforce.p4java.core.file.DiffType;
import com.perforce.p4java.core.file.IFileAnnotation;
import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.exception.AccessException;
import com.perforce.p4java.exception.ConnectionException;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.exception.RequestException;
import com.perforce.p4java.impl.generic.core.file.FileAnnotation;
import com.perforce.p4java.option.server.GetFileAnnotationsOptions;
import com.perforce.p4java.server.IOptionsServer;
import com.perforce.p4java.server.IServerMessage;
import com.perforce.p4java.server.delegator.IFileAnnotateDelegator;
import com.perforce.p4java.util.compat.Jdk7Nonnull;

/**
 * Implementation to handle the Annotate command.
 */
public class FileAnnotateDelegator extends BaseDelegator implements IFileAnnotateDelegator {
    /**
     * Instantiate a new FileAnnotateDelegator, providing the server object that will be used to
     * execute Perforce Helix attribute commands.
     *
     * @param server a concrete implementation of a Perforce Helix Server
     */
    public FileAnnotateDelegator(IOptionsServer server) {
        super(server);
    }

    @Override
    public List<IFileAnnotation> getFileAnnotations(
            final List<IFileSpec> fileSpecs,
            @Jdk7Nonnull final DiffType diffType,
            final boolean allResults,
            final boolean useChangeNumbers,
            final boolean followBranches)
            throws ConnectionException, RequestException, AccessException {

        // p4ic4idea: this should be an illegal argument exception
        // throwRequestExceptionIfConditionFails(
        //       isNull(diffType) || diffType.isWsOption(),
        //        "Bad whitespace option in getFileAnnotations");
        if (isNull(diffType) || diffType.isWsOption()) {
            throw new IllegalArgumentException("Bad whitespace option in getFileAnnotations");
        }

        try {
            GetFileAnnotationsOptions getFileAnnotationsOptions = new GetFileAnnotationsOptions()
                    .setAllResults(allResults)
                    .setUseChangeNumbers(useChangeNumbers)
                    .setFollowBranches(followBranches)
                    .setWsOpts(diffType);
            return getFileAnnotations(fileSpecs, getFileAnnotationsOptions);
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
    public List<IFileAnnotation> getFileAnnotations(
            final List<IFileSpec> fileSpecs,
            final GetFileAnnotationsOptions opts) throws P4JavaException {

        List<IFileAnnotation> returnList = new ArrayList<IFileAnnotation>();

        List<Map<String, Object>> resultMaps = execMapCmdList(
                ANNOTATE,
                processParameters(opts, fileSpecs, server),
                null);

        if (nonNull(resultMaps)) {
            String depotFile = null;
            for (Map<String, Object> map : resultMaps) {
                if (nonNull(map)) {
                    // RPC version returns info, cmd version returns error... we
                    // throw
                    // an exception in either case.
                    IServerMessage errStr = getErrorStr(map);
                    throwRequestExceptionOnError(errStr);

                    // Note that this processing depends a bit on the current
                    // ordering of tagged results back from the server; if this
                    // changes, we may need to change things here as well...
                    if (isNewDepotFile(map)) {
                        // marks the start of annotations for
                        depotFile = parseString(map, DEPOT_FILE);
                    } else {
                        returnList.addAll(pickupDataAnnotationAndBuildFileAnnotation(
                                depotFile,
                                server.getCurrentClient(),
                                map)
                        );
                    }
                }
            }
        }

        return returnList;
    }

    /**
     * Look for any associated contributing integrations
     */
    private static void bindAssociatedContributingIntegrationsToFileAnnotation(
            @Jdk7Nonnull final Map<String, Object> map,
            @Jdk7Nonnull final FileAnnotation dataAnnotation) {

        for (int order = 0; map.containsKey(DEPOT_FILE + order); order++) {
            try {
                dataAnnotation.addIntegrationAnnotation(
                        new FileAnnotation(
                                order,
                                parseString(map, DEPOT_FILE + order),
                                parseInt(map, "upper" + order),
                                parseInt(map, "lower" + order))
                );
            } catch (Throwable thr) {
                Log.error("bad conversion in getFileAnnotations");
                Log.exception(thr);
            }
        }
    }

    private static boolean isNewDepotFile(Map<String, Object> map) {
        return map.containsKey(DEPOT_FILE);
    }

    /**
     * Pick up the "data" annotation
     */
    private static List<IFileAnnotation> pickupDataAnnotationAndBuildFileAnnotation(
            final String depotFile, final IClient currentClient,
            final Map<String, Object> map) {

        List<IFileAnnotation> returnList = new ArrayList<IFileAnnotation>();
        IClientSummary.ClientLineEnd lineEnd = null;
        if (nonNull(currentClient)) {
            lineEnd = currentClient.getLineEnd();
        }
        FileAnnotation dataAnnotation = new FileAnnotation(map, depotFile, lineEnd);

        returnList.add(dataAnnotation);
        bindAssociatedContributingIntegrationsToFileAnnotation(map, dataAnnotation);

        return returnList;
    }
}
