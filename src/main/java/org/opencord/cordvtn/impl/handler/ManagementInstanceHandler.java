/*
 * Copyright 2016-present Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opencord.cordvtn.impl.handler;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.packet.Ethernet;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.xosclient.api.VtnService;
import org.opencord.cordvtn.impl.AbstractInstanceHandler;
import org.opencord.cordvtn.api.Instance;
import org.opencord.cordvtn.api.InstanceHandler;
import org.opencord.cordvtn.impl.CordVtnNodeManager;
import org.opencord.cordvtn.impl.CordVtnPipeline;

import java.util.Optional;

import static org.onosproject.xosclient.api.VtnServiceApi.ServiceType.MANAGEMENT;

/**
 * Provides network connectivity for management network connected instances.
 */
@Component(immediate = true)
public class ManagementInstanceHandler extends AbstractInstanceHandler implements InstanceHandler {

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CordVtnPipeline pipeline;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CordVtnNodeManager nodeManager;

    @Activate
    protected void activate() {
        serviceType = Optional.of(MANAGEMENT);
        super.activate();
    }

    @Deactivate
    protected void deactivate() {
        super.deactivate();
    }

    @Override
    public void instanceDetected(Instance instance) {
        VtnService service = getVtnService(instance.serviceId());
        if (service == null) {
            log.warn("Failed to get VtnService for {}", instance);
            return;
        }

        switch (service.networkType()) {
            case MANAGEMENT_LOCAL:
                log.info("LOCAL management instance detected {}", instance);
                localManagementRules(instance, true);
                break;
            case MANAGEMENT_HOSTS:
                log.info("HOSTS management instance detected {}", instance);
                hostsManagementRules(instance, true);
                break;
            default:
                break;
        }
    }

    @Override
    public void instanceRemoved(Instance instance) {
        VtnService service = getVtnService(instance.serviceId());
        if (service == null) {
            log.warn("Failed to get VtnService for {}", instance);
            return;
        }

        switch (service.networkType()) {
            case MANAGEMENT_LOCAL:
                log.info("LOCAL management instance removed {}", instance);
                localManagementRules(instance, false);
                break;
            case MANAGEMENT_HOSTS:
                log.info("HOSTS management instance removed {}", instance);
                hostsManagementRules(instance, false);
                break;
            default:
                break;
        }
    }

    private void localManagementRules(Instance instance, boolean install) {
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPDst(instance.ipAddress().toIpPrefix())
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setEthDst(instance.mac())
                .setOutput(instance.portNumber())
                .build();

        FlowRule flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(CordVtnPipeline.PRIORITY_DEFAULT)
                .forDevice(instance.deviceId())
                .forTable(CordVtnPipeline.TABLE_DST_IP)
                .makePermanent()
                .build();

        pipeline.processFlowRule(install, flowRule);
    }

    private void hostsManagementRules(Instance instance, boolean install) {
        PortNumber hostMgmtPort = nodeManager.hostManagementPort(instance.deviceId());
        if (hostMgmtPort == null) {
            log.warn("Can not find host management port in {}", instance.deviceId());
            return;
        }

        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchInPort(instance.portNumber())
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(hostMgmtPort)
                .build();

        FlowRule flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(CordVtnPipeline.PRIORITY_MANAGEMENT)
                .forDevice(instance.deviceId())
                .forTable(CordVtnPipeline.TABLE_IN_PORT)
                .makePermanent()
                .build();

        pipeline.processFlowRule(install, flowRule);

        selector = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPDst(instance.ipAddress().toIpPrefix())
                .build();

        treatment = DefaultTrafficTreatment.builder()
                .setEthDst(instance.mac())
                .setOutput(instance.portNumber())
                .build();

        flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(CordVtnPipeline.PRIORITY_DEFAULT)
                .forDevice(instance.deviceId())
                .forTable(CordVtnPipeline.TABLE_DST_IP)
                .makePermanent()
                .build();

        pipeline.processFlowRule(install, flowRule);
    }
}
