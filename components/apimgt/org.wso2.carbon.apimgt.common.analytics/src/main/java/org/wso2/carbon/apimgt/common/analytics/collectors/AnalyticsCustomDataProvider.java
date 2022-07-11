package org.wso2.carbon.apimgt.common.analytics.collectors;

import java.util.Map;

/**
 * Data provider interface to extract custom request data.
 */
public interface AnalyticsCustomDataProvider {

    Map<String, Object> getCustomProperties(Object context);

}
