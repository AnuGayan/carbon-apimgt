/*
 * Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.apimgt.gateway.handlers.graphQL;

import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.OperationDefinition;
import graphql.parser.Parser;
import graphql.validation.Validator;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;
import org.wso2.carbon.apimgt.gateway.handlers.graphQL.utils.GraphQLProcessorUtil;
import org.wso2.carbon.apimgt.gateway.handlers.security.APIKeyValidator;
import org.wso2.carbon.apimgt.gateway.handlers.security.APISecurityException;
import org.wso2.carbon.apimgt.gateway.handlers.security.AuthenticationContext;
import org.wso2.carbon.apimgt.gateway.handlers.security.jwt.JWTValidator;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.dto.ResourceInfoDTO;
import org.wso2.carbon.apimgt.impl.dto.VerbInfoDTO;
import org.wso2.carbon.apimgt.impl.internal.DataHolder;
import org.wso2.carbon.apimgt.impl.jwt.SignedJWTInfo;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.apimgt.keymgt.model.entity.API;

import java.util.Map;
import java.util.Set;

public class GraphQLRequestProcessor {

    private static final Log log = LogFactory.getLog(GraphQLRequestProcessor.class);

    public GraphQLProcessorResponseDTO handleRequest(String msgText, API electedAPI,
            SignedJWTInfo signedJWTInfo, AuthenticationContext authenticationContext,
            Map<String, ResourceInfoDTO> electedAPIResourcesMap) {

        GraphQLProcessorResponseDTO responseDTO = null;
        JSONObject graphQLMsg = new JSONObject(msgText);
        Parser parser = new Parser();

        if (checkIfSubscribeMessage(graphQLMsg)) {
            String operationId = graphQLMsg.getString(GraphQLConstants.SubscriptionConstants.PAYLOAD_FIELD_NAME_ID);
            if (validatePayloadFields(graphQLMsg)) {
                String graphQLSubscriptionPayload = ((JSONObject) graphQLMsg.get(
                        GraphQLConstants.SubscriptionConstants.PAYLOAD_FIELD_NAME_PAYLOAD)).getString(
                        GraphQLConstants.SubscriptionConstants.PAYLOAD_FIELD_NAME_QUERY);
                Document document = parser.parseDocument(graphQLSubscriptionPayload);
                // Extract the operation type and operations from the payload
                OperationDefinition operation = getOperationFromPayload(document);
                if (operation != null) {
                    if (checkIfValidSubscribeOperation(operation)) {
                        responseDTO = validateQueryPayload(electedAPI, document, operationId);
                        if (!responseDTO.isError()) {
                            // subscription operation name

                            String subscriptionOperation = GraphQLProcessorUtil.getOperationList(operation,
                                    DataHolder.getInstance().getGraphQLSchemaDTOForAPI(electedAPI.getUuid())
                                            .getTypeDefinitionRegistry());
                            // validate scopes based on subscription payload
                            responseDTO = validateScopes(subscriptionOperation, operationId, electedAPI, signedJWTInfo,
                                    authenticationContext);
                            if (!responseDTO.isError()) {
                                // extract verb info dto with throttle policy for matching verb
                                VerbInfoDTO verbInfoDTO = findMatchingVerb(subscriptionOperation,
                                        electedAPIResourcesMap, electedAPI);
                                //                                SubscriptionAnalyzer subscriptionAnalyzer =
                                //                                        new SubscriptionAnalyzer(inboundMessageContext.getGraphQLSchemaDTO()
                                //                                                .getGraphQLSchema());
                                //                                // analyze query depth and complexity
                                //                                responseDTO = validateQueryDepthAndComplexity(subscriptionAnalyzer,
                                //                                        inboundMessageContext, graphQLSubscriptionPayload, operationId);
                                //                                if (!responseDTO.isError()) {
                                //                                    //throttle for matching resource
                                //                                    responseDTO = InboundWebsocketProcessorUtil.doThrottleForGraphQL(msgSize, verbInfoDTO,
                                //                                            inboundMessageContext, operationId);
                                //                                    // add verb info dto for the successful invoking subscription operation request
                                //                                    inboundMessageContext.addVerbInfoForGraphQLMsgId(
                                //                                            graphQLMsg.getString(
                                //                                                    GraphQLConstants.SubscriptionConstants.PAYLOAD_FIELD_NAME_ID),
                                //                                            new GraphQLOperationDTO(verbInfoDTO, subscriptionOperation));
                                //                                }
                            }

                        }
                    } else {
                        responseDTO = getBadRequestGraphQLFrameErrorDTO(
                                "Invalid operation. Only allowed Subscription type operations", operationId);
                    }
                } else {
                    responseDTO = getBadRequestGraphQLFrameErrorDTO("Operation definition cannot be empty",
                            operationId);
                }
            } else {
                responseDTO = getBadRequestGraphQLFrameErrorDTO("Invalid operation payload", operationId);
            }
        }
        return responseDTO;
    }

    /**
     * Validate message fields 'payload' and 'query'.
     * Example valid payload: 'payload':{query: subscription { greetings }}'
     *
     * @param graphQLMsg GraphQL message JSON object
     * @return true if valid payload fields present
     */
    private boolean validatePayloadFields(JSONObject graphQLMsg) {
        return graphQLMsg.get(GraphQLConstants.SubscriptionConstants.PAYLOAD_FIELD_NAME_PAYLOAD) != null &&
                ((JSONObject) graphQLMsg.get(GraphQLConstants.SubscriptionConstants.PAYLOAD_FIELD_NAME_PAYLOAD)).get(
                        GraphQLConstants.SubscriptionConstants.PAYLOAD_FIELD_NAME_QUERY) != null;
    }

    /**
     * Get GraphQL Operation from payload.
     *
     * @param document GraphQL payload
     * @return Operation definition
     */
    private OperationDefinition getOperationFromPayload(Document document) {

        OperationDefinition operation = null;
        // Extract the operation type and operations from the payload
        for (Definition definition : document.getDefinitions()) {
            if (definition instanceof OperationDefinition) {
                operation = (OperationDefinition) definition;
                break;
            }
        }
        return operation;
    }

    /**
     * Check if message has mandatory graphql subscription payload and id fields. Payload should consist 'type' field
     * and its value equal to either of 'start' or 'subscribe'. The value 'start' is used in
     * 'subscriptions-transport-ws' protocol and 'subscribe' is used in 'graphql-ws' protocol.
     *
     * @param graphQLMsg GraphQL message JSON object
     * @return true if valid subscribe message
     */
    private boolean checkIfSubscribeMessage(JSONObject graphQLMsg) {
        return graphQLMsg.getString(GraphQLConstants.SubscriptionConstants.PAYLOAD_FIELD_NAME_TYPE) != null
                && GraphQLConstants.SubscriptionConstants.PAYLOAD_FIELD_NAME_ARRAY_FOR_SUBSCRIBE.contains(
                graphQLMsg.getString(GraphQLConstants.SubscriptionConstants.PAYLOAD_FIELD_NAME_TYPE))
                && graphQLMsg.getString(GraphQLConstants.SubscriptionConstants.PAYLOAD_FIELD_NAME_ID) != null;
    }

    /**
     * Check if graphql operation is a Subscription operation.
     *
     * @param operation GraphQL operation
     * @return true if valid operation type
     */
    private boolean checkIfValidSubscribeOperation(OperationDefinition operation) {
        return operation.getOperation() != null && APIConstants.GRAPHQL_SUBSCRIPTION.equalsIgnoreCase(
                operation.getOperation().toString());
    }

    /**
     * Validates GraphQL query payload using QueryValidator and graphql schema of the invoking API.
     *
     * @param electedAPI  Elected API
     * @param document    Graphql payload
     * @param operationId Graphql message id
     * @return InboundProcessorResponseDTO
     */
    protected GraphQLProcessorResponseDTO validateQueryPayload(API electedAPI, Document document, String operationId) {

        GraphQLProcessorResponseDTO responseDTO = new GraphQLProcessorResponseDTO();
        responseDTO.setId(operationId);
        QueryValidator queryValidator = new QueryValidator(new Validator());
        // payload validation
        String validationErrorMessage = queryValidator.validatePayload(
                DataHolder.getInstance().getGraphQLSchemaDTOForAPI(electedAPI.getUuid()).getGraphQLSchema(), document);
        if (validationErrorMessage != null) {
            String error =
                    GraphQLConstants.FrameErrorConstants.GRAPHQL_INVALID_QUERY_MESSAGE + " : " + validationErrorMessage;
            log.error(error);
            responseDTO.setError(true);
            responseDTO.setErrorCode(GraphQLConstants.FrameErrorConstants.GRAPHQL_INVALID_QUERY);
            responseDTO.setErrorMessage(error);
            return responseDTO;
        }
        return responseDTO;
    }

    /**
     * Validates scopes for subscription operations.
     *
     * @param subscriptionOperation Subscription operation
     * @param operationId           GraphQL message Id
     * @param electedAPI            Elected API
     * @param signedJWTInfo         Signed JWT Info during the handshake
     * @param authenticationContext Authentication context
     * @return GraphQLProcessorResponseDTO
     */
    public static GraphQLProcessorResponseDTO validateScopes(String subscriptionOperation, String operationId,
            API electedAPI, SignedJWTInfo signedJWTInfo, AuthenticationContext authenticationContext) {

        GraphQLProcessorResponseDTO responseDTO = new GraphQLProcessorResponseDTO();
        // validate scopes based on subscription payload
        try {
            if (!authorizeGraphQLSubscriptionEvents(subscriptionOperation, electedAPI, signedJWTInfo,
                    authenticationContext)) {
                String errorMessage =
                        GraphQLConstants.FrameErrorConstants.RESOURCE_FORBIDDEN_ERROR_MESSAGE + StringUtils.SPACE
                                + subscriptionOperation;
                log.error(errorMessage);
                responseDTO = getGraphQLFrameErrorDTO(GraphQLConstants.FrameErrorConstants.RESOURCE_FORBIDDEN_ERROR,
                        errorMessage, false, operationId);
            }
        } catch (APISecurityException e) {
            log.error(GraphQLConstants.FrameErrorConstants.RESOURCE_FORBIDDEN_ERROR_MESSAGE, e);
            responseDTO = getGraphQLFrameErrorDTO(GraphQLConstants.FrameErrorConstants.RESOURCE_FORBIDDEN_ERROR,
                    e.getMessage(), false, operationId);
        }
        return responseDTO;
    }

    /**
     * Validate scopes of JWT token for incoming GraphQL subscription messages.
     *
     * @param matchingResource      Invoking GraphQL subscription operation
     * @param electedAPI            Elected API
     * @param signedJWTInfo         Signed JWT Info during the handshake
     * @param authenticationContext Authentication context
     * @return true if authorized
     * @throws APISecurityException If authorization fails
     */
    public static boolean authorizeGraphQLSubscriptionEvents(String matchingResource, API electedAPI,
            SignedJWTInfo signedJWTInfo, AuthenticationContext authenticationContext) throws APISecurityException {

        JWTValidator jwtValidator = new JWTValidator(new APIKeyValidator());
        jwtValidator.validateScopesForGraphQLSubscriptions(electedAPI.getContext(), electedAPI.getApiVersion(),
                matchingResource, signedJWTInfo, authenticationContext);
        return true;
    }

    /**
     * Get error frame DTO for error code and message closeConnection parameters.
     *
     * @param errorCode       Error code
     * @param errorMessage    Error message
     * @param closeConnection Whether to close connection after throwing the error frame
     * @return GraphQLProcessorResponseDTO
     */
    public static GraphQLProcessorResponseDTO getFrameErrorDTO(int errorCode, String errorMessage,
            boolean closeConnection) {

        GraphQLProcessorResponseDTO graphQLProcessorResponseDTO = new GraphQLProcessorResponseDTO();
        graphQLProcessorResponseDTO.setError(true);
        graphQLProcessorResponseDTO.setErrorCode(errorCode);
        graphQLProcessorResponseDTO.setErrorMessage(errorMessage);
        graphQLProcessorResponseDTO.setCloseConnection(closeConnection);
        return graphQLProcessorResponseDTO;
    }

    /**
     * Get GraphQL subscription error frame DTO for error code and message closeConnection parameters.
     *
     * @param errorCode       Error code
     * @param errorMessage    Error message
     * @param closeConnection Whether to close connection after throwing the error frame
     * @param operationId     Operation ID
     * @return InboundProcessorResponseDTO
     */
    public static GraphQLProcessorResponseDTO getGraphQLFrameErrorDTO(int errorCode, String errorMessage,
            boolean closeConnection, String operationId) {

        GraphQLProcessorResponseDTO graphQLProcessorResponseDTO = new GraphQLProcessorResponseDTO();
        graphQLProcessorResponseDTO.setError(true);
        graphQLProcessorResponseDTO.setErrorCode(errorCode);
        graphQLProcessorResponseDTO.setErrorMessage(errorMessage);
        graphQLProcessorResponseDTO.setCloseConnection(closeConnection);
        graphQLProcessorResponseDTO.setId(operationId);
        return graphQLProcessorResponseDTO;
    }


    /**
     * Get bad request (error code 4010) error frame DTO for GraphQL subscriptions. The closeConnection parameter is
     * false.
     *
     * @param errorMessage Error message
     * @param operationId  Operation ID
     * @return InboundProcessorResponseDTO
     */
    public static GraphQLProcessorResponseDTO getBadRequestGraphQLFrameErrorDTO(String errorMessage,
            String operationId) {

        GraphQLProcessorResponseDTO graphQLProcessorResponseDTO = new GraphQLProcessorResponseDTO();
        graphQLProcessorResponseDTO.setError(true);
        graphQLProcessorResponseDTO.setErrorCode(GraphQLConstants.FrameErrorConstants.BAD_REQUEST);
        graphQLProcessorResponseDTO.setErrorMessage(errorMessage);
        graphQLProcessorResponseDTO.setId(operationId);
        return graphQLProcessorResponseDTO;
    }

    /**
     * Finds matching VerbInfoDTO for the subscription operation.
     *
     * @param operation              subscription operation name
     * @param electedAPIResourcesMap Resource map of the elected API
     * @param electedAPI             Elected API
     * @return VerbInfoDTO
     */
    public static VerbInfoDTO findMatchingVerb(String operation, Map<String, ResourceInfoDTO> electedAPIResourcesMap,
            API electedAPI) {
        String resourceCacheKey;
        VerbInfoDTO verbInfoDTO = null;
        if (electedAPIResourcesMap != null) {
            ResourceInfoDTO resourceInfoDTO = electedAPIResourcesMap.get(operation);
            Set<VerbInfoDTO> verbDTOList = resourceInfoDTO.getHttpVerbs();
            for (VerbInfoDTO verb : verbDTOList) {
                if (verb.getHttpVerb().equals(GraphQLConstants.SubscriptionConstants.HTTP_METHOD_NAME)) {
                    if (isResourcePathMatching(operation, resourceInfoDTO)) {
                        verbInfoDTO = verb;
                        resourceCacheKey = APIUtil.getResourceInfoDTOCacheKey(electedAPI.getContext(),
                                electedAPI.getApiVersion(), operation,
                                GraphQLConstants.SubscriptionConstants.HTTP_METHOD_NAME);
                        verb.setRequestKey(resourceCacheKey);
                        break;
                    }
                }
            }
        }
        return verbInfoDTO;
    }

    /**
     * Check if resource path matches.
     *
     * @param resourceString  Resource string
     * @param resourceInfoDTO ResourceInfoDTO
     * @return true if matches
     */
    private static boolean isResourcePathMatching(String resourceString, ResourceInfoDTO resourceInfoDTO) {
        String resource = resourceString.trim();
        String urlPattern = resourceInfoDTO.getUrlPattern().trim();
        return resource.equalsIgnoreCase(urlPattern);
    }
}
