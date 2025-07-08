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

package org.wso2.carbon.apimgt.impl.discovery.agents;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.APIProvider;
import org.wso2.carbon.apimgt.api.model.API;
import org.wso2.carbon.apimgt.api.model.APIIdentifier;
import org.wso2.carbon.apimgt.api.model.Environment;
import org.wso2.carbon.apimgt.impl.discovery.FederatedAPIDiscovery;
import org.wso2.carbon.apimgt.impl.utils.APIMgtAsyncExecutorService;
import org.wso2.carbon.apimgt.rest.api.common.RestApiCommonUtil;
import org.wso2.carbon.context.PrivilegedCarbonContext; // For tenant/user context propagation

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Sample implementation of a FederatedAPIDiscovery agent.
 * This agent simulates discovering a predefined list of APIs and
 * demonstrates self-scheduling and asynchronous API processing.
 */
public class SampleFederatedAgent implements FederatedAPIDiscovery {

    private static final Log log = LogFactory.getLog(SampleFederatedAgent.class);
    private static final String AGENT_TYPE = "SampleAgent";

    private Environment environmentConfig;
    private IDiscoveryAgentSchedulerService sharedSchedulerService;
    private ScheduledFuture<?> selfSchedulingFuture;
    private ExecutorService apiProcessingExecutor; // For async processing of discovered APIs

    @Override
    public void init(Environment environment, IDiscoveryAgentSchedulerService schedulerService) throws APIManagementException {
        this.environmentConfig = environment;
        this.sharedSchedulerService = schedulerService;
        log.info(AGENT_TYPE + " initialized for environment: " + environment.getName() + " using shared scheduler.");

        // Initialize agent's own executor for processing discovered APIs.
        this.apiProcessingExecutor = Executors.newFixedThreadPool(3, r -> {
            Thread t = new Thread(r);
            t.setName(AGENT_TYPE + "-APIProcessor-" + environment.getName() + "-" + t.getId());
            return t;
        });
    }

    @Override
    public void discoverAPIs(Environment environment) throws APIManagementException {
        log.info(AGENT_TYPE + " discoverAPIs called for environment: " + environment.getName());

        // 1. Simulate actual discovery from an external source
        List<API> discoveredAPIs = new ArrayList<>();
        // Example: Create a dummy API
        API sampleAPI = new API(new APIIdentifier("admin", "DiscoveredSampleAPI", "1.0.0"));
        sampleAPI.setContext("/discovered/sample");
        sampleAPI.setContextTemplate("/discovered/{version}/sample");
        sampleAPI.setUriTemplates(new ArrayList<>()); // Add URITemplates as needed
        sampleAPI.setOrganization(PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain(true));
        // ... set other necessary API properties
        discoveredAPIs.add(sampleAPI);

        log.info(AGENT_TYPE + " discovered " + discoveredAPIs.size() + " APIs from " + environment.getName());

        // 2. Process them asynchronously
        if (!discoveredAPIs.isEmpty()) {
            processDiscoveredAPIsAsync(discoveredAPIs, environment);
        }

        // 3. Self-schedule using the shared scheduler if interval is configured
        if (sharedSchedulerService != null && environmentConfig.isFederatedDiscoveryEnabled() && environmentConfig.getDiscoveryInterval() > 0) {
            Runnable task = () -> {
                try {
                    // Important: Pass the original environment aot. this.environmentConfig might change if init is called again
                    discoverAPIs(this.environmentConfig);
                } catch (APIManagementException e) {
                    log.error(AGENT_TYPE + " error in self-scheduled discovery for " + this.environmentConfig.getName(), e);
                } catch (Exception e) {
                    log.error(AGENT_TYPE + " unexpected error in self-scheduled discovery for " + this.environmentConfig.getName(), e);
                }
            };

            // Cancel previous future before scheduling new one, to avoid multiple overlapping schedules for the same agent instance
            // This is important if discoverAPIs is called multiple times (e.g. on environment update)
            if (this.selfSchedulingFuture != null && !this.selfSchedulingFuture.isDone()) {
                 this.selfSchedulingFuture.cancel(false); // Don't interrupt if running, just prevent future execution
            }

            this.selfSchedulingFuture = sharedSchedulerService.getScheduler().schedule(task,
                    environmentConfig.getDiscoveryInterval(), TimeUnit.SECONDS);

            log.info(AGENT_TYPE + " self-scheduled next discovery run using shared scheduler in "
                    + environmentConfig.getDiscoveryInterval() + " seconds for " + environment.getName());
        } else {
            log.info(AGENT_TYPE + " self-scheduling not configured (interval <=0 or discovery disabled) or shared scheduler not available for " + environment.getName());
        }
    }

    @Override
    public void processDiscoveredAPIsAsync(List<API> discoveredAPIs, Environment environment) throws APIManagementException {
        if (apiProcessingExecutor == null || apiProcessingExecutor.isShutdown()) {
            log.warn(AGENT_TYPE + ": API Processing Executor is not available or shutdown for environment "
                    + environment.getName() + ". Cannot process APIs.");
            // Optionally, re-initialize or throw an exception if this state is unexpected.
            // For now, just returning.
            if (apiProcessingExecutor == null) {
                 this.apiProcessingExecutor = Executors.newFixedThreadPool(3, r -> {
                    Thread t = new Thread(r);
                    t.setName(AGENT_TYPE + "-APIProcessor-" + environment.getName() + "-" + t.getId());
                    return t;
                });
                log.info(AGENT_TYPE + ": Re-initialized apiProcessingExecutor for " + environment.getName());
            } else {
                return;
            }
        }

        final String tenantDomain = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain();
        final String username = PrivilegedCarbonContext.getThreadLocalCarbonContext().getUsername();

        for (API api : discoveredAPIs) {
            apiProcessingExecutor.submit(() -> {
                boolean isTenantFlowStarted = false;
                try {
                    PrivilegedCarbonContext.startTenantFlow();
                    PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);
                    PrivilegedCarbonContext.getThreadLocalCarbonContext().setUsername(username);
                    isTenantFlowStarted = true;

                    log.info(AGENT_TYPE + " processing API: " + api.getId().getApiName() + " asynchronously for env: " + environment.getName());
                    APIProvider apiProvider = RestApiCommonUtil.getLoggedInUserProvider();
                    apiProvider.addAPI(api);
                    log.info(AGENT_TYPE + " successfully added API: " + api.getId().getApiName() + " via async task for env: " + environment.getName());

                } catch (APIManagementException e) {
                    log.error(AGENT_TYPE + " error adding API: " + api.getId().getApiName() + " asynchronously for env: " + environment.getName(), e);
                } catch (Exception e) {
                    log.error(AGENT_TYPE + " unexpected error processing API: " + api.getId().getApiName() + " for env: " + environment.getName(), e);
                } finally {
                    if (isTenantFlowStarted) {
                        PrivilegedCarbonContext.endTenantFlow();
                    }
                }
            });
        }
        log.info(AGENT_TYPE + " submitted " + discoveredAPIs.size() + " APIs for asynchronous processing from env: " + environment.getName());
    }

    @Override
    public String getType() {
        return AGENT_TYPE;
    }

    /**
     * Called when discovery for an environment should be stopped.
     * This agent will shut down its internal schedulers and executors.
     */
    @Override
    public void stopAgent(String environmentUUID) throws APIManagementException {
        if (this.environmentConfig != null && this.environmentConfig.getUuid().equals(environmentUUID)) {
            log.info(AGENT_TYPE + " stopping activities for environment: " + this.environmentConfig.getName() + " (UUID: " + environmentUUID + ")");

            // Cancel self-scheduling task from the shared scheduler
            if (selfSchedulingFuture != null && !selfSchedulingFuture.isDone()) {
                selfSchedulingFuture.cancel(true); // Interrupt if running
                log.info(AGENT_TYPE + ": Cancelled self-scheduling task from shared scheduler for " + this.environmentConfig.getName());
            }
            selfSchedulingFuture = null; // Clear the future

            // Shutdown agent's own executor for API processing
            if (apiProcessingExecutor != null && !apiProcessingExecutor.isShutdown()) {
                apiProcessingExecutor.shutdown();
                try {
                    if (!apiProcessingExecutor.awaitTermination(10, TimeUnit.SECONDS)) { // Increased timeout slightly
                        apiProcessingExecutor.shutdownNow();
                        if (!apiProcessingExecutor.awaitTermination(10, TimeUnit.SECONDS)){
                             log.error(AGENT_TYPE + ": API processing executor did not terminate for " + this.environmentConfig.getName());
                        }
                    }
                } catch (InterruptedException e) {
                    apiProcessingExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
                log.info(AGENT_TYPE + ": API processing executor for " + this.environmentConfig.getName() + " shutdown.");
            }
            apiProcessingExecutor = null; // Clear the executor
        } else {
            log.warn(AGENT_TYPE + " received stopAgent signal for UUID " + environmentUUID
                    + " but current config is for " + (this.environmentConfig != null ? this.environmentConfig.getUuid() : "null")
                    + " or agent not properly initialized for this environment.");
        }
    }
}
