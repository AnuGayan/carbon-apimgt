/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.carbon.apimgt.gateway.handlers;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.apache.axiom.util.UIDGenerator;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpHeaders;
import org.json.JSONObject;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.model.subscription.URLMapping;
import org.wso2.carbon.apimgt.gateway.APIMgtGatewayConstants;
import org.wso2.carbon.apimgt.gateway.InboundMessageContextDataHolder;
import org.wso2.carbon.apimgt.gateway.handlers.graphQL.GraphQLConstants;
import org.wso2.carbon.apimgt.gateway.handlers.graphQL.InboundProcessorResponseDTO;
import org.wso2.carbon.apimgt.gateway.handlers.graphQL.GraphQLRequestProcessor;
import org.wso2.carbon.apimgt.gateway.handlers.security.APIKeyValidator;
import org.wso2.carbon.apimgt.gateway.handlers.security.APISecurityConstants;
import org.wso2.carbon.apimgt.gateway.handlers.security.APISecurityException;
import org.wso2.carbon.apimgt.gateway.handlers.security.APISecurityUtils;
import org.wso2.carbon.apimgt.gateway.handlers.security.AuthenticationContext;
import org.wso2.carbon.apimgt.gateway.handlers.security.jwt.JWTValidator;
import org.wso2.carbon.apimgt.gateway.handlers.throttling.APIThrottleConstants;
import org.wso2.carbon.apimgt.gateway.internal.ServiceReferenceHolder;
import org.wso2.carbon.apimgt.gateway.utils.APIMgtGoogleAnalyticsUtils;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.APIManagerAnalyticsConfiguration;
import org.wso2.carbon.apimgt.impl.caching.CacheProvider;
import org.wso2.carbon.apimgt.impl.dto.APIKeyValidationInfoDTO;
import org.wso2.carbon.apimgt.impl.dto.ResourceInfoDTO;
import org.wso2.carbon.apimgt.impl.dto.VerbInfoDTO;
import org.wso2.carbon.apimgt.impl.jwt.SignedJWTInfo;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.apimgt.usage.publisher.APIMgtUsageDataPublisher;
import org.wso2.carbon.apimgt.usage.publisher.DataPublisherUtil;
import org.wso2.carbon.apimgt.usage.publisher.dto.ExecutionTimeDTO;
import org.wso2.carbon.apimgt.usage.publisher.dto.RequestResponseStreamDTO;
import org.wso2.carbon.apimgt.usage.publisher.dto.ThrottlePublisherDTO;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.ganalytics.publisher.GoogleAnalyticsData;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;
import org.wso2.carbon.utils.multitenancy.MultitenantUtils;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.cache.Cache;

/**
 * This is a handler which is actually embedded to the netty pipeline which does operations such as
 * authentication and throttling for the websocket handshake and subsequent websocket frames.
 */
public class WebsocketInboundHandler extends ChannelInboundHandlerAdapter {
    private static final Log log = LogFactory.getLog(WebsocketInboundHandler.class);
    private static APIMgtUsageDataPublisher usageDataPublisher;

    public WebsocketInboundHandler() {
        initializeDataPublisher();
    }

    private void initializeDataPublisher() {
        if (APIUtil.isAnalyticsEnabled() && usageDataPublisher == null) {
            String publisherClass = getApiManagerAnalyticsConfiguration().getPublisherClass();

            try {
                synchronized (this) {
                    if (usageDataPublisher == null) {
                        try {
                            log.debug("Instantiating Web Socket Data Publisher");
                            usageDataPublisher = (APIMgtUsageDataPublisher) APIUtil.getClassForName(publisherClass)
                                    .newInstance();
                            usageDataPublisher.init();
                        } catch (ClassNotFoundException e) {
                            log.error("Class not found " + publisherClass, e);
                        } catch (InstantiationException e) {
                            log.error("Error instantiating " + publisherClass, e);
                        } catch (IllegalAccessException e) {
                            log.error("Illegal access to " + publisherClass, e);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Cannot publish event. " + e.getMessage(), e);
            }
        }
    }

    /**
     * extract the version from the request uri
     *
     * @param url
     * @return version String
     */
    private String getVersionFromUrl(final String url) {
        return url.replaceFirst(".*/([^/?]+).*", "$1");
    }

    //method removed because url is going to be always null
/*    private String getContextFromUrl(String url) {
        int lastIndex = 0;
        if (url != null) {
            lastIndex = url.lastIndexOf('/');
            return url.substring(0, lastIndex);
        } else {
            return "";
        }
    }*/

    @SuppressWarnings("unchecked")
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg)
            throws Exception {

        String channelId = ctx.channel().id().asLongText();
        InboundMessageContext inboundMessageContext;
        if (InboundMessageContextDataHolder.getInstance().getInboundMessageContextMap().containsKey(channelId)) {
            inboundMessageContext = InboundMessageContextDataHolder.getInstance()
                    .getInboundMessageContextForConnectionId(channelId);
        } else {
            inboundMessageContext = new InboundMessageContext();
            InboundMessageContextDataHolder.getInstance()
                    .addInboundMessageContextForConnection(channelId, inboundMessageContext);
        }
        inboundMessageContext.setUserIP(getRemoteIP(ctx));

        //check if the request is a handshake
        if (msg instanceof FullHttpRequest) {
            FullHttpRequest req = (FullHttpRequest) msg;

            // This block is for the health check of the ports 8099 and 9099
            if (!req.headers().contains(HttpHeaders.UPGRADE)) {
                FullHttpResponse httpResponse =
                        new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                httpResponse.headers().set("content-type", "text/plain; charset=UTF-8");
                httpResponse.headers().set("content-length", httpResponse.content().readableBytes());
                ctx.writeAndFlush(httpResponse);
                return;
            }

            inboundMessageContext.setUri(req.getUri());
            URI uriTemp = new URI(inboundMessageContext.getUri());
            String apiContextUri = new URI(uriTemp.getScheme(), uriTemp.getAuthority(), uriTemp.getPath(), null,
                    uriTemp.getFragment()).toString();
            apiContextUri = apiContextUri.endsWith("/") ?
                    apiContextUri.substring(0, apiContextUri.length() - 1) :
                    apiContextUri;
            inboundMessageContext.setApiContextUri(apiContextUri);

            if (log.isDebugEnabled()) {
                log.debug("Websocket API apiContextUri = " + apiContextUri);
            }
            if (req.getUri().contains("/t/")) {
                inboundMessageContext.setTenantDomain(MultitenantUtils.getTenantDomainFromUrl(req.getUri()));
            } else {
                inboundMessageContext.setTenantDomain(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME);
            }

            inboundMessageContext.setElectedAPI(
                    WebsocketUtil.getApi(req.uri(), inboundMessageContext.getTenantDomain()));
            setResourcesMapToContext(inboundMessageContext);

            String useragent = req.headers().get(HttpHeaders.USER_AGENT);

            // '-' is used for empty values to avoid possible errors in DAS side.
            // Required headers are stored one by one as validateOAuthHeader()
            // removes some of the headers from the request
            useragent = useragent != null ? useragent : "-";
            inboundMessageContext.setHeaders(inboundMessageContext.getHeaders().add(HttpHeaders.USER_AGENT, useragent));

            InboundProcessorResponseDTO responseDTO = validateOAuthHeader(req, inboundMessageContext);
            if (!responseDTO.isError()) {
                if (!MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(inboundMessageContext.getTenantDomain())) {
                    // carbon-mediation only support websocket invocation from super tenant APIs.
                    // This is a workaround to mimic the the invocation came from super tenant.
                    req.setUri(req.getUri().replaceFirst("/", "-"));
                    String modifiedUri = inboundMessageContext.getUri().replaceFirst("/t/", "-t/");
                    req.setUri(modifiedUri);
                    msg = req;
                } else {
                    req.setUri(inboundMessageContext.getUri()); // Setting endpoint appended uri
                }

                if (StringUtils.isNotEmpty(inboundMessageContext.getToken())) {
                    ((FullHttpRequest) msg).headers()
                            .set(APIMgtGatewayConstants.WS_JWT_TOKEN_HEADER, inboundMessageContext.getToken());
                }
                ctx.fireChannelRead(msg);

                // publish google analytics data
                GoogleAnalyticsData.DataBuilder gaData = new GoogleAnalyticsData.DataBuilder(null, null, null,
                        null).setDocumentPath(inboundMessageContext.getUri())
                        .setDocumentHostName(DataPublisherUtil.getHostAddress()).setSessionControl("end")
                        .setCacheBuster(APIMgtGoogleAnalyticsUtils.getCacheBusterId())
                        .setIPOverride(ctx.channel().remoteAddress().toString());
                APIMgtGoogleAnalyticsUtils gaUtils = new APIMgtGoogleAnalyticsUtils();
                gaUtils.init(inboundMessageContext.getTenantDomain());
                gaUtils.publishGATrackingData(gaData, req.headers().get(HttpHeaders.USER_AGENT),
                        inboundMessageContext.getHeaders().get(HttpHeaders.AUTHORIZATION));
            } else {
                InboundMessageContextDataHolder.getInstance().removeInboundMessageContextForConnection(channelId);
                if (APIConstants.APITransportType.GRAPHQL.toString()
                        .equals(inboundMessageContext.getElectedAPI().getApiType())) {
                    String errorMessage = "No Authorization Header or access_token query parameter present";
                    log.error(errorMessage + " in request for the websocket context "
                            + inboundMessageContext.getApiContextUri());
                    responseDTO = GraphQLRequestProcessor.getHandshakeErrorDTO(
                            GraphQLConstants.HandshakeErrorConstants.API_AUTH_ERROR, errorMessage);
                } else {
                    // If not a GraphQL API (Only a WebSocket API)
                    responseDTO.setError(true);
                    responseDTO.setErrorMessage(APISecurityConstants.API_AUTH_INVALID_CREDENTIALS_MESSAGE);
                    responseDTO.setErrorCode(HttpResponseStatus.UNAUTHORIZED.code());
                }
                WebsocketUtil.sendInvalidCredentialsMessage(ctx, inboundMessageContext, responseDTO);
            }
        } else if ((msg instanceof CloseWebSocketFrame) || (msg instanceof PingWebSocketFrame)) {
            //remove inbound message context from data holder
            InboundMessageContextDataHolder.getInstance().getInboundMessageContextMap().remove(channelId);
            //if the inbound frame is a closed frame, throttling, analytics will not be published.
            ctx.fireChannelRead(msg);
        } else if (msg instanceof WebSocketFrame) {

            if (APIConstants.APITransportType.GRAPHQL.toString()
                    .equals(inboundMessageContext.getElectedAPI().getApiType()) && msg instanceof TextWebSocketFrame) {
                // Authenticate and handle GraphQL subscription requests
                GraphQLRequestProcessor graphQLRequestProcessor = new GraphQLRequestProcessor();
                InboundProcessorResponseDTO responseDTO = graphQLRequestProcessor.handleRequest((WebSocketFrame) msg,
                        ctx, inboundMessageContext);
                if (responseDTO.isError()) {
                    handleGraphQLRequestError(responseDTO, channelId, ctx);
                } else {
                    handleWSRequestSuccess(ctx, msg, inboundMessageContext);
                }
            } else {
                // If not a GraphQL API (Only a WebSocket API)
                boolean isAllowed = doThrottle(ctx, (WebSocketFrame) msg, null, inboundMessageContext);

                if (isAllowed) {
                    handleWSRequestSuccess(ctx, msg, inboundMessageContext);
                } else {
                    ctx.writeAndFlush(new TextWebSocketFrame("Websocket frame throttled out"));
                    if (log.isDebugEnabled()) {
                        log.debug("Inbound Websocket frame is throttled. " + ctx.channel().toString());
                    }
                }
            }
        }
    }

    /**
     * Authenticate request
     *
     * @param req Full Http Request
     * @return true if the access token is valid
     */
    public InboundProcessorResponseDTO validateOAuthHeader(FullHttpRequest req,
            InboundMessageContext inboundMessageContext) throws APISecurityException {

        InboundProcessorResponseDTO responseDTO = new InboundProcessorResponseDTO();

        try {
            PrivilegedCarbonContext.startTenantFlow();
            PrivilegedCarbonContext.getThreadLocalCarbonContext()
                    .setTenantDomain(inboundMessageContext.getTenantDomain(), true);
            inboundMessageContext.setVersion(getVersionFromUrl(inboundMessageContext.getUri()));
            APIKeyValidationInfoDTO info = null;
            if (!req.headers().contains(HttpHeaders.AUTHORIZATION)) {
                QueryStringDecoder decoder = new QueryStringDecoder(req.getUri());
                Map<String, List<String>> requestMap = decoder.parameters();
                if (requestMap.containsKey(APIConstants.AUTHORIZATION_QUERY_PARAM_DEFAULT)) {
                    req.headers().add(HttpHeaders.AUTHORIZATION,
                            APIConstants.CONSUMER_KEY_SEGMENT + ' ' + requestMap.get(
                                    APIConstants.AUTHORIZATION_QUERY_PARAM_DEFAULT).get(0));
                    removeTokenFromQuery(requestMap, inboundMessageContext);
                } else {
                    log.error("No Authorization Header or access_token query parameter present");
                    responseDTO.setError(true);
                }
            }
            String authorizationHeader = req.headers().get(HttpHeaders.AUTHORIZATION);
            inboundMessageContext.setHeaders(
                    inboundMessageContext.getHeaders().add(HttpHeaders.AUTHORIZATION, authorizationHeader));
            String[] auth = authorizationHeader.split(" ");
            if (APIConstants.CONSUMER_KEY_SEGMENT.equals(auth[0])) {
                String cacheKey;
                boolean isJwtToken = false;
                String apiKey = auth[1];
                if (WebsocketUtil.isRemoveOAuthHeadersFromOutMessage()) {
                    req.headers().remove(HttpHeaders.AUTHORIZATION);
                }

                //Initial guess of a JWT token using the presence of a DOT.
                inboundMessageContext.setSignedJWTInfo(null);
                if (StringUtils.isNotEmpty(apiKey) && apiKey.contains(APIConstants.DOT)) {
                    try {
                        // Check if the header part is decoded
                        if (StringUtils.countMatches(apiKey, APIConstants.DOT) != 2) {
                            log.debug("Invalid JWT token. The expected token format is <header.payload.signature>");
                            throw new APISecurityException(APISecurityConstants.API_AUTH_INVALID_CREDENTIALS,
                                    "Invalid JWT token");
                        }
                        inboundMessageContext.setSignedJWTInfo(getSignedJwtInfo(apiKey));
                        String keyManager = ServiceReferenceHolder.getInstance().getJwtValidationService()
                                .getKeyManagerNameIfJwtValidatorExist(inboundMessageContext.getSignedJWTInfo());
                        if (StringUtils.isNotEmpty(keyManager)) {
                            isJwtToken = true;
                        }
                    } catch (ParseException e) {
                        log.debug("Not a JWT token. Failed to decode the token header.", e);
                    } catch (APIManagementException e) {
                        log.error("error while check validation of JWt", e);
                        throw new APISecurityException(APISecurityConstants.API_AUTH_GENERAL_ERROR,
                                APISecurityConstants.API_AUTH_GENERAL_ERROR_MESSAGE);
                    }
                }
                // Find the authentication scheme based on the token type
                String apiVersion = inboundMessageContext.getVersion();
                boolean isDefaultVersion = false;
                if ((inboundMessageContext.getApiContextUri().startsWith("/" + inboundMessageContext.getVersion())
                        || inboundMessageContext.getApiContextUri().startsWith(
                        "/t/" + inboundMessageContext.getTenantDomain() + "/" + inboundMessageContext.getVersion()))) {
                    apiVersion = APIConstants.DEFAULT_WEBSOCKET_VERSION;
                    isDefaultVersion = true;
                }
                if (isJwtToken) {
                    log.debug("The token was identified as a JWT token");

                    if (APIConstants.APITransportType.GRAPHQL.toString()
                            .equals(inboundMessageContext.getElectedAPI().getApiType())) {
                        responseDTO = GraphQLRequestProcessor.authenticateGraphQLJWTToken(inboundMessageContext);
                    } else {
                        responseDTO = authenticateWSJWTToken(inboundMessageContext, isDefaultVersion);
                    }
                } else {
                    log.debug("The token was identified as an OAuth token");
                    //If the key have already been validated
                    if (WebsocketUtil.isGatewayTokenCacheEnabled()) {
                        cacheKey = WebsocketUtil.getAccessTokenCacheKey(apiKey, inboundMessageContext.getUri());
                        info = WebsocketUtil.validateCache(apiKey, cacheKey);
                        if (info != null) {

                            //This prefix is added for synapse to dispatch this request to the specific sequence
                            if (APIConstants.API_KEY_TYPE_PRODUCTION.equals(info.getType())) {
                                inboundMessageContext.setUri("/_PRODUCTION_" + inboundMessageContext.getUri());
                            } else if (APIConstants.API_KEY_TYPE_SANDBOX.equals(info.getType())) {
                                inboundMessageContext.setUri("/_SANDBOX_" + inboundMessageContext.getUri());
                            }

                            inboundMessageContext.setInfoDTO(info);
                            responseDTO.setError(info.isAuthorized());
                        }
                    }
                    String keyValidatorClientType = APISecurityUtils.getKeyValidatorClientType();
                    if (APIConstants.API_KEY_VALIDATOR_WS_CLIENT.equals(keyValidatorClientType)) {
                        info = getApiKeyDataForWSClient(apiKey, inboundMessageContext.getTenantDomain(),
                                inboundMessageContext.getApiContextUri(), apiVersion);
                    } else {
                        responseDTO.setError(true);
                    }
                    if (info == null || !info.isAuthorized()) {
                        responseDTO.setError(true);
                    }
                    if (info.getApiName() != null && info.getApiName().contains("*")) {
                        String[] str = info.getApiName().split("\\*");
                        inboundMessageContext.setVersion(str[1]);
                        inboundMessageContext.setUri("/" + str[1]);
                        info.setApiName(str[0]);
                    }
                    if (WebsocketUtil.isGatewayTokenCacheEnabled()) {
                        cacheKey = WebsocketUtil.getAccessTokenCacheKey(apiKey, inboundMessageContext.getUri());
                        WebsocketUtil.putCache(info, apiKey, cacheKey);
                    }
                    //This prefix is added for synapse to dispatch this request to the specific sequence
                    if (APIConstants.API_KEY_TYPE_PRODUCTION.equals(info.getType())) {
                        inboundMessageContext.setUri("/_PRODUCTION_" + inboundMessageContext.getUri());
                    } else if (APIConstants.API_KEY_TYPE_SANDBOX.equals(info.getType())) {
                        inboundMessageContext.setUri("/_SANDBOX_" + inboundMessageContext.getUri());
                    }
                    inboundMessageContext.setToken(info.getEndUserToken());
                    inboundMessageContext.setInfoDTO(info);
                    responseDTO.setError(false);
                }
            } else {
                responseDTO.setError(true);
            }
        } finally {
            PrivilegedCarbonContext.endTenantFlow();
        }
        return responseDTO;
    }

    protected APIKeyValidationInfoDTO getApiKeyDataForWSClient(String key, String domain, String apiContextUri,
            String apiVersion) throws APISecurityException {

        return new WebsocketWSClient().getAPIKeyData(apiContextUri, apiVersion, key, domain);
    }

    protected APIManagerAnalyticsConfiguration getApiManagerAnalyticsConfiguration() {
        return DataPublisherUtil.getApiManagerAnalyticsConfiguration();
    }

    /**
     * Checks if the request is throttled
     *
     * @param ctx                   ChannelHandlerContext
     * @param msg                   WebSocketFrame
     * @param verbInfoDTO           VerbInfoDTO
     * @param inboundMessageContext InboundMessageContext
     * @return false if throttled
     * @throws APIManagementException
     */
    public boolean doThrottle(ChannelHandlerContext ctx, WebSocketFrame msg, VerbInfoDTO verbInfoDTO,
            InboundMessageContext inboundMessageContext) {

        APIKeyValidationInfoDTO infoDTO = inboundMessageContext.getInfoDTO();
        String apiName = infoDTO.getApiName();
        String apiContext = inboundMessageContext.getApiContextUri();
        String apiVersion = inboundMessageContext.getVersion();
        String applicationLevelTier = infoDTO.getApplicationTier();

        String apiLevelTier = infoDTO.getApiTier();
        String apiLevelThrottleKey = apiContext + ":" + apiVersion;
        String subscriptionLevelTier = infoDTO.getTier();
        String resourceLevelTier;
        String resourceLevelThrottleKey;

        // If API level throttle policy is present then it will apply and no resource level policy will apply for it
        if (verbInfoDTO == null) {
            resourceLevelThrottleKey = apiLevelThrottleKey;
            resourceLevelTier = apiLevelTier;
        } else {
            resourceLevelThrottleKey = verbInfoDTO.getRequestKey();
            resourceLevelTier = verbInfoDTO.getThrottling();
        }

        String authorizedUser;
        if (MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equalsIgnoreCase(infoDTO.getSubscriberTenantDomain())) {
            authorizedUser = infoDTO.getSubscriber() + "@" + infoDTO.getSubscriberTenantDomain();
        } else {
            authorizedUser = infoDTO.getSubscriber();
        }

        String appTenant = infoDTO.getSubscriberTenantDomain();
        String apiTenant = inboundMessageContext.getTenantDomain();
        String appId = infoDTO.getApplicationId();
        String applicationLevelThrottleKey = appId + ":" + authorizedUser;
        String subscriptionLevelThrottleKey = appId + ":" + apiContext + ":" + apiVersion;
        String messageId = UIDGenerator.generateURNString();
        String remoteIP = getRemoteIP(ctx);
        if (log.isDebugEnabled()) {
            log.debug("Remote IP address : " + remoteIP);
        }
        if (remoteIP.indexOf(":") > 0) {
            remoteIP = remoteIP.substring(1, remoteIP.indexOf(":"));
        }
        JSONObject jsonObMap = new JSONObject();
        if (remoteIP != null && remoteIP.length() > 0) {
            try {
                InetAddress address = APIUtil.getAddress(remoteIP);
                if (address instanceof Inet4Address) {
                    jsonObMap.put(APIThrottleConstants.IP, APIUtil.ipToLong(remoteIP));
                } else if (address instanceof Inet6Address) {
                    jsonObMap.put(APIThrottleConstants.IPv6, APIUtil.ipToBigInteger(remoteIP));
                }
            } catch (UnknownHostException e) {
                //ignore the error and log it
                log.error("Error while parsing host IP " + remoteIP, e);
            }
        }
        jsonObMap.put(APIThrottleConstants.MESSAGE_SIZE, msg.content().capacity());
        try {
            PrivilegedCarbonContext.startTenantFlow();
            PrivilegedCarbonContext.getThreadLocalCarbonContext()
                    .setTenantDomain(inboundMessageContext.getTenantDomain(), true);
            boolean isThrottled = WebsocketUtil.isThrottled(resourceLevelThrottleKey, subscriptionLevelThrottleKey,
                    applicationLevelThrottleKey);
            if (isThrottled) {
                if (APIUtil.isAnalyticsEnabled()) {
                    publishThrottleEvent(inboundMessageContext);
                }
                return false;
            }
        } finally {
            PrivilegedCarbonContext.endTenantFlow();
        }
        Object[] objects = new Object[] { messageId, applicationLevelThrottleKey, applicationLevelTier,
                apiLevelThrottleKey, apiLevelTier, subscriptionLevelThrottleKey, subscriptionLevelTier,
                resourceLevelThrottleKey, resourceLevelTier, authorizedUser, apiContext, apiVersion, appTenant,
                apiTenant, appId, apiName, jsonObMap.toString() };
        org.wso2.carbon.databridge.commons.Event event = new org.wso2.carbon.databridge.commons.Event(
                "org.wso2.throttle.request.stream:1.0.0", System.currentTimeMillis(), null, null, objects);
        if (ServiceReferenceHolder.getInstance().getThrottleDataPublisher() == null) {
            log.error("Cannot publish events to traffic manager because ThrottleDataPublisher "
                    + "has not been initialised");
            return true;
        }
        ServiceReferenceHolder.getInstance().getThrottleDataPublisher().getDataPublisher().tryPublish(event);
        return true;
    }

    public String getRemoteIP(ChannelHandlerContext ctx) {
        return ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress().getHostAddress();
    }

    /**
     * Publish reuqest event to analytics server
     *
     * @param clientIp       client's IP Address
     * @param isThrottledOut request is throttled out or not
     */
    public void publishRequestEvent(String clientIp, boolean isThrottledOut,
            InboundMessageContext inboundMessageContext) {
        long requestTime = System.currentTimeMillis();
        String useragent = inboundMessageContext.getHeaders().get(HttpHeaders.USER_AGENT);

        try {
            APIKeyValidationInfoDTO infoDTO = inboundMessageContext.getInfoDTO();
            String appOwner = infoDTO.getSubscriber();
            String keyType = infoDTO.getType();
            String correlationID = UUID.randomUUID().toString();

            RequestResponseStreamDTO requestPublisherDTO = new RequestResponseStreamDTO();
            requestPublisherDTO.setApiName(infoDTO.getApiName());
            requestPublisherDTO.setApiCreator(infoDTO.getApiPublisher());
            requestPublisherDTO.setApiCreatorTenantDomain(MultitenantUtils.getTenantDomain(infoDTO.getApiPublisher()));
            requestPublisherDTO.setApiVersion(infoDTO.getApiName() + ':' + inboundMessageContext.getVersion());
            requestPublisherDTO.setApplicationId(infoDTO.getApplicationId());
            requestPublisherDTO.setApplicationName(infoDTO.getApplicationName());
            requestPublisherDTO.setApplicationOwner(appOwner);
            requestPublisherDTO.setUserIp(clientIp);
            requestPublisherDTO.setApplicationConsumerKey(infoDTO.getConsumerKey());
            //context will always be empty as this method will call only for WebSocketFrame and url is null
            requestPublisherDTO.setApiContext(inboundMessageContext.getApiContextUri());
            requestPublisherDTO.setThrottledOut(isThrottledOut);
            requestPublisherDTO.setApiHostname(DataPublisherUtil.getHostAddress());
            requestPublisherDTO.setApiMethod("-");
            requestPublisherDTO.setRequestTimestamp(requestTime);
            requestPublisherDTO.setApiResourcePath("-");
            requestPublisherDTO.setApiResourceTemplate("-");
            requestPublisherDTO.setUserAgent(useragent);
            requestPublisherDTO.setUsername(infoDTO.getEndUserName());
            requestPublisherDTO.setUserTenantDomain(inboundMessageContext.getTenantDomain());
            requestPublisherDTO.setApiTier(infoDTO.getTier());
            requestPublisherDTO.setApiVersion(inboundMessageContext.getVersion());
            requestPublisherDTO.setMetaClientType(keyType);
            requestPublisherDTO.setCorrelationID(correlationID);
            requestPublisherDTO.setUserAgent(useragent);
            requestPublisherDTO.setCorrelationID(correlationID);
            requestPublisherDTO.setGatewayType(APIMgtGatewayConstants.GATEWAY_TYPE);
            requestPublisherDTO.setLabel(APIMgtGatewayConstants.SYNAPDE_GW_LABEL);
            requestPublisherDTO.setProtocol("WebSocket");
            requestPublisherDTO.setDestination("-");
            requestPublisherDTO.setBackendTime(0);
            requestPublisherDTO.setResponseCacheHit(false);
            requestPublisherDTO.setResponseCode(0);
            requestPublisherDTO.setResponseSize(0);
            requestPublisherDTO.setServiceTime(0);
            requestPublisherDTO.setResponseTime(0);
            ExecutionTimeDTO executionTime = new ExecutionTimeDTO();
            executionTime.setBackEndLatency(0);
            executionTime.setOtherLatency(0);
            executionTime.setRequestMediationLatency(0);
            executionTime.setResponseMediationLatency(0);
            executionTime.setSecurityLatency(0);
            executionTime.setThrottlingLatency(0);
            requestPublisherDTO.setExecutionTime(executionTime);
            usageDataPublisher.publishEvent(requestPublisherDTO);
        } catch (Exception e) {
            // flow should not break if event publishing failed
            log.error("Cannot publish event. " + e.getMessage(), e);
        }

    }

    /**
     * Publish throttle events.
     *
     * @param inboundMessageContext InboundMessageContext
     */
    private void publishThrottleEvent(InboundMessageContext inboundMessageContext) {
        long requestTime = System.currentTimeMillis();
        String correlationID = UUID.randomUUID().toString();
        try {
            APIKeyValidationInfoDTO infoDTO = inboundMessageContext.getInfoDTO();
            ThrottlePublisherDTO throttlePublisherDTO = new ThrottlePublisherDTO();
            throttlePublisherDTO.setKeyType(infoDTO.getType());
            throttlePublisherDTO.setTenantDomain(inboundMessageContext.getTenantDomain());
            //throttlePublisherDTO.setApplicationConsumerKey(infoDTO.getConsumerKey());
            throttlePublisherDTO.setApiname(infoDTO.getApiName());
            throttlePublisherDTO.setVersion(infoDTO.getApiName() + ':' + inboundMessageContext.getVersion());
            throttlePublisherDTO.setContext(inboundMessageContext.getApiContextUri());
            throttlePublisherDTO.setApiCreator(infoDTO.getApiPublisher());
            throttlePublisherDTO.setApiCreatorTenantDomain(MultitenantUtils.getTenantDomain(infoDTO.getApiPublisher()));
            throttlePublisherDTO.setApplicationName(infoDTO.getApplicationName());
            throttlePublisherDTO.setApplicationId(infoDTO.getApplicationId());
            throttlePublisherDTO.setSubscriber(infoDTO.getSubscriber());
            throttlePublisherDTO.setThrottledTime(requestTime);
            throttlePublisherDTO.setGatewayType(APIMgtGatewayConstants.GATEWAY_TYPE);
            throttlePublisherDTO.setThrottledOutReason("-");
            throttlePublisherDTO.setUsername(infoDTO.getEndUserName());
            throttlePublisherDTO.setCorrelationID(correlationID);
            throttlePublisherDTO.setHostName(DataPublisherUtil.getHostAddress());
            throttlePublisherDTO.setAccessToken("-");
            usageDataPublisher.publishEvent(throttlePublisherDTO);
        } catch (Exception e) {
            // flow should not break if event publishing failed
            log.error("Cannot publish event. " + e.getMessage(), e);
        }
    }

    private void removeTokenFromQuery(Map<String, List<String>> parameters,
            InboundMessageContext inboundMessageContext) {
        String uri = inboundMessageContext.getUri();
        StringBuilder queryBuilder = new StringBuilder(uri.substring(0, uri.indexOf('?') + 1));

        for (Map.Entry<String, List<String>> entry : parameters.entrySet()) {
            if (!APIConstants.AUTHORIZATION_QUERY_PARAM_DEFAULT.equals(entry.getKey())) {
                queryBuilder.append(entry.getKey()).append('=').append(entry.getValue().get(0)).append('&');
            }
        }

        // remove trailing '?' or '&' from the built string
        uri = queryBuilder.substring(0, queryBuilder.length() - 1);
        inboundMessageContext.setUri(uri);
    }

    private SignedJWTInfo getSignedJwtInfo(String accessToken) throws ParseException {

        String signature = accessToken.split("\\.")[2];
        SignedJWTInfo signedJWTInfo = null;
        Cache gatewaySignedJWTParseCache = CacheProvider.getGatewaySignedJWTParseCache();
        if (gatewaySignedJWTParseCache != null) {
            Object cachedEntry = gatewaySignedJWTParseCache.get(signature);
            if (cachedEntry != null) {
                signedJWTInfo = (SignedJWTInfo) cachedEntry;
            }
            if (signedJWTInfo == null || !signedJWTInfo.getToken().equals(accessToken)) {
                SignedJWT signedJWT = SignedJWT.parse(accessToken);
                JWTClaimsSet jwtClaimsSet = signedJWT.getJWTClaimsSet();
                signedJWTInfo = new SignedJWTInfo(accessToken, signedJWT, jwtClaimsSet);
                gatewaySignedJWTParseCache.put(signature, signedJWTInfo);
            }
        } else {
            SignedJWT signedJWT = SignedJWT.parse(accessToken);
            JWTClaimsSet jwtClaimsSet = signedJWT.getJWTClaimsSet();
            signedJWTInfo = new SignedJWTInfo(accessToken, signedJWT, jwtClaimsSet);
        }
        return signedJWTInfo;
    }

    /**
     * Set the resource map with VerbInfoDTOs to the context using URL mappings from the InboundMessageContext.
     *
     * @param inboundMessageContext InboundMessageContext
     */
    private void setResourcesMapToContext(InboundMessageContext inboundMessageContext) {

        List<URLMapping> urlMappings = inboundMessageContext.getElectedAPI().getResources();
        Map<String, ResourceInfoDTO> resourcesMap = inboundMessageContext.getResourcesMap();

        ResourceInfoDTO resourceInfoDTO;
        VerbInfoDTO verbInfoDTO;
        for (URLMapping urlMapping : urlMappings) {
            resourceInfoDTO = resourcesMap.get(urlMapping.getUrlPattern());
            if (resourceInfoDTO == null) {
                resourceInfoDTO = new ResourceInfoDTO();
                resourceInfoDTO.setUrlPattern(urlMapping.getUrlPattern());
                resourceInfoDTO.setHttpVerbs(new LinkedHashSet<>());
                resourcesMap.put(urlMapping.getUrlPattern(), resourceInfoDTO);
            }
            verbInfoDTO = new VerbInfoDTO();
            verbInfoDTO.setHttpVerb(urlMapping.getHttpMethod());
            verbInfoDTO.setAuthType(urlMapping.getAuthScheme());
            verbInfoDTO.setThrottling(urlMapping.getThrottlingPolicy());
            resourceInfoDTO.getHttpVerbs().add(verbInfoDTO);
        }
    }

    /**
     * @param responseDTO InboundProcessorResponseDTO
     * @param channelId   Channel Id of the web socket connection
     * @param ctx         ChannelHandlerContext
     */
    private void handleGraphQLRequestError(InboundProcessorResponseDTO responseDTO, String channelId,
            ChannelHandlerContext ctx) {
        if (responseDTO.isCloseConnection()) {
            // remove inbound message context from data holder
            InboundMessageContextDataHolder.getInstance().getInboundMessageContextMap().remove(channelId);
            if (log.isDebugEnabled()) {
                log.debug("Error while handling Outbound Websocket frame. Closing connection for " + ctx.channel()
                        .toString());
            }
            ctx.writeAndFlush(new CloseWebSocketFrame(responseDTO.getErrorCode(),
                    responseDTO.getErrorMessage() + StringUtils.SPACE + "Connection closed" + "!"));
            ctx.close();
        } else {
            String errorMessage = responseDTO.getErrorResponseString();
            ctx.writeAndFlush(new TextWebSocketFrame(errorMessage));
            if (responseDTO.getErrorCode() == GraphQLConstants.FrameErrorConstants.THROTTLED_OUT_ERROR) {
                if (log.isDebugEnabled()) {
                    log.debug("Inbound Websocket frame is throttled. " + ctx.channel().toString());
                }
            }
        }
    }

    /**
     * @param ctx                   ChannelHandlerContext
     * @param msg                   Message
     * @param inboundMessageContext InboundMessageContext
     */
    private void handleWSRequestSuccess(ChannelHandlerContext ctx, Object msg,
            InboundMessageContext inboundMessageContext) {
        ctx.fireChannelRead(msg);
        // publish analytics events if analytics is enabled
        if (APIUtil.isAnalyticsEnabled()) {
            publishRequestEvent(inboundMessageContext.getUserIP(), true, inboundMessageContext);
        }
    }

    /**
     * @param inboundMessageContext InboundMessageContext
     * @param isDefaultVersion      Is default version or not
     * @return responseDTO
     * @throws APISecurityException If an error occurs while authenticating the WebSocket API
     */
    public InboundProcessorResponseDTO authenticateWSJWTToken(InboundMessageContext inboundMessageContext,
            Boolean isDefaultVersion) throws APISecurityException {
        InboundProcessorResponseDTO responseDTO = new InboundProcessorResponseDTO();
        AuthenticationContext authenticationContext = new JWTValidator(
                new APIKeyValidator()).authenticateForWSAndGraphQL(inboundMessageContext);
        inboundMessageContext.setAuthContext(authenticationContext);
        if (!WebsocketUtil.validateAuthenticationContext(inboundMessageContext, isDefaultVersion)) {
            responseDTO.setError(true);
        }
        return responseDTO;
    }
}
