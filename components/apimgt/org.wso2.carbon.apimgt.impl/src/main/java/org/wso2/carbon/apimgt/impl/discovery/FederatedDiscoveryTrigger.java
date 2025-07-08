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
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.model.Environment;
import org.wso2.carbon.apimgt.impl.internal.ServiceReferenceHolder;

/**
 * Triggers the initial execution of federated API discovery agents
 * when environments are configured or updated.
 */
public class FederatedDiscoveryTrigger {

    private static final Log log = LogFactory.getLog(FederatedDiscoveryTrigger.class);

    public FederatedDiscoveryTrigger() {
        // Constructor
    }

    /**
     * Triggers the discovery process for a given environment.
     * It initializes the agent and calls its discoverAPIs method once.
     * The agent itself is responsible for any subsequent periodic execution.
     *
     * @param environment The Gateway Environment with discovery configuration.
     */
    public void triggerDiscovery(Environment environment) {
        if (environment == null || !environment.isFederatedDiscoveryEnabled() || environment.getDiscoveryAgentType() == null) {
            log.debug("Federated discovery not enabled or agent type not specified for environment: "
                    + (environment != null ? environment.getName() : "null")
                    + ". No discovery triggered.");
            // If an old agent was running for this environment and is now disabled,
            // we might need to explicitly stop it if the agent type hasn't changed.
            // This requires knowing the previous state or the agent type.
            // For now, we assume stopDiscovery will be called if the environment is explicitly disabled.
            return;
        }

        FederatedAPIDiscovery discoveryAgent = ServiceReferenceHolder.getInstance()
                .getFederatedApiDiscoverer(environment.getDiscoveryAgentType());

        if (discoveryAgent == null) {
            log.error("No FederatedAPIDiscovery agent found for type: " + environment.getDiscoveryAgentType()
                    + " for environment: " + environment.getName());
            return;
        }

        try {
            IDiscoveryAgentSchedulerService schedulerService = ServiceReferenceHolder.getInstance().getDiscoveryAgentSchedulerService();
            if (schedulerService == null) {
                log.error("DiscoveryAgentSchedulerService not found. Cannot trigger discovery for environment: "
                        + environment.getName());
                return;
            }
            log.info("Initializing and triggering one-time discovery for environment: "
                    + environment.getName() + " with agent: " + discoveryAgent.getType());
            discoveryAgent.init(environment, schedulerService);
            discoveryAgent.discoverAPIs(environment); // This call is now expected to be handled by the agent for periodicity
            log.info("Initial discovery triggered for environment: " + environment.getName());
        } catch (APIManagementException e) {
            log.error("Error during initial triggering of FederatedAPIDiscovery agent for environment: "
                    + environment.getName(), e);
        }
    }

    /**
     * Signals a discovery agent to stop its activities for a given environment.
     * This is typically called when an environment is deleted or discovery is disabled.
     * This requires the FederatedAPIDiscovery interface to have a method like stop().
     *
     * @param environment The environment for which discovery should be stopped.
     */
    public void stopDiscovery(Environment environment) {
        if (environment == null || environment.getDiscoveryAgentType() == null) {
            log.debug("Cannot stop discovery. Environment or discovery agent type is null for environment: "
                    + (environment != null ? environment.getName() : "null"));
            return;
        }

        FederatedAPIDiscovery discoveryAgent = ServiceReferenceHolder.getInstance()
                .getFederatedApiDiscoverer(environment.getDiscoveryAgentType());

        if (discoveryAgent == null) {
            log.warn("No FederatedAPIDiscovery agent found for type: " + environment.getDiscoveryAgentType()
                    + " during stopDiscovery for environment: " + environment.getName() + ". Unable to send stop signal.");
            return;
        }

        try {
            log.info("Attempting to stop discovery agent type: " + discoveryAgent.getType() +
                     " for environment: " + environment.getName() + " (UUID: " + environment.getUuid() + ")");
            discoveryAgent.stopAgent(environment.getUuid());
            log.info("Successfully signalled stop to discovery agent type: " + discoveryAgent.getType() +
                     " for environment: " + environment.getName());
        } catch (APIManagementException e) {
            log.error("Error explicitly stopping FederatedAPIDiscovery agent for environment: "
                    + environment.getName(), e);
        } catch (Exception e) { // Catching other unexpected exceptions during stop
            log.error("Unexpected error trying to stop FederatedAPIDiscovery agent for environment: "
                    + environment.getName(), e);
        }
    }

    /**
     * Updates the discovery process for an environment.
     * This might involve stopping an old agent (if type changed) and starting a new one.
     *
     * @param oldEnvironment The previous state of the environment (can be null if it's a new enabling).
     * @param newEnvironment The new state of the environment.
     */
    public void updateDiscovery(Environment oldEnvironment, Environment newEnvironment) {
        if (newEnvironment == null) {
            log.error("New environment configuration is null. Cannot update discovery.");
            return;
        }

        boolean wasEnabled = oldEnvironment != null && oldEnvironment.isFederatedDiscoveryEnabled();
        boolean isEnabled = newEnvironment.isFederatedDiscoveryEnabled();
        String oldAgentType = wasEnabled ? oldEnvironment.getDiscoveryAgentType() : null;
        String newAgentType = isEnabled ? newEnvironment.getDiscoveryAgentType() : null;

        if (wasEnabled && (!isEnabled || (newAgentType != null && !newAgentType.equals(oldAgentType)))) {
            // Discovery was enabled and is now disabled OR agent type has changed. Stop the old one.
            log.info("Discovery configuration changed for environment: " + newEnvironment.getName() +
                     ". Stopping previous agent (if any).");
            stopDiscovery(oldEnvironment);
        }

        if (isEnabled) {
            log.info("Triggering discovery for updated environment: " + newEnvironment.getName());
            triggerDiscovery(newEnvironment);
        } else {
            log.info("Federated discovery is disabled for environment: " + newEnvironment.getName() +
                     ". No discovery will be triggered.");
        }
    }
}
