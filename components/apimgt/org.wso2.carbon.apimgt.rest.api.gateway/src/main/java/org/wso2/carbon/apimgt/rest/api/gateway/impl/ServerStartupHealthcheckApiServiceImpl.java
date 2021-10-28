package org.wso2.carbon.apimgt.rest.api.gateway.impl;

import org.wso2.carbon.apimgt.gateway.utils.GatewayUtils;
import org.wso2.carbon.apimgt.rest.api.gateway.*;
import org.wso2.carbon.apimgt.rest.api.gateway.dto.*;

import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.MessageContext;


import java.util.List;

import java.io.InputStream;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;


public class ServerStartupHealthcheckApiServiceImpl implements ServerStartupHealthcheckApiService {

    public Response serverStartupHealthcheckGet(MessageContext messageContext) {
        boolean isAllApisDeployed = GatewayUtils.isAllApisDeployed();
        if (isAllApisDeployed) {
            return Response.status(Response.Status.OK).build();
        }
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
}
