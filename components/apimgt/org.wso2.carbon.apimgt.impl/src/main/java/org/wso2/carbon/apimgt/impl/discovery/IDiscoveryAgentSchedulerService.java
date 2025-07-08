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

import java.util.concurrent.ScheduledExecutorService;

/**
 * Interface for a shared ScheduledExecutorService for scheduling the main
 * discovery loop of FederatedAPIDiscovery agents.
 * Implementations of this service will be registered via OSGi.
 */
public interface IDiscoveryAgentSchedulerService {

    /**
     * Returns the configured ScheduledExecutorService.
     * Agents will use this to schedule their periodic discoverAPIs tasks.
     *
     * @return The ScheduledExecutorService instance.
     */
    ScheduledExecutorService getScheduler();

    /**
     * Initiates an orderly shutdown of the scheduler.
     * This should be called when the service is being deactivated.
     */
    void shutdown();
}
