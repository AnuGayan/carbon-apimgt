/*
 * Copyright (c) 2023, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.apimgt.impl.discovery;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.internal.ServiceReferenceHolder;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages a shared ScheduledExecutorService for Federated API Discovery agents'
 * periodic task scheduling.
 */
public class DiscoveryAgentSchedulerService implements IDiscoveryAgentSchedulerService {

    private static final Log log = LogFactory.getLog(DiscoveryAgentSchedulerService.class);
    private final ScheduledExecutorService scheduler;

    public DiscoveryAgentSchedulerService() {
        int corePoolSize = 5; // Default

        if (ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService() != null &&
            ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService().getAPIManagerConfiguration() != null) {
            org.wso2.carbon.apimgt.impl.APIManagerConfiguration apiManagerConfig =
                    ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService().getAPIManagerConfiguration();
            try {
                String coreSizeStr = apiManagerConfig.getFirstProperty(APIConstants.DISCOVERY_AGENT_SCHEDULER_CORE_POOL_SIZE);
                if (coreSizeStr != null && !coreSizeStr.isEmpty()) {
                    corePoolSize = Integer.parseInt(coreSizeStr);
                } else {
                    log.info("Discovery Agent Scheduler core pool size not configured in api-manager.xml. Defaulting to " + corePoolSize);
                }
            } catch (NumberFormatException e) {
                log.warn("Could not parse Discovery Agent Scheduler core pool size. Using default: " + corePoolSize, e);
            }
        } else {
            log.warn("APIManagerConfigurationService not available. Using default Discovery Agent Scheduler core pool size: " + corePoolSize);
        }

        this.scheduler = Executors.newScheduledThreadPool(corePoolSize, new ThreadFactory() {
            private final AtomicInteger threadCount = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("DiscoveryAgentSchedulerThread-" + threadCount.getAndIncrement());
                return t;
            }
        });
        log.info("DiscoveryAgentSchedulerService initialized with CorePoolSize: " + corePoolSize);
    }

    @Override
    public ScheduledExecutorService getScheduler() {
        return this.scheduler;
    }

    @Override
    public void shutdown() {
        log.info("Shutting down DiscoveryAgentSchedulerService...");
        this.scheduler.shutdown();
        try {
            if (!this.scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                this.scheduler.shutdownNow();
                if (!this.scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                    log.error("DiscoveryAgentSchedulerService did not terminate gracefully after shutdownNow().");
                }
            }
        } catch (InterruptedException ie) {
            this.scheduler.shutdownNow();
            Thread.currentThread().interrupt();
            log.warn("DiscoveryAgentSchedulerService shutdown was interrupted.");
        }
        log.info("DiscoveryAgentSchedulerService shutdown process complete.");
    }
}
