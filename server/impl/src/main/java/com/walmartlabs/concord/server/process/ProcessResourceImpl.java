package com.walmartlabs.concord.server.process;

import com.walmartlabs.concord.common.Constants;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.api.history.ProcessHistoryEntry;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.api.process.ProcessStatusResponse;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import com.walmartlabs.concord.server.cfg.AttachmentStoreConfiguration;
import com.walmartlabs.concord.server.history.ProcessHistoryDao;
import com.walmartlabs.concord.server.process.pipelines.ProjectPipeline;
import com.walmartlabs.concord.server.process.pipelines.RequestDataOnlyPipeline;
import com.walmartlabs.concord.server.process.pipelines.SelfContainedArchivePipeline;
import com.walmartlabs.concord.server.project.ProjectDao;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.PathParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Named
public class ProcessResourceImpl implements ProcessResource, Resource {

    private static final Logger log = LoggerFactory.getLogger(ProcessResourceImpl.class);

    private final ProjectDao projectDao;
    private final ProcessHistoryDao historyDao;
    private final ProjectPipeline projectPipeline;
    private final SelfContainedArchivePipeline archivePipeline;
    private final RequestDataOnlyPipeline requestPipeline;
    private final ProcessExecutorImpl processExecutor;
    private final AttachmentStoreConfiguration attachmentCfg;

    @Inject
    public ProcessResourceImpl(ProjectDao projectDao,
                               ProcessHistoryDao historyDao,
                               ProjectPipeline projectPipeline,
                               SelfContainedArchivePipeline archivePipeline,
                               RequestDataOnlyPipeline requestPipeline,
                               ProcessExecutorImpl processExecutor,
                               AttachmentStoreConfiguration attachmentCfg) {

        this.projectDao = projectDao;
        this.historyDao = historyDao;
        this.projectPipeline = projectPipeline;
        this.archivePipeline = archivePipeline;
        this.requestPipeline = requestPipeline;
        this.processExecutor = processExecutor;
        this.attachmentCfg = attachmentCfg;
    }

    @Override
    public StartProcessResponse start(InputStream in) {
        String instanceId = UUID.randomUUID().toString();
        Payload payload = createPayload(instanceId, in);
        archivePipeline.process(payload);
        return new StartProcessResponse(instanceId);
    }

    @Override
    public StartProcessResponse start(@PathParam("entryPoint") String entryPoint, Map<String, Object> req) {
        String instanceId = UUID.randomUUID().toString();
        Payload payload = createPayload(instanceId, entryPoint, req);
        requestPipeline.process(payload);
        return new StartProcessResponse(instanceId);
    }

    @Override
    public StartProcessResponse start(String entryPoint, MultipartInput input) {
        String instanceId = UUID.randomUUID().toString();
        Payload payload = createPayload(instanceId, entryPoint, input);
        projectPipeline.process(payload);
        return new StartProcessResponse(instanceId);
    }

    /**
     * Creates a payload. It is implied that all necessary resources to start a process are
     * supplied in the multipart data.
     *
     * @param instanceId
     * @param input
     * @return
     */
    private Payload createPayload(String instanceId, String entryPoint, MultipartInput input) {
        try {
            Path baseDir = Files.createTempDirectory("request");
            Path workspaceDir = Files.createDirectory(baseDir.resolve("workspace"));
            log.debug("createPayload ['{}'] -> baseDir: {}", instanceId, baseDir);

            Payload p = PayloadParser.parse(instanceId, baseDir, input)
                    .putHeader(Payload.WORKSPACE_DIR, workspaceDir);

            return parseEntryPoint(p, entryPoint);
        } catch (IOException e) {
            throw new ProcessException("Error while parsing a request", e);
        }
    }

    /**
     * Creates a payload from the supplied map of parameters.
     *
     * @param instanceId
     * @param request
     * @return
     */
    private Payload createPayload(String instanceId, String entryPoint, Map<String, Object> request) {
        try {
            Path baseDir = Files.createTempDirectory("request");
            Path workspaceDir = Files.createDirectory(baseDir.resolve("workspace"));
            log.debug("createPayload ['{}'] -> baseDir: {}", instanceId, baseDir);

            Payload p = new Payload(instanceId)
                    .putHeader(Payload.WORKSPACE_DIR, workspaceDir)
                    .mergeValues(Payload.REQUEST_DATA_MAP, request);

            return parseEntryPoint(p, entryPoint);
        } catch (IOException e) {
            throw new ProcessException("Error while parsing a request", e);
        }
    }

    /**
     * Creates a payload from an archive, containing all necessary resources.
     *
     * @param instanceId
     * @param in
     * @return
     */
    private Payload createPayload(String instanceId, InputStream in) {
        try {
            Path baseDir = Files.createTempDirectory("request");
            Path workspaceDir = Files.createDirectory(baseDir.resolve("workspace"));
            log.debug("createPayload ['{}'] -> baseDir: {}", instanceId, baseDir);

            Path archive = baseDir.resolve("_input.zip");
            Files.copy(in, archive);

            return new Payload(instanceId)
                    .putHeader(Payload.WORKSPACE_DIR, workspaceDir)
                    .putAttachment(Payload.WORKSPACE_ARCHIVE, archive);
        } catch (IOException e) {
            throw new ProcessException("Error while parsing a request", e);
        }
    }

    private Payload parseEntryPoint(Payload payload, String entryPoint) {
        String[] as = entryPoint.split(":");
        if (as.length < 1) {
            throw new ValidationErrorsException("Invalid entry point format: " + entryPoint);
        }

        String projectName = as[0].trim();
        assertProject(projectName);

        // TODO replace with a queue/stack/linkedlist?
        String[] rest = as.length > 1 ? Arrays.copyOfRange(as, 1, as.length) : new String[0];
        String realEntryPoint = rest.length > 0 ? rest[rest.length - 1] : null;

        // TODO check permissions

        return payload.putHeader(Payload.PROJECT_NAME, projectName)
                .putHeader(Payload.ENTRY_POINT, rest)
                .mergeValues(Payload.REQUEST_DATA_MAP, Collections.singletonMap(Constants.ENTRY_POINT_KEY, realEntryPoint));
    }

    private void assertProject(String projectName) {
        if (!projectDao.exists(projectName)) {
            throw new ValidationErrorsException("Unknown project name: " + projectName);
        }
    }

    @Override
    @Validate
    public ProcessStatusResponse waitForCompletion(String instanceId, long timeout) {
        log.info("waitForCompletion ['{}', {}] -> waiting...", instanceId, timeout);

        long t1 = System.currentTimeMillis();

        ProcessStatusResponse r;
        while (true) {
            r = get(instanceId);
            if (r.getStatus() == ProcessStatus.FINISHED || r.getStatus() == ProcessStatus.FAILED) {
                break;
            }

            if (timeout > 0) {
                long t2 = System.currentTimeMillis();
                if (t2 - t1 >= timeout) {
                    log.warn("waitForCompletion ['{}', {}] -> timeout, last status: {}", instanceId, timeout, r.getStatus());
                    throw new WebApplicationException(Response.status(Status.REQUEST_TIMEOUT).entity(r).build());
                }
            }

            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                break;
            }
        }
        return r;
    }

    @Override
    @Validate
    public void kill(String agentId) {
        processExecutor.cancel(agentId);
    }

    @Override
    @Validate
    public ProcessStatusResponse get(String instanceId) {
        ProcessHistoryEntry r = historyDao.get(instanceId);
        if (r == null) {
            log.warn("get ['{}'] -> not found", instanceId);
            throw new WebApplicationException(Status.NOT_FOUND);
        }
        return new ProcessStatusResponse(r.getlastUpdateDt(), r.getStatus(), r.getLogFileName());
    }

    @Override
    @Validate
    public Response downloadAttachment(String instanceId, String attachmentName) {
        if (attachmentName.endsWith("/")) {
            throw new WebApplicationException("Invalid attachment name: " + attachmentName, Status.BAD_REQUEST);
        }

        Path p = attachmentCfg.getBaseDir().resolve(instanceId + ".zip");
        if (!Files.exists(p)) {
            return Response.status(Status.NOT_FOUND).build();
        }

        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(p))) {
            ZipEntry e = zip.getNextEntry();
            if (attachmentName.equals(e.getName())) {
                Path tmpFile = Files.createTempFile("attachment", ".data");
                try (OutputStream out = Files.newOutputStream(tmpFile)) {
                    IOUtils.copy(zip, out);
                }

                return Response.ok((StreamingOutput) out -> {
                    try (InputStream in = Files.newInputStream(tmpFile)) {
                        IOUtils.copy(in, out);
                    }
                }).build();
            }
        } catch (IOException e) {
            throw new WebApplicationException("Error while reading an attachment archive", e);
        }

        return Response.status(Status.NOT_FOUND).build();
    }
}
