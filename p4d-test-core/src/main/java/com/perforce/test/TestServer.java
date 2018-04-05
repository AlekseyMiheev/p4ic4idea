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

package com.perforce.test;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DaemonExecutor;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteResultHandler;
import org.apache.commons.exec.ProcessDestroyer;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.ShutdownHookProcessDestroyer;

import javax.annotation.Nonnull;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.concurrent.Callable;

public class TestServer {
    private static final Charset P4D_ENCODING = Charset.forName("UTF-8");



    private static final String CASE_INSENSITIVE_ARG = "-C0";
    private static final String CASE_SENSITIVE_ARG = "-C1";


    private final File outDir;
    private final ProcessDestroyer processDestroyer = new ShutdownHookProcessDestroyer();
    private String version = "r17.1";
    private String user;
    private String port = "1666";
    private int monitor = 3;
    private boolean proxy;
    private boolean unicode = false;
    private boolean caseSensitive = false;

    @Nonnull
    private ExecuteStatus status = ExecuteStatus.createDummy();

    public TestServer() {
        this(new File("p4d.d"));
    }

    public TestServer(@Nonnull File outDir) {
        this.outDir = outDir;
    }

    @SuppressWarnings("WeakerAccess")
    public File getRootDir() {
        return outDir;
    }

    public String getPathToRoot() {
        return outDir.getAbsolutePath();
    }

    public void setP4dVersion(String version) {
        ensureNotRunning();
        this.version = version;
    }

    public void setUnicode(boolean unicode) {
        ensureNotRunning();
        this.unicode = unicode;
    }

    public void setCaseSensitive(boolean set) {
        this.caseSensitive = set;
    }

    public ExecutableSpecification getServerExecutableSpecification() {
        return codeline -> {
            if ("main".equals(codeline)) {
                setP4dVersion("r17.1");
            } else if (codeline.startsWith("p")) {
                setP4dVersion("r" + codeline.substring(1));
            } else {
                setP4dVersion(codeline);
            }
        };
    }

    public synchronized void initialize() throws IOException {
        initialize(null, null, null);
    }

    public synchronized void initialize(ClassLoader cl, String initialDepotResource, String checkpointResource)
            throws IOException {
        ensureNotRunning();
        delete();

        File p4d = extractP4d(outDir);
        if (initialDepotResource == null) {
            // Perform an initial startup of the server to initialize the database
            // This will create a checkpoint, and the empty file system will force
            // the creation of the initial depot files.
            execNoError("-jc");
        } else {
            P4ExtFileUtils.extractResource(cl, initialDepotResource, p4d.getParentFile(), true);
        }
        if (unicode) {
            execNoError("-xi");
        }
        if (checkpointResource != null) {
            File outfile = new File(p4d.getParentFile(), "checkpoint.gz");
            P4ExtFileUtils.extractResource(cl, initialDepotResource, outfile, false);
            execNoError("-z", "-jr", outfile.getAbsolutePath());
        }
        if (initialDepotResource != null && checkpointResource != null) {
            // upgrade the server files.
            execNoError("-xu");
        }
    }

    @Nonnull
    public String getLocalUrl() {
        return "p4java://localhost:" + port;
    }

    @Nonnull
    public String getRSHURL()
            throws IOException {
        File p4d = extractP4d(outDir);
        StringBuilder ret = new StringBuilder("p4jrsh://");
        ret
                .append(p4d.getAbsolutePath())
                .append(" -r ")
                .append(getPathToRoot())
                .append(' ');
        if (caseSensitive) {
            ret.append(CASE_SENSITIVE_ARG);
        } else {
            ret.append(CASE_INSENSITIVE_ARG);
        }
        ret.append(" -L log -i --java");
        return ret.toString();
    }


    /**
     * Start the server in the background.  This should only be used by tests that
     * need to test explicit socket connections.  Most tests should use the
     * RSH connection.
     *
     * @throws IOException on error
     */
    public synchronized void startAsync()
            throws IOException {
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IOException("could not create output dir " + outDir);
        }
        final File p4d = extractP4d(outDir);
        try {
            status.with(() -> {
                ensureNotRunning();
                CommandLine cmdLine = createBaseCmdLine(p4d);
                cmdLine
                        // disable journal
                        .addArgument("-J")
                        .addArgument("off")

                        // set logging level
                        .addArgument("-v")
                        .addArgument("subsystem=" + monitor);
                DaemonExecutor executor = new DaemonExecutor();
                final ExecuteStatus newStatus = new ExecuteStatus();
                executor.setProcessDestroyer(new ProcessDestroyer() {
                    @Override
                    public boolean add(Process process) {
                        newStatus.setProcess(process);
                        return processDestroyer.add(process);
                    }

                    @Override
                    public boolean remove(Process process) {
                        newStatus.setProcess(null);
                        return processDestroyer.remove(process);
                    }

                    @Override
                    public int size() {
                        return processDestroyer.size();
                    }
                });
                PumpStreamHandler streamHandler = new PumpStreamHandler(status.log);
                executor.setStreamHandler(streamHandler);
                executor.execute(cmdLine, newStatus);
                status = newStatus;
                return null;
            });
            // TODO need to correctly wait for the server to start.  This is a terrible way.
            // But then, we should really be using the rsh connection method, not the
            // async method.
            Thread.sleep(1000L);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Stop the server and delete the directory.
    public void delete()
            throws IOException {
        stopServer();
        if (outDir.exists()) {
            Files.walkFileTree(outDir.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    if (!file.toFile().delete()) {
                        throw new IOException("could not delete " + file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                        throws IOException {
                    if (!dir.toFile().delete()) {
                        throw new IOException("Could not delete " + dir);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    public String getUser() {
        return user;
    }

    /*
    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        ensureNotRunning();
        this.port = port;
    }

    public String getProxyPort() {
        return port;
    }
    */

    public void setMonitor(int monitor) {
        ensureNotRunning();
        this.monitor = monitor;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void importRecord(String journalRecord)
            throws IOException {
        // Really, should use "p4 pull" or "p4 replicate"
        File tmp = File.createTempFile("journal", ".jnl");
        try {
            try (FileWriter out = new FileWriter(tmp)) {
                out.write(journalRecord + "\n");
                out.flush();
            }
            execNoError("-jr", tmp.getAbsolutePath());
        } finally {
            tmp.delete();
        }
    }

    public String getLog() {
        return new String(status.log.toByteArray(), P4D_ENCODING);
    }

    public void setProxy(boolean proxy) {
        this.proxy = proxy;
    }

    public synchronized void stopServer() {
        try {
            status.with(() -> {
                if (status.isRunning()) {
                    if (status.process != null) {
                        status.process.destroy();
                    }
                    status.waitFor(1000000L);
                    status = ExecuteStatus.createDummy();
                }
                return null;
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("WeakerAccess")
    public void execNoError(String... args) throws IOException {
        int res = exec(args);
        if (res != 0) {
            throw new IOException("Execution of p4d " +
                    Arrays.asList(args) +
                    " caused result code " + res);
        }
    }

    /**
     * Call and wait execution of the p4d server.
     *
     * @param args p4d arguments
     * @return status code
     * @throws IOException on error
     */
    public int exec(String... args)
            throws IOException {
        try {
            return status.with(() -> {
                ensureNotRunning();
                return innerExec(args);
            });
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String getP4dPath(String version)
            throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return "bin/" + version + "/bin.ntx64/p4d.exe";
        }
        if (os.contains("mac")) {
            return "bin/" + version + "/bin.darwin90x86_64/p4d";
        }
        if (os.contains("nix") || os.contains("nux")) {
            return "bin/" + version + "/bin.linux26x86_64/p4d";
        }
        throw new IOException("No p4d registered for OS " + os);
    }

    private File getP4dOutput(File outdir) {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return new File(outdir, "p4d.exe");
        }
        return new File(outdir, "p4d");
    }

    private File extractP4d(File outdir)
            throws IOException {
        File outP4d = getP4dOutput(outdir);
        if (!outP4d.exists()) {
            if (!outdir.exists() && !outdir.mkdirs()) {
                throw new IOException("could not create output dir " + outdir);
            }
            String osP4d = getP4dPath(version);
            P4ExtFileUtils.extractResource(getClass().getClassLoader(), osP4d, outP4d, false);
            if (!outP4d.setExecutable(true)) {
                throw new IOException("Could not make executable: " + outP4d);
            }
        }
        return outP4d;
    }

    private synchronized void ensureNotRunning() {
        if (status.isRunning()) {
            throw new IllegalStateException("Server is actively running; cannot run another command.");
        }
    }

    private int innerExec(String... args)
            throws IOException {
        File p4d = extractP4d(outDir);
        CommandLine cmdLine = createBaseCmdLine(p4d);
        for (String arg : args) {
            cmdLine.addArgument(arg);
        }
        DefaultExecutor executor = new DefaultExecutor();
        executor.setProcessDestroyer(processDestroyer);
        return executor.execute(cmdLine);
    }

    @Nonnull
    private CommandLine createBaseCmdLine(@Nonnull File p4d) {
        CommandLine cmdLine = new CommandLine(p4d);
        cmdLine
                .addArgument("-r")
                .addArgument(getPathToRoot())
                .addArgument("-p")
                .addArgument(port);
        if (caseSensitive) {
            cmdLine.addArgument(CASE_SENSITIVE_ARG);
        } else {
            cmdLine.addArgument(CASE_INSENSITIVE_ARG);
        }
        return cmdLine;
    }

    private static class ExecuteStatus implements ExecuteResultHandler {
        private final Object sync = new Object();
        private boolean running = true;
        private int exitCode = 0;
        private ExecuteException error;
        private Process process;
        public ByteArrayOutputStream log = new ByteArrayOutputStream();


        static ExecuteStatus create() {
            return new ExecuteStatus();
        }

        static ExecuteStatus createDummy() {
            ExecuteStatus ret = new ExecuteStatus();
            ret.running = false;
            return ret;
        }

        void setProcess(Process process) {
            synchronized (sync) {
                this.process = process;
            }
        }

        public <T> T with(Callable<T> c)
                throws Exception {
            synchronized (sync) {
                return c.call();
            }
        }

        public boolean isRunning() {
            synchronized (sync) {
                return running;
            }
        }

        @Override
        public void onProcessComplete(int exitValue) {
            synchronized (sync) {
                running = false;
                exitCode = exitValue;
                sync.notifyAll();
            }
        }

        @Override
        public void onProcessFailed(ExecuteException e) {
            synchronized (sync) {
                running = false;
                error = e;
                sync.notifyAll();
            }
        }

        void waitFor(long timeoutMs)
                throws InterruptedException {
            synchronized (sync) {
                while (running) {
                    sync.wait(timeoutMs);
                }
            }
        }
    }
}