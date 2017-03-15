package com.walmartlabs.concord.it.server;

import com.icegreen.greenmail.junit.GreenMailRule;
import com.icegreen.greenmail.util.ServerSetup;
import com.walmartlabs.concord.common.Constants;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.api.process.ProcessStatusResponse;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import com.walmartlabs.concord.server.api.project.CreateProjectRequest;
import com.walmartlabs.concord.server.api.project.ProjectResource;
import org.junit.Rule;
import org.junit.Test;

import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class SmtpIT extends AbstractServerIT {

    @Rule
    public final GreenMailRule mail = new GreenMailRule(new ServerSetup(0, "0.0.0.0", ServerSetup.PROTOCOL_SMTP));

    @Test
    public void testSimple() throws Exception {
        URI dir = SmtpIT.class.getResource("smtp").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // --

        String projectName = "project@" + System.currentTimeMillis();

        Map<String, Object> smtpParams = new HashMap<>();
        smtpParams.put("host", ITConstants.SMTP_SERVER_HOST);
        smtpParams.put("port", mail.getSmtp().getPort());

        Map<String, Object> args = new HashMap<>();
        args.put("smtpParams", smtpParams);

        Map<String, Object> cfg = new HashMap<>();
        cfg.put(Constants.ARGUMENTS_KEY, args);

        ProjectResource projectResource = proxy(ProjectResource.class);
        projectResource.create(new CreateProjectRequest(projectName, cfg));

        // --

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(projectName, new ByteArrayInputStream(payload));

        // ---

        ProcessStatusResponse pir = waitForCompletion(processResource, spr.getInstanceId());
        assertEquals(ProcessStatus.FINISHED, pir.getStatus());

        // ---

        MimeMessage[] messages = mail.getReceivedMessages();
        assertNotNull(messages);
        assertEquals(1, messages.length);
        assertEquals("hi!\r\n", messages[0].getContent());
    }
}
