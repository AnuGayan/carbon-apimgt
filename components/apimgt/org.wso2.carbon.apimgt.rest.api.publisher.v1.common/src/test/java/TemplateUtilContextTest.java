/*
*  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
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

import org.junit.Assert;
import org.junit.Test;
import org.wso2.carbon.apimgt.api.model.API;
import org.wso2.carbon.apimgt.api.model.APIIdentifier;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.rest.api.publisher.v1.common.template.ConfigContext;
import org.wso2.carbon.apimgt.rest.api.publisher.v1.common.template.TemplateUtilContext;

import java.util.HashMap;
import java.util.Map;

public class TemplateUtilContextTest {

    @Test
    public void testTemplateUtilContext() throws Exception {
        API api = new API(new APIIdentifier("admin", "TestAPI", "1.0.0"));
        api.setStatus(APIConstants.CREATED);
        api.setContextTemplate("/");
        ConfigContext configcontext = new APIConfigContextWrapper(api);
        TemplateUtilContext templateUtilContext = new TemplateUtilContext(configcontext);
        String xmlSampleText = "<data>TemplateUtilContextTest Class</data>";
        String xmlEscapedText = "&lt;data&gt;TemplateUtilContextTest Class&lt;/data&gt;";
        String result = templateUtilContext.escapeXml(xmlSampleText);
        Assert.assertTrue("Failed to escape XML tags in the provided string : " + xmlSampleText,
                xmlEscapedText.equalsIgnoreCase(result));
        Assert.assertNotNull(templateUtilContext.getContext().get("util"));
    }

    @Test
    public void testJsonStringToMap() {
        API api = new API(new APIIdentifier("admin", "TestAPI", "1.0.0"));
        api.setStatus(APIConstants.CREATED);
        api.setContextTemplate("/");
        ConfigContext configcontext = new APIConfigContextWrapper(api);
        TemplateUtilContext templateUtilContext = new TemplateUtilContext(configcontext);

        // Define the JSON string
        String jsonString = "{\"key1\":\"value1\",\"key2\":\"value2\"}";

        // Define the expected map
        Map<String, Object> expectedMap = new HashMap<>();
        expectedMap.put("key1", "value1");
        expectedMap.put("key2", "value2");

        // Call the method
        Map<String, Object> resultMap = templateUtilContext.jsonStringToMap(jsonString);

        // Verify the result
        Assert.assertNotNull("Result map should not be null", resultMap);
        Assert.assertEquals("Map does not match expected value", expectedMap, resultMap);
    }
}
