/*
 *   Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *   WSO2 Inc. licenses this file to you under the Apache License,
 *   Version 2.0 (the "License"); you may not use this file except
 *   in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.wso2.carbon.apimgt.gateway.handlers;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.wso2.carbon.apimgt.gateway.InboundMessageContextDataHolder;
import org.wso2.carbon.apimgt.gateway.dto.GraphQLOperationDTO;
import org.wso2.carbon.apimgt.gateway.dto.InboundProcessorResponseDTO;
import org.wso2.carbon.apimgt.gateway.graphQL.GraphQLConstants;
import org.wso2.carbon.apimgt.gateway.graphQL.GraphQLProcessor;
import org.wso2.carbon.apimgt.gateway.internal.ServiceReferenceHolder;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.APIManagerAnalyticsConfiguration;
import org.wso2.carbon.apimgt.impl.APIManagerConfiguration;
import org.wso2.carbon.apimgt.impl.dto.VerbInfoDTO;
import org.wso2.carbon.apimgt.keymgt.model.entity.API;
import org.wso2.carbon.apimgt.usage.publisher.APIMgtUsageDataPublisher;
import org.wso2.carbon.apimgt.usage.publisher.DataPublisherUtil;
import org.wso2.carbon.context.PrivilegedCarbonContext;

import java.util.UUID;

/**
 * Test class for WebsocketHandler
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ PrivilegedCarbonContext.class, DataPublisherUtil.class, GraphQLProcessor.class,
        WebsocketUtil.class, ServiceReferenceHolder.class })
public class WebsocketHandlerTestCase {

    private static final String channelIdString = "11111";
    private ChannelHandlerContext channelHandlerContext;
    private ChannelPromise channelPromise;
    private WebsocketHandler websocketHandler;
    private WebSocketFrame msg;
    private org.wso2.carbon.apimgt.keymgt.model.entity.API websocketAPI;
    private org.wso2.carbon.apimgt.keymgt.model.entity.API graphQLAPI;
    private ServiceReferenceHolder serviceReferenceHolder;
    private APIManagerConfiguration apiManagerConfiguration;

    @Before
    public void setup() {
        PowerMockito.mockStatic(GraphQLProcessor.class);
        PowerMockito.mockStatic(ServiceReferenceHolder.class);
        PowerMockito.mockStatic(ServiceReferenceHolder.class);
        serviceReferenceHolder = Mockito.mock(ServiceReferenceHolder.class);
        apiManagerConfiguration = Mockito.mock(APIManagerConfiguration.class);
        PowerMockito.when(ServiceReferenceHolder.getInstance()).thenReturn(serviceReferenceHolder);
        PowerMockito.when(serviceReferenceHolder.getAPIManagerConfiguration()).thenReturn(apiManagerConfiguration);
        PowerMockito.mockStatic(WebsocketUtil.class);
        channelHandlerContext = Mockito.mock(ChannelHandlerContext.class);
        Channel channel = Mockito.mock(Channel.class);
        ChannelId channelId = Mockito.mock(ChannelId.class);
        channelPromise = Mockito.mock(ChannelPromise.class);
        msg = Mockito.mock(WebSocketFrame.class);
        ByteBuf content = Mockito.mock(ByteBuf.class);
        Mockito.when(msg.content()).thenReturn(content);
        websocketHandler = new WebsocketHandler();
        Mockito.when(channelHandlerContext.channel()).thenReturn(channel);
        Mockito.when(channel.id()).thenReturn(channelId);
        Mockito.when(channelId.asLongText()).thenReturn(channelIdString);
        websocketAPI = new API(UUID.randomUUID().toString(), 1, "admin", "WSAPI", "1.0.0", "/wscontext", "Unlimited",
                "WS", false);
        graphQLAPI = new API(UUID.randomUUID().toString(), 2, "admin", "GraphQLAPI", "1.0.0", "graphql", "Unlimited",
                APIConstants.GRAPHQL_API, false);

    }

    /*
     * This method tests write() when msg is not a WebSocketFrame
     * */
    @Test
    public void testWSWrite() throws Exception {

        Object msg = "msg";
        String publisherClass = "publisherClass";
        PowerMockito.mockStatic(DataPublisherUtil.class);
        APIManagerAnalyticsConfiguration apiMngAnalyticsConfig = Mockito.mock(APIManagerAnalyticsConfiguration.class);
        PowerMockito.when(DataPublisherUtil.getApiManagerAnalyticsConfiguration()).thenReturn(apiMngAnalyticsConfig);
        Mockito.when(apiMngAnalyticsConfig.getPublisherClass()).thenReturn(publisherClass);
        WebsocketHandler websocketHandler = new WebsocketHandler();
        websocketHandler.write(channelHandlerContext, msg, channelPromise);
        Assert.assertTrue((InboundMessageContextDataHolder.getInstance().getInboundMessageContextMap()
                .containsKey(channelIdString))); // No error has occurred context exists in data-holder map.

        msg = Mockito.mock(CloseWebSocketFrame.class);
        websocketHandler.write(channelHandlerContext, msg, channelPromise);
        Assert.assertFalse((InboundMessageContextDataHolder.getInstance().getInboundMessageContextMap()
                .containsKey(channelIdString))); // Closing connection. Remove context from data-holder map.
    }

    /*
     * This method tests write() when msg is a WebSocketFrame for WebSocket API.
     * */
    @Test
    public void testWSWriteSuccessResponse() throws Exception {

        InboundMessageContext inboundMessageContext = new InboundMessageContext();
        inboundMessageContext.setElectedAPI(websocketAPI);
        InboundMessageContextDataHolder.getInstance().addInboundMessageContextForConnection(channelIdString,
                inboundMessageContext);
        WebsocketHandler websocketHandler = new WebsocketHandler() {
            @Override protected boolean isAllowed(ChannelHandlerContext ctx, WebSocketFrame msg,
                    InboundMessageContext inboundMessageContext, APIMgtUsageDataPublisher usageDataPublisher) {
                return true;
            }
        };
        WebsocketInboundHandler websocketInboundHandler = Mockito.mock(WebsocketInboundHandler.class);
        PowerMockito.when(WebsocketUtil.doThrottle(channelHandlerContext, msg, null, inboundMessageContext,
                websocketInboundHandler.getUsageDataPublisher())).thenReturn(true);
        websocketHandler.write(channelHandlerContext, msg, channelPromise);
        Assert.assertTrue((InboundMessageContextDataHolder.getInstance().getInboundMessageContextMap()
                .containsKey(channelIdString))); // No error has occurred context exists in data-holder map.
    }

    @Test
    public void testGraphQLWriteResponse() throws Exception {

        InboundMessageContext inboundMessageContext = new InboundMessageContext();
        inboundMessageContext.setElectedAPI(graphQLAPI);
        InboundMessageContextDataHolder.getInstance().addInboundMessageContextForConnection(channelIdString,
                inboundMessageContext);
        msg = new TextWebSocketFrame("{\"id\":\"1\",\"type\":\"start\",\"payload\":{\"variables\":{},"
                + "\"extensions\":{},\"operationName\":null,"
                + "\"query\":\"subscription {\\n  liftStatusChange {\\n    id\\n    name\\n    }\\n}\\n\"}}");

        VerbInfoDTO verbInfoDTO = new VerbInfoDTO();
        verbInfoDTO.setHttpVerb(GraphQLConstants.SubscriptionConstants.HTTP_METHOD_NAME);
        verbInfoDTO.setAuthType("OAUTH");
        GraphQLOperationDTO graphQLOperationDTO = new GraphQLOperationDTO(verbInfoDTO, "liftStatusChange");
        inboundMessageContext.addVerbInfoForGraphQLMsgId("1", graphQLOperationDTO);
        InboundProcessorResponseDTO responseDTO = new InboundProcessorResponseDTO();
        PowerMockito.when(GraphQLProcessor.authenticateGraphQLJWTToken(inboundMessageContext))
                .thenReturn(responseDTO);
        PowerMockito.when(GraphQLProcessor
                .validateScopes(Mockito.anyObject(), Mockito.anyObject(), Mockito.anyObject())).thenReturn(responseDTO);
        PowerMockito.when(GraphQLProcessor.doThrottleForGraphQL(Mockito.anyObject(), Mockito.anyObject(),
                        Mockito.anyObject(), Mockito.anyObject(), Mockito.anyObject(), Mockito.anyObject()))
                .thenReturn(responseDTO);

        // happy path
        websocketHandler.write(channelHandlerContext, msg, channelPromise);
        Assert.assertTrue((InboundMessageContextDataHolder.getInstance().getInboundMessageContextMap()
                .containsKey(channelIdString)));// No error has occurred context exists in data-holder map.

        // close connection error
        responseDTO.setError(true);
        responseDTO.setCloseConnection(true);
        websocketHandler.write(channelHandlerContext, msg, channelPromise);
        Assert.assertFalse(InboundMessageContextDataHolder.getInstance().getInboundMessageContextMap()
                .containsKey(channelIdString));  // Closing connection error has occurred

        // Websocket frame error has occurred
        InboundMessageContextDataHolder.getInstance().addInboundMessageContextForConnection(channelIdString,
                inboundMessageContext);
        responseDTO.setError(true);
        responseDTO.setCloseConnection(false);
        websocketHandler.write(channelHandlerContext, msg, channelPromise);
        Assert.assertTrue((InboundMessageContextDataHolder.getInstance().getInboundMessageContextMap()
                .containsKey(channelIdString)));
    }
}
