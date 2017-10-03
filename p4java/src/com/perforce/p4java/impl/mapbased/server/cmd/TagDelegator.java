package com.perforce.p4java.impl.mapbased.server.cmd;

import static com.perforce.p4java.common.base.P4JavaExceptions.rethrowFunction;
import static com.perforce.p4java.impl.mapbased.server.Parameters.processParameters;
import static com.perforce.p4java.impl.mapbased.server.cmd.ResultListBuilder.buildNonNullObjectListFromCommandResultMaps;
import static com.perforce.p4java.server.CmdSpec.TAG;
import static com.perforce.p4java.util.compat.StringUtils.EMPTY;
import static com.perforce.p4java.util.compat.StringUtils.isNotBlank;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.perforce.p4java.Log;
import com.perforce.p4java.common.function.FunctionWithException;
import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.exception.AccessException;
import com.perforce.p4java.exception.ConnectionException;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.exception.RequestException;
import com.perforce.p4java.option.server.TagFilesOptions;
import com.perforce.p4java.server.IOptionsServer;
import com.perforce.p4java.server.delegator.ITagDelegator;

/**
 * Implementation to handle the Tag command.
 */
public class TagDelegator extends BaseDelegator implements ITagDelegator {
    /**
     * Instantiate a new TagDelegator, providing the server object that will be used to
     * execute Perforce Helix attribute commands.
     *
     * @param server a concrete implementation of a Perforce Helix Server
     */
    public TagDelegator(IOptionsServer server) {
        super(server);
    }

    @Override
    public List<IFileSpec> tagFiles(
            final List<IFileSpec> fileSpecs,
            final String labelName,
            final boolean listOnly,
            final boolean delete)
            throws ConnectionException, RequestException, AccessException {

        try {
            TagFilesOptions tagFilesOptions = new TagFilesOptions()
                    .setDelete(delete)
                    .setListOnly(listOnly);
            return tagFiles(fileSpecs, labelName, tagFilesOptions);
        } catch (final ConnectionException exc) {
            throw exc;
        } catch (AccessException exc) {
            throw exc;
        } catch (P4JavaException exc) {
            // p4ic4idea: TODO should this still return an empty collection, not raise an exception?
            Log.warn("Unexpected exception in IServer.getDepotFiles: %s", exc);
            return Collections.emptyList();
        }
    }

    @Override
    public List<IFileSpec> tagFiles(
            final List<IFileSpec> fileSpecs,
            final String labelName,
            final TagFilesOptions opts) throws P4JavaException {

        String labelOpt = EMPTY;
        if (isNotBlank(labelName)) {
            labelOpt = "-l" + labelName;
        }

        List<Map<String, Object>> resultMaps = execMapCmdList(
                TAG,
                processParameters(
                        opts,
                        fileSpecs,
                        labelOpt,
                        server),
                null);

        return buildNonNullObjectListFromCommandResultMaps(
                resultMaps,
                rethrowFunction(new FunctionWithException<Map<String, Object>, IFileSpec>() {
                    @Override
                    public IFileSpec apply(Map<String, Object> map) throws P4JavaException {
                        return ResultListBuilder.handleFileReturn(map, server);
                    }
                })
        );
    }
}
