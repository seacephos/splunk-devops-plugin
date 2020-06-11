package com.splunk.splunkjenkins.utils;

import com.splunk.splunkjenkins.SplunkJenkinsInstallation;
import org.apache.commons.beanutils.BeanUtils;

import java.util.Map;

public class RemoteUtils {

    public static void initSplunkConfigOnAgent(Map eventCollectorProperty) {
        // Init SplunkJenkins global config in slave, can not reference Jenkins.getInstance(), Xtream
        SplunkJenkinsInstallation config = new SplunkJenkinsInstallation(false);
        try {
            BeanUtils.populate(config, eventCollectorProperty);
            config.setEnabled(true);
            initSplunkConfigOnAgent(config);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void initSplunkConfigOnAgent(SplunkJenkinsInstallation instance) {
        SplunkJenkinsInstallation.initOnAgent(instance);
        // only use one thread on agent
        SplunkLogService.getInstance().MAX_WORKER_COUNT = 1;
    }
}
