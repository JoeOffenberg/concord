package com.walmartlabs.concord.server.events;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */


import com.walmartlabs.concord.server.api.events.ExternalEventResource;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.triggers.TriggersDao;
import com.walmartlabs.concord.server.process.ProcessManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.UUID;

@Named
public class ExternalEventResourceImpl extends AbstractEventResource implements ExternalEventResource, Resource {

    private static final Logger log = LoggerFactory.getLogger(ExternalEventResourceImpl.class);

    @Inject
    public ExternalEventResourceImpl(ProcessManager processManager,
                                     TriggersDao triggersDao,
                                     ProjectDao projectDao) {

        super(processManager, triggersDao, projectDao);
    }

    @Override
    public Response event(String eventName, Map<String, Object> event) {
        if (event == null || event.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        String eventId = (String) event.get("id");
        if (eventId == null) {
            eventId = UUID.randomUUID().toString();
        }

        int count = process(eventId, eventName, event, event);

        log.info("event ['{}', '{}', '{}'] -> done, {} processes started", eventId, eventName, event, count);
        return Response.ok().build();
    }
}