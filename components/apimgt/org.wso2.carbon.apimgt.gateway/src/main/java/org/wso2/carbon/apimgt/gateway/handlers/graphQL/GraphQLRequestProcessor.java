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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.apache.axis2.AxisFault;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.gateway.dto.GraphQLOperationDTO;
import org.wso2.carbon.apimgt.gateway.dto.QueryAnalyzerResponseDTO;
import org.wso2.carbon.apimgt.gateway.handlers.InboundMessageContext;
import org.wso2.carbon.apimgt.gateway.handlers.WebsocketInboundHandler;
import org.wso2.carbon.apimgt.gateway.handlers.WebsocketUtil;
import org.wso2.carbon.apimgt.gateway.handlers.graphQL.analyzer.SubscriptionAnalyzer;
import org.wso2.carbon.apimgt.gateway.handlers.security.APIKeyValidator;
import org.wso2.carbon.apimgt.gateway.handlers.security.APISecurityConstants;
import org.wso2.carbon.apimgt.gateway.handlers.security.APISecurityException;
import org.wso2.carbon.apimgt.gateway.handlers.security.AuthenticationContext;
import org.wso2.carbon.apimgt.gateway.handlers.security.jwt.JWTValidator;
import org.wso2.carbon.apimgt.gateway.internal.DataHolder;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.dto.APIKeyValidationInfoDTO;
import org.wso2.carbon.apimgt.impl.dto.ResourceInfoDTO;
import org.wso2.carbon.apimgt.impl.dto.VerbInfoDTO;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.context.PrivilegedCarbonContext;

import java.util.List;
import java.util.Set;

/**
 * A GraphQL subscriptions specific extension of RequestProcessor. This class intercepts the inbound websocket
 * execution path of graphQL subscription requests.
 */
public class GraphQLRequestProcessor {

    private static final Log log = LogFactory.getLog(GraphQLRequestProcessor.class);

    /**
     * This method process websocket request messages (publish messages) and
     * hand over the processing to relevant request intercepting processor for authentication, scope validation,
     * throttling etc.
     *
     * @param msg                   Websocket request message frame
     * @param ctx                   ChannelHandlerContext
     * @param inboundMessageContext InboundMessageContext
     * @return InboundProcessorResponseDTO with handshake processing response
     */
    public InboundProcessorResponseDTO handleRequest(WebSocketFrame msg, ChannelHandlerContext ctx,
            InboundMessageContext inboundMessageContext) throws APISecurityException, AxisFault {
        InboundProcessorResponseDTO responseDTO;
        GraphQLAPIHandler.setGraphQLSchemaToDataHolder(inboundMessageContext);

        try {
            PrivilegedCarbonContext.startTenantFlow();
            PrivilegedCarbonContext.getThreadLocalCarbonContext()
                    .setTenantDomain(inboundMessageContext.getTenantDomain(), true);
            responseDTO = authenticateGraphQLJWTToken(inboundMessageContext);

            String msgText = ((TextWebSocketFrame) msg).text();
            JSONObject graphQLMsg = new JSONObject(msgText);
            Parser parser = new Parser();

            // for gql subscription operation payloads
            if (!responseDTO.isError() && checkIfSubscribeMessage(graphQLMsg)) {
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
                            responseDTO = validateQueryPayload(inboundMessageContext, document, operationId);
                            if (!responseDTO.isError()) {
                                // subscription operation name
                                String subscriptionOperation = GraphQLAPIHandler.getOperationList(operation,
                                        DataHolder.getInstance().getGraphQLSchemaDTOForAPI(
                                                        inboundMessageContext.getElectedAPI().getUuid())
                                                .getTypeDefinitionRegistry());
                                // validate scopes based on subscription payload
                                responseDTO = validateScopes(inboundMessageContext, subscriptionOperation, operationId);
                                if (!responseDTO.isError()) {
                                    // extract verb info dto with throttle policy for matching verb
                                    VerbInfoDTO verbInfoDTO = findMatchingVerb(inboundMessageContext,
                                            subscriptionOperation);
                                    SubscriptionAnalyzer subscriptionAnalyzer = new SubscriptionAnalyzer(
                                            DataHolder.getInstance().getGraphQLSchemaDTOForAPI(
                                                            inboundMessageContext.getElectedAPI().getUuid())
                                                    .getGraphQLSchema());
                                    // analyze query depth and complexity
                                    responseDTO = validateQueryDepthAndComplexity(subscriptionAnalyzer,
                                            inboundMessageContext, graphQLSubscriptionPayload, operationId);
                                    if (!responseDTO.isError()) {
                                        // throttle for matching resource
                                        responseDTO = doThrottleForGraphQL(msg, ctx, verbInfoDTO, inboundMessageContext,
                                                operationId);
                                        inboundMessageContext.addVerbInfoForGraphQLMsgId(graphQLMsg.getString(
                                                        GraphQLConstants.SubscriptionConstants.PAYLOAD_FIELD_NAME_ID),
                                                new GraphQLOperationDTO(verbInfoDTO, subscriptionOperation));
                                    }
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
        } finally {
            PrivilegedCarbonContext.endTenantFlow();
        }
    }

    /**
     * Authenticates JWT token in incoming GraphQL subscription requests.
     *
     * @param inboundMessageContext InboundMessageContext
     * @return InboundProcessorResponseDTO
     */
    public static InboundProcessorResponseDTO authenticateGraphQLJWTToken(InboundMessageContext inboundMessageContext) {

        InboundProcessorResponseDTO responseDTO = new InboundProcessorResponseDTO();
        AuthenticationContext authenticationContext;
        JWTValidator jwtValidator = new JWTValidator(new APIKeyValidator());
        try {
            authenticationContext = jwtValidator.authenticateForWSAndGraphQL(inboundMessageContext.getSignedJWTInfo(),
                    inboundMessageContext.getApiContextUri(), inboundMessageContext.getVersion());
            inboundMessageContext.setAuthContext(authenticationContext);
            if (!WebsocketUtil.validateAuthenticationContext(inboundMessageContext,
                    inboundMessageContext.getElectedAPI().isDefaultVersion())) {
                responseDTO = getFrameErrorDTO(GraphQLConstants.FrameErrorConstants.API_AUTH_INVALID_CREDENTIALS,
                        APISecurityConstants.API_AUTH_INVALID_CREDENTIALS_MESSAGE, true);
            }
        } catch (APISecurityException e) {
            log.error(GraphQLConstants.FrameErrorConstants.API_AUTH_INVALID_CREDENTIALS, e);
            responseDTO = getFrameErrorDTO(GraphQLConstants.FrameErrorConstants.API_AUTH_INVALID_CREDENTIALS,
                    e.getMessage(), true);
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
        return graphQLMsg.has(GraphQLConstants.SubscriptionConstants.PAYLOAD_FIELD_NAME_PAYLOAD)
                && graphQLMsg.get(GraphQLConstants.SubscriptionConstants.PAYLOAD_FIELD_NAME_PAYLOAD) != null
                && ((JSONObject) graphQLMsg.get(GraphQLConstants.SubscriptionConstants.PAYLOAD_FIELD_NAME_PAYLOAD))
                .has(GraphQLConstants.SubscriptionConstants.PAYLOAD_FIELD_NAME_QUERY)
                && ((JSONObject) graphQLMsg.get(GraphQLConstants.SubscriptionConstants.PAYLOAD_FIELD_NAME_PAYLOAD))
                .get(GraphQLConstants.SubscriptionConstants.PAYLOAD_FIELD_NAME_QUERY) != null;
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
     * @param inboundMessageContext InboundMessageContext
     * @param document              Graphql payload
     * @param operationId           Graphql message id
     * @return InboundProcessorResponseDTO
     */
    protected InboundProcessorResponseDTO validateQueryPayload(InboundMessageContext inboundMessageContext,
            Document document, String operationId) {

        InboundProcessorResponseDTO responseDTO = new InboundProcessorResponseDTO();
        responseDTO.setId(operationId);
        QueryValidator queryValidator = new QueryValidator(new Validator());
        // payload validation
        String validationErrorMessage = queryValidator.validatePayload(
                DataHolder.getInstance().getGraphQLSchemaDTOForAPI(inboundMessageContext.getElectedAPI().getUuid())
                        .getGraphQLSchema(), document);
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
     * @param inboundMessageContext InboundMessageContext
     * @param subscriptionOperation Subscription operation
     * @param operationId           GraphQL message Id
     * @return InboundProcessorResponseDTO
     */
    public static InboundProcessorResponseDTO validateScopes(InboundMessageContext inboundMessageContext, String subscriptionOperation, String operationId) {

        InboundProcessorResponseDTO responseDTO = new InboundProcessorResponseDTO();
        // validate scopes based on subscription payload
        try {
            if (!authorizeGraphQLSubscriptionEvents(inboundMessageContext, subscriptionOperation)) {
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
     * @param inboundMessageContext InboundMessageContext
     * @param matchingResource      Invoking GraphQL subscription operation
     * @return true if authorized
     * @throws APISecurityException If authorization fails
     */
    public static boolean authorizeGraphQLSubscriptionEvents(InboundMessageContext inboundMessageContext,
            String matchingResource) throws APISecurityException {

        JWTValidator jwtValidator = new JWTValidator(new APIKeyValidator());
        jwtValidator.validateScopesForGraphQLSubscriptions(inboundMessageContext.getApiContextUri(),
                inboundMessageContext.getVersion(), matchingResource, inboundMessageContext.getSignedJWTInfo(),
                inboundMessageContext.getAuthContext());
        return true;
    }

    /**
     * Get error frame DTO for error code and message closeConnection parameters.
     *
     * @param errorCode       Error code
     * @param errorMessage    Error message
     * @param closeConnection Whether to close connection after throwing the error frame
     * @return InboundProcessorResponseDTO
     */
    public static InboundProcessorResponseDTO getFrameErrorDTO(int errorCode, String errorMessage,
            boolean closeConnection) {

        InboundProcessorResponseDTO inboundProcessorResponseDTO = new InboundProcessorResponseDTO();
        inboundProcessorResponseDTO.setError(true);
        inboundProcessorResponseDTO.setErrorCode(errorCode);
        inboundProcessorResponseDTO.setErrorMessage(errorMessage);
        inboundProcessorResponseDTO.setCloseConnection(closeConnection);
        return inboundProcessorResponseDTO;
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
    public static InboundProcessorResponseDTO getGraphQLFrameErrorDTO(int errorCode, String errorMessage,
            boolean closeConnection, String operationId) {

        InboundProcessorResponseDTO inboundProcessorResponseDTO = new InboundProcessorResponseDTO();
        inboundProcessorResponseDTO.setError(true);
        inboundProcessorResponseDTO.setErrorCode(errorCode);
        inboundProcessorResponseDTO.setErrorMessage(errorMessage);
        inboundProcessorResponseDTO.setCloseConnection(closeConnection);
        inboundProcessorResponseDTO.setId(operationId);
        return inboundProcessorResponseDTO;
    }

    /**
     * Get bad request (error code 4010) error frame DTO for GraphQL subscriptions. The closeConnection parameter is
     * false.
     *
     * @param errorMessage Error message
     * @param operationId  Operation ID
     * @return InboundProcessorResponseDTO
     */
    public static InboundProcessorResponseDTO getBadRequestGraphQLFrameErrorDTO(String errorMessage,
            String operationId) {

        InboundProcessorResponseDTO inboundProcessorResponseDTO = new InboundProcessorResponseDTO();
        inboundProcessorResponseDTO.setError(true);
        inboundProcessorResponseDTO.setErrorCode(GraphQLConstants.FrameErrorConstants.BAD_REQUEST);
        inboundProcessorResponseDTO.setErrorMessage(errorMessage);
        inboundProcessorResponseDTO.setId(operationId);
        return inboundProcessorResponseDTO;
    }

    /**
     * Get GraphQL Subscriptions handshake error DTO for error code and message. The closeConnection parameter is false.
     *
     * @param errorCode    Error code
     * @param errorMessage Error message
     * @return InboundProcessorResponseDTO
     */
    public static InboundProcessorResponseDTO getHandshakeErrorDTO(int errorCode, String errorMessage) {

        InboundProcessorResponseDTO inboundProcessorResponseDTO = new InboundProcessorResponseDTO();
        inboundProcessorResponseDTO.setError(true);
        inboundProcessorResponseDTO.setErrorCode(errorCode);
        inboundProcessorResponseDTO.setErrorMessage(errorMessage);
        return inboundProcessorResponseDTO;
    }

    /**
     * Finds matching VerbInfoDTO for the subscription operation.
     *
     * @param inboundMessageContext InboundMessageContext
     * @param operation             subscription operation name
     * @return VerbInfoDTO
     */
    public static VerbInfoDTO findMatchingVerb(InboundMessageContext inboundMessageContext, String operation) {
        String resourceCacheKey;
        VerbInfoDTO verbInfoDTO = null;
        if (inboundMessageContext.getResourcesMap() != null) {
            ResourceInfoDTO resourceInfoDTO = inboundMessageContext.getResourcesMap().get(operation);
            Set<VerbInfoDTO> verbDTOList = resourceInfoDTO.getHttpVerbs();
            for (VerbInfoDTO verb : verbDTOList) {
                if (verb.getHttpVerb().equals(GraphQLConstants.SubscriptionConstants.HTTP_METHOD_NAME)) {
                    if (isResourcePathMatching(operation, resourceInfoDTO)) {
                        verbInfoDTO = verb;
                        resourceCacheKey = APIUtil.getResourceInfoDTOCacheKey(
                                inboundMessageContext.getElectedAPI().getContext(),
                                inboundMessageContext.getElectedAPI().getApiVersion(), operation,
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

    /**
     * Validate query depth and complexity of graphql subscription payload.
     *
     * @param subscriptionAnalyzer  Query complexity and depth analyzer for subscription operations
     * @param inboundMessageContext InboundMessageContext
     * @param payload               GraphQL payload
     * @param operationId           Graphql message id
     * @return InboundProcessorResponseDTO
     */
    private InboundProcessorResponseDTO validateQueryDepthAndComplexity(SubscriptionAnalyzer subscriptionAnalyzer,
            InboundMessageContext inboundMessageContext, String payload, String operationId) {

        InboundProcessorResponseDTO responseDTO = validateQueryDepth(subscriptionAnalyzer,
                inboundMessageContext.getInfoDTO(), payload, operationId);
        if (!responseDTO.isError()) {
            return validateQueryComplexity(subscriptionAnalyzer, inboundMessageContext.getInfoDTO(), payload,
                    operationId);
        }
        return responseDTO;
    }

    /**
     * Validate query complexity of graphql subscription payload.
     *
     * @param subscriptionAnalyzer Query complexity and depth analyzer for subscription operations
     * @param infoDTO              APIKeyValidationInfoDTO
     * @param payload              GraphQL payload
     * @param operationId          Graphql message id
     * @return InboundProcessorResponseDTO
     */
    private InboundProcessorResponseDTO validateQueryComplexity(SubscriptionAnalyzer subscriptionAnalyzer,
            APIKeyValidationInfoDTO infoDTO, String payload, String operationId) {

        InboundProcessorResponseDTO responseDTO = new InboundProcessorResponseDTO();
        responseDTO.setId(operationId);
        try {
            QueryAnalyzerResponseDTO queryAnalyzerResponseDTO = subscriptionAnalyzer.analyseSubscriptionQueryComplexity(
                    payload, infoDTO.getGraphQLMaxComplexity());
            if (!queryAnalyzerResponseDTO.isSuccess() && !queryAnalyzerResponseDTO.getErrorList().isEmpty()) {
                List<String> errorList = queryAnalyzerResponseDTO.getErrorList();
                log.error("Query complexity validation failed for: " + payload + " errors: " + errorList.toString());
                responseDTO.setError(true);
                responseDTO.setErrorCode(GraphQLConstants.FrameErrorConstants.GRAPHQL_QUERY_TOO_COMPLEX);
                responseDTO.setErrorMessage(
                        GraphQLConstants.FrameErrorConstants.GRAPHQL_QUERY_TOO_COMPLEX_MESSAGE + " : "
                                + queryAnalyzerResponseDTO.getErrorList().toString());
                return responseDTO;
            }
        } catch (APIManagementException e) {
            log.error("Error while validating query complexity for: " + payload, e);
            responseDTO.setError(true);
            responseDTO.setErrorMessage(e.getMessage());
            responseDTO.setErrorCode(GraphQLConstants.FrameErrorConstants.INTERNAL_SERVER_ERROR);
        }
        return responseDTO;
    }

    /**
     * Validate query depth of graphql subscription payload.
     *
     * @param subscriptionAnalyzer Query complexity and depth analyzer for subscription operations
     * @param infoDTO              APIKeyValidationInfoDTO
     * @param payload              GraphQL payload
     * @param operationId          GraphQL message Id
     * @return InboundProcessorResponseDTO
     */
    private InboundProcessorResponseDTO validateQueryDepth(SubscriptionAnalyzer subscriptionAnalyzer,
            APIKeyValidationInfoDTO infoDTO, String payload, String operationId) {

        InboundProcessorResponseDTO responseDTO = new InboundProcessorResponseDTO();
        responseDTO.setId(operationId);
        QueryAnalyzerResponseDTO queryAnalyzerResponseDTO = subscriptionAnalyzer.analyseSubscriptionQueryDepth(
                infoDTO.getGraphQLMaxDepth(), payload);
        if (!queryAnalyzerResponseDTO.isSuccess() && !queryAnalyzerResponseDTO.getErrorList().isEmpty()) {
            List<String> errorList = queryAnalyzerResponseDTO.getErrorList();
            log.error("Query depth validation failed for: " + payload + " errors: " + errorList.toString());
            responseDTO.setError(true);
            responseDTO.setErrorCode(GraphQLConstants.FrameErrorConstants.GRAPHQL_QUERY_TOO_DEEP);
            responseDTO.setErrorMessage(GraphQLConstants.FrameErrorConstants.GRAPHQL_QUERY_TOO_DEEP_MESSAGE + " : "
                    + queryAnalyzerResponseDTO.getErrorList().toString());
            return responseDTO;
        }
        return responseDTO;
    }

    /**
     * Checks if the request is throttled for GraphQL subscriptions.
     *
     * @param msg                   Websocket frame
     * @param ctx                   Channel handler context
     * @param verbInfoDTO           InboundMessageContext
     * @param inboundMessageContext VerbInfoDTO for invoking operation
     * @param operationId           Operation ID
     * @return InboundProcessorResponseDTO
     */
    public static InboundProcessorResponseDTO doThrottleForGraphQL(WebSocketFrame msg, ChannelHandlerContext ctx,
            VerbInfoDTO verbInfoDTO, InboundMessageContext inboundMessageContext, String operationId) {

        InboundProcessorResponseDTO responseDTO = new InboundProcessorResponseDTO();
        responseDTO.setId(operationId);
        WebsocketInboundHandler websocketInboundHandler = new WebsocketInboundHandler();

        boolean isAllowed = websocketInboundHandler.doThrottle(ctx, msg, verbInfoDTO, inboundMessageContext);

        if (isAllowed) {
            return responseDTO;
        } else {
            responseDTO.setError(true);
            responseDTO.setErrorCode(GraphQLConstants.FrameErrorConstants.THROTTLED_OUT_ERROR);
            responseDTO.setErrorMessage(GraphQLConstants.FrameErrorConstants.THROTTLED_OUT_ERROR_MESSAGE);
        }
        return responseDTO;
    }
}
