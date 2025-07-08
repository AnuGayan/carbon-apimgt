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

import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.model.API;
import org.wso2.carbon.apimgt.api.model.Environment;

import java.util.List;

/**
 * Interface for Federated API Discovery agents.
 * Implementations of this interface will be responsible for discovering APIs
 * from external API gateways or registries and making them available in WSO2 API Manager.
 */
public interface FederatedAPIDiscovery {

    /**
     * Initializes the discovery agent with the provided Gateway Environment configuration.
     * This method will be called when a new Gateway Environment is added or updated,
     * if this discovery agent is configured for that environment.
     *
     * @param environment The Gateway Environment configuration.
     * @param schedulerService The shared scheduler service for agents to schedule their main discovery loop.
     * @throws APIManagementException if an error occurs during initialization.
     */
    void init(Environment environment, IDiscoveryAgentSchedulerService schedulerService) throws APIManagementException;

    /**
     * Discovers APIs from the federated environment.
     * This method will be called once upon environment setup or update if discovery is enabled.
     * Implementations of this method are responsible for their own periodic execution logic
     * if continuous discovery is required (e.g., by using an internal scheduler to call
     * itself or a helper method again after the configured interval).
     *
     * After discovering APIs, implementations should typically call
     * {@link #processDiscoveredAPIsAsync(List, Environment)} to handle their registration
     * in the API Manager.
     *
     * @param environment The Gateway Environment configuration, which may include interval settings.
     * @throws APIManagementException if an error occurs during API discovery.
     */
    void discoverAPIs(Environment environment) throws APIManagementException;

    /**
     * Asynchronously processes a list of discovered APIs and adds them to the API Manager.
     * Implementations should handle the asynchronous execution, typically by using a dedicated
     * thread pool. This method will obtain an APIProvider and add the APIs.
     *
     * @param discoveredAPIs List of APIs found by the discovery mechanism.
     * @param environment    The environment from which these APIs were discovered (useful for context/logging).
     * @throws APIManagementException if an error occurs during the submission of processing tasks.
     *                                Individual API addition errors should be handled within the async tasks.
     */
    void processDiscoveredAPIsAsync(List<API> discoveredAPIs, Environment environment) throws APIManagementException;

    /**
     * Returns the type of this Federated API Discovery agent.
     * This type is used to identify and select the appropriate discovery agent
     * based on Gateway Environment configuration. For example, "AWS", "Azure", "Solace".
     *
     * @return The type of the discovery agent.
     */
    String getType();

    /**
     * Signals the discovery agent to stop its activities for a specific environment.
     * This is typically called when an environment is deleted or discovery is disabled for it.
     * Implementations should use this to clean up resources, cancel internal schedulers, etc.
     *
     * @param environmentUUID The UUID of the environment for which discovery should be stopped.
     * @throws APIManagementException if an error occurs during the stop process.
     */
    void stopAgent(String environmentUUID) throws APIManagementException;
}
