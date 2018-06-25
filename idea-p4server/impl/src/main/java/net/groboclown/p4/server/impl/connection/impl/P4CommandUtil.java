/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.groboclown.p4.server.impl.connection.impl;

import com.perforce.p4java.client.IClient;
import com.perforce.p4java.core.CoreFactory;
import com.perforce.p4java.core.IChangelist;
import com.perforce.p4java.core.IChangelistSummary;
import com.perforce.p4java.core.IJob;
import com.perforce.p4java.core.IJobSpec;
import com.perforce.p4java.core.file.FileSpecBuilder;
import com.perforce.p4java.core.file.IExtendedFileSpec;
import com.perforce.p4java.core.file.IFileAnnotation;
import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.exception.AccessException;
import com.perforce.p4java.exception.ConnectionException;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.exception.RequestException;
import com.perforce.p4java.option.changelist.SubmitOptions;
import com.perforce.p4java.option.client.AddFilesOptions;
import com.perforce.p4java.option.client.DeleteFilesOptions;
import com.perforce.p4java.option.client.EditFilesOptions;
import com.perforce.p4java.option.client.RevertFilesOptions;
import com.perforce.p4java.option.server.ChangelistOptions;
import com.perforce.p4java.option.server.GetChangelistsOptions;
import com.perforce.p4java.option.server.GetExtendedFilesOptions;
import com.perforce.p4java.option.server.GetFileAnnotationsOptions;
import com.perforce.p4java.server.IOptionsServer;
import com.perforce.p4java.server.IServer;
import net.groboclown.p4.server.api.values.JobStatus;
import net.groboclown.p4.server.api.values.P4ChangelistId;
import net.groboclown.p4.server.api.values.P4FileType;
import net.groboclown.p4.server.api.values.P4Job;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Keeps all the different server commands that need to be run in one place.  Allows for
 * easier unit test code reuse without needing to call into every nuance of the runners.
 * <p>
 * These run all the p4java API commands, without translating the results or inputs to/from
 * the plugin types.
 * <p>
 * As a side effect, this maintains a catalogue of commands used by the plugin.  This
 * allows for an easier effort to focus on API compatibility and correctness.
 */
public class P4CommandUtil {
    public List<IExtendedFileSpec> getFilesOpenInDefaultChangelist(IServer server,
            String clientName, int maxFileResults)
            throws P4JavaException {
        GetExtendedFilesOptions options = new GetExtendedFilesOptions("-Olhp -Rco -e default");
        options.setMaxResults(maxFileResults);
        return server.getExtendedFiles(
                FileSpecBuilder.makeFileSpecList("//" + clientName + "/..."),
                options
        );
    }

    public List<IExtendedFileSpec> getFileDetailsForOpenedSpecs(IServer server, List<IFileSpec> sources,
            int maxFileResults)
            throws P4JavaException {
        GetExtendedFilesOptions options = new GetExtendedFilesOptions("-Olhp");
        options.setMaxResults(maxFileResults);
        return server.getExtendedFiles(sources, options);
    }

    public IChangelist getChangelistDetails(IServer server, int changelistId)
            throws P4JavaException {
        ChangelistOptions co = new ChangelistOptions();
        co.setIncludeFixStatus(true);
        return server.getChangelist(changelistId, co);
    }

    public List<IFileSpec> getShelvedFiles(IServer server, int changelistId, int maxFileResults)
            throws P4JavaException {
        return server.getShelvedFiles(changelistId, maxFileResults);
    }

    public List<IChangelistSummary> getPendingChangelists(IClient client, int maxChangelistResults)
            throws P4JavaException {
        GetChangelistsOptions clOptions = new GetChangelistsOptions(
                maxChangelistResults, client.getName(), client.getServer().getUserName(),
                true, IChangelist.Type.PENDING, false
        );
        return client.getServer().getChangelists(null, clOptions);
    }

    public IJob createJob(IOptionsServer server, Map<String, Object> fields)
            throws ConnectionException, AccessException, RequestException {
        return server.createJob(fields);
    }

    public IJob getJob(IServer server, String jobId)
            throws ConnectionException, AccessException, RequestException {
        return server.getJob(jobId);
    }

    public IJobSpec getJobSpec(IOptionsServer server)
            throws ConnectionException, AccessException, RequestException {
        return server.getJobSpec();
    }

    /**
     *
     * @param server server
     * @param files escaped name of the files
     * @return annotated file information
     */
    public List<IFileAnnotation> getAnnotations(IServer server, List<IFileSpec> files)
            throws P4JavaException {
        GetFileAnnotationsOptions options = new GetFileAnnotationsOptions(
                false, // allResults
                false, // useChangeNumbers
                false, // followBranches
                false, // ignoreWhitespaceChanges
                false, // ignoreWhitespace
                true, // ignoreLineEndings
                false // followAllIntegrations
        );
        return server.getFileAnnotations(files, options);
    }

    /**
     *
     * @param client client
     * @param files non-escaped name of the files.
     * @param fileType file type, or null if not specified
     * @param changelistId changelist ID, or null if default changelist.
     * @param charset charset, or null if not specified
     * @return result of adding the files.
     */
    public List<IFileSpec> addFiles(IClient client, List<IFileSpec> files, P4FileType fileType,
            P4ChangelistId changelistId,
            String charset)
            throws P4JavaException {
        AddFilesOptions addOptions = new AddFilesOptions();
        if (fileType != null) {
            addOptions.setFileType(fileType.toString());
        }
        if (changelistId != null && !changelistId.isDefaultChangelist()) {
            addOptions.setChangelistId(changelistId.getChangelistId());
        }
        addOptions.setUseWildcards(true);
        if (charset != null) {
            addOptions.setCharset(charset);
        }
        return client.addFiles(files, addOptions);
    }


    /**
     *
     * @param client client
     * @param files escaped file specs
     * @param onlyUnchanged true if revert only unchanged files.
     * @return result
     * @throws P4JavaException underlying error
     */
    public List<IFileSpec> revertFiles(IClient client, List<IFileSpec> files, boolean onlyUnchanged)
            throws P4JavaException {
        RevertFilesOptions options = new RevertFilesOptions();
        options.setRevertOnlyUnchanged(onlyUnchanged);
        return client.revertFiles(files, options);
    }

    /**
     *
     * @param client client
     * @param files escaped file specs
     * @param fileType file type
     * @param changelistId changelist to edit the file in
     * @param charset charset
     * @return edit file results
     * @throws P4JavaException underlying error
     */
    public List<IFileSpec> editFiles(IClient client, List<IFileSpec> files, P4FileType fileType,
            P4ChangelistId changelistId, String charset)
            throws P4JavaException {
        EditFilesOptions editOptions = new EditFilesOptions();
        if (fileType != null) {
            editOptions.setFileType(fileType.toString());
        }
        if (changelistId != null && !changelistId.isDefaultChangelist()) {
            editOptions.setChangelistId(changelistId.getChangelistId());
        }
        if (charset != null) {
            editOptions.setCharset(charset);
        }
        return client.editFiles(files, editOptions);
    }

    public List<IFileSpec> deleteFiles(IClient client, List<IFileSpec> files, P4ChangelistId changelistId)
            throws P4JavaException {
        DeleteFilesOptions options = new DeleteFilesOptions();
        if (changelistId != null && changelistId.getState() == P4ChangelistId.State.NUMBERED) {
            options.setChangelistId(changelistId.getChangelistId());
        }

        // Let the IDE perform the delete.
        // FIXME double check if this is right.
        options.setBypassClientDelete(true);

        return client.deleteFiles(files, options);
    }

    public List<IFileSpec> submitChangelist(
            JobStatus jobStatus,
            Collection<P4Job> updatedJobs,
            IChangelist changelist)
            throws P4JavaException {
        SubmitOptions options = new SubmitOptions();
        if (jobStatus != null) {
            options.setJobStatus(jobStatus.getName());
        }
        if (updatedJobs != null) {
            List<String> jobIds = new ArrayList<>(updatedJobs.size());
            for (P4Job p4Job : updatedJobs) {
                jobIds.add(p4Job.getJobId());
            }
            options.setJobIds(jobIds);
        }
        return changelist.submit(options);
    }

    public IChangelist createChangelist(IClient client, String description)
            throws P4JavaException {
        return CoreFactory.createChangelist(client, description, true);
    }
}
