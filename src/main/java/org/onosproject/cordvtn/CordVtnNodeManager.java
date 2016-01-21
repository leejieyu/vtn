/*
 * Copyright 2014-2015 Open Networking Laboratory
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
package org.onosproject.cordvtn;

import com.google.common.collect.Sets;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onlab.util.ItemNotFoundException;
import org.onlab.util.KryoNamespace;
import org.onosproject.cluster.ClusterService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DefaultAnnotations;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.Port;
import org.onosproject.net.behaviour.BridgeConfig;
import org.onosproject.net.behaviour.BridgeName;
import org.onosproject.net.behaviour.ControllerInfo;
import org.onosproject.net.behaviour.DefaultTunnelDescription;
import org.onosproject.net.behaviour.TunnelConfig;
import org.onosproject.net.behaviour.TunnelDescription;
import org.onosproject.net.behaviour.TunnelName;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.config.NetworkConfigService;
import org.onosproject.net.config.basics.SubjectFactories;
import org.onosproject.net.device.DeviceAdminService;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.driver.DriverHandler;
import org.onosproject.net.driver.DriverService;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.group.GroupService;
import org.onosproject.net.host.HostService;
import org.onosproject.ovsdb.controller.OvsdbClientService;
import org.onosproject.ovsdb.controller.OvsdbController;
import org.onosproject.ovsdb.controller.OvsdbNodeId;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.ConsistentMap;
import org.onosproject.store.service.Serializer;
import org.onosproject.store.service.StorageService;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static org.onlab.util.Tools.groupedThreads;
import static org.onosproject.net.Device.Type.SWITCH;
import static org.onosproject.net.behaviour.TunnelDescription.Type.VXLAN;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Reads node information from the network config file and handles the config
 * update events.
 * Only a leader controller performs the node addition or deletion.
 */
@Component(immediate = true)
@Service(value = CordVtnNodeManager.class)
public class CordVtnNodeManager {

    protected final Logger log = getLogger(getClass());

    private static final KryoNamespace.Builder NODE_SERIALIZER = KryoNamespace.newBuilder()
            .register(KryoNamespaces.API)
            .register(CordVtnNode.class)
            .register(NodeState.class);

    private static final String DEFAULT_BRIDGE = "br-int";
    private static final String DEFAULT_TUNNEL = "vxlan";
    private static final String VPORT_PREFIX = "tap";
    private static final String OK = "OK";
    private static final String NO = "NO";

    private static final Map<String, String> DEFAULT_TUNNEL_OPTIONS = new HashMap<String, String>() {
        {
            put("key", "flow");
            put("remote_ip", "flow");
        }
    };
    private static final int DPID_BEGIN = 3;
    private static final int OFPORT = 6653;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkConfigRegistry configRegistry;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkConfigService configService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected StorageService storageService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceAdminService adminService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected OvsdbController controller;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ClusterService clusterService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DriverService driverService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected MastershipService mastershipService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected GroupService groupService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CordVtnService cordVtnService;

    private final ConfigFactory configFactory =
            new ConfigFactory(SubjectFactories.APP_SUBJECT_FACTORY, CordVtnConfig.class, "cordvtn") {
                @Override
                public CordVtnConfig createConfig() {
                    return new CordVtnConfig();
                }
            };

    private final ExecutorService eventExecutor =
            newSingleThreadScheduledExecutor(groupedThreads("onos/cordvtncfg", "event-handler"));

    private final NetworkConfigListener configListener = new InternalConfigListener();
    private final DeviceListener deviceListener = new InternalDeviceListener();

    private final OvsdbHandler ovsdbHandler = new OvsdbHandler();
    private final BridgeHandler bridgeHandler = new BridgeHandler();

    private ConsistentMap<CordVtnNode, NodeState> nodeStore;
    private CordVtnRuleInstaller ruleInstaller;
    private ApplicationId appId;

    private enum NodeState {

        INIT {
            @Override
            public void process(CordVtnNodeManager nodeManager, CordVtnNode node) {
                nodeManager.connectOvsdb(node);
            }
        },
        OVSDB_CONNECTED {
            @Override
            public void process(CordVtnNodeManager nodeManager, CordVtnNode node) {
                if (!nodeManager.getOvsdbConnectionState(node)) {
                    nodeManager.connectOvsdb(node);
                } else {
                    nodeManager.createIntegrationBridge(node);
                }
            }
        },
        BRIDGE_CREATED {
            @Override
            public void process(CordVtnNodeManager nodeManager, CordVtnNode node) {
                if (!nodeManager.getOvsdbConnectionState(node)) {
                    nodeManager.connectOvsdb(node);
                } else {
                    nodeManager.createTunnelInterface(node);
                }
            }
        },
        TUNNEL_INTERFACE_CREATED {
            @Override
            public void process(CordVtnNodeManager nodeManager, CordVtnNode node) {
                if (!nodeManager.getOvsdbConnectionState(node)) {
                    nodeManager.connectOvsdb(node);
                } else {
                    nodeManager.createPhyInterface(node);
                }
            }

        },
        COMPLETE {
            @Override
            public void process(CordVtnNodeManager nodeManager, CordVtnNode node) {
                nodeManager.postInit(node);
            }
        },
        INCOMPLETE {
            @Override
            public void process(CordVtnNodeManager nodeManager, CordVtnNode node) {
            }
        };

        // TODO Add physical port add state

        public abstract void process(CordVtnNodeManager nodeManager, CordVtnNode node);
    }

    @Activate
    protected void active() {
        appId = coreService.getAppId(CordVtnService.CORDVTN_APP_ID);

        nodeStore = storageService.<CordVtnNode, NodeState>consistentMapBuilder()
                .withSerializer(Serializer.using(NODE_SERIALIZER.build()))
                .withName("cordvtn-nodestore")
                .withApplicationId(appId)
                .build();

        ruleInstaller = new CordVtnRuleInstaller(appId, flowRuleService,
                                                 deviceService,
                                                 driverService,
                                                 groupService,
                                                 mastershipService,
                                                 DEFAULT_TUNNEL);

        deviceService.addListener(deviceListener);
        configService.addListener(configListener);
        configRegistry.registerConfigFactory(configFactory);
    }

    @Deactivate
    protected void deactivate() {
        configRegistry.unregisterConfigFactory(configFactory);
        configService.removeListener(configListener);
        deviceService.removeListener(deviceListener);

        eventExecutor.shutdown();
        nodeStore.clear();
    }

    /**
     * Adds a new node to the service.
     *
     * @param node cordvtn node
     */
    public void addNode(CordVtnNode node) {
        checkNotNull(node);

        nodeStore.putIfAbsent(node, checkNodeState(node));
        initNode(node);
    }

    /**
     * Deletes a node from the service.
     *
     * @param node cordvtn node
     */
    public void deleteNode(CordVtnNode node) {
        checkNotNull(node);

        if (getOvsdbConnectionState(node)) {
            disconnectOvsdb(node);
        }

        nodeStore.remove(node);
    }

    /**
     * Initiates node to serve virtual tenant network.
     *
     * @param node cordvtn node
     */
    public void initNode(CordVtnNode node) {
        checkNotNull(node);

        if (!nodeStore.containsKey(node)) {
            log.warn("Node {} does not exist, add node first", node.hostname());
            return;
        }

        NodeState state = checkNodeState(node);
        state.process(this, node);
    }

    /**
     * Returns node initialization state.
     *
     * @param node cordvtn node
     * @return true if initial node setup is completed, otherwise false
     */
    public boolean getNodeInitState(CordVtnNode node) {
        checkNotNull(node);

        NodeState state = getNodeState(node);
        return state != null && state.equals(NodeState.COMPLETE);
    }

    /**
     * Returns detailed node initialization state.
     * Return string includes the following information.
     *
     * Integration bridge created/connected: OK or NO
     * VXLAN interface created: OK or NO
     * Physical interface added: OK or NO
     *
     * @param node cordvtn node
     * @return string including detailed node init state
     */
    public String checkNodeInitState(CordVtnNode node) {
        checkNotNull(node);

        NodeState state = getNodeState(node);
        if (state == null) {
            log.warn("Failed to get init state of {}", node.hostname());
            return null;
        }

        String result = String.format(
                "Integration bridge created/connected : %s (%s)%n" +
                        "VXLAN interface created : %s%n" +
                        "Physical interface added : %s (%s)",
                checkIntegrationBridge(node) ? OK : NO, DEFAULT_BRIDGE,
                checkTunnelInterface(node) ? OK : NO,
                checkPhyInterface(node) ? OK : NO, node.phyPortName());

        return result;
    }

    /**
     * Returns the number of the nodes known to the service.
     *
     * @return number of nodes
     */
    public int getNodeCount() {
        return nodeStore.size();
    }

    /**
     * Returns all nodes known to the service.
     *
     * @return list of nodes
     */
    public List<CordVtnNode> getNodes() {
        List<CordVtnNode> nodes = new ArrayList<>();
        nodes.addAll(nodeStore.keySet());
        return nodes;
    }

    /**
     * Returns cordvtn node associated with a given OVSDB device.
     *
     * @param ovsdbId OVSDB device id
     * @return cordvtn node, null if it fails to find the node
     */
    private CordVtnNode getNodeByOvsdbId(DeviceId ovsdbId) {
        return getNodes().stream()
                .filter(node -> node.ovsdbId().equals(ovsdbId))
                .findFirst().orElse(null);
    }

    /**
     * Returns cordvtn node associated with a given integration bridge.
     *
     * @param bridgeId device id of integration bridge
     * @return cordvtn node, null if it fails to find the node
     */
    private CordVtnNode getNodeByBridgeId(DeviceId bridgeId) {
        return getNodes().stream()
                .filter(node -> node.intBrId().equals(bridgeId))
                .findFirst().orElse(null);
    }

    /**
     * Returns state of a given cordvtn node.
     *
     * @param node cordvtn node
     * @return node state, or null if no such node exists
     */
    private NodeState getNodeState(CordVtnNode node) {
        checkNotNull(node);

        try {
            return nodeStore.get(node).value();
        } catch (NullPointerException e) {
            log.error("Failed to get state of {}", node.hostname());
            return null;
        }
    }

    /**
     * Sets a new state for a given cordvtn node.
     *
     * @param node cordvtn node
     * @param newState new node state
     */
    private void setNodeState(CordVtnNode node, NodeState newState) {
        checkNotNull(node);

        log.debug("Changed {} state: {}", node.hostname(), newState.toString());

        nodeStore.put(node, newState);
        newState.process(this, node);
    }

    /**
     * Checks current state of a given cordvtn node and returns it.
     *
     * @param node cordvtn node
     * @return node state
     */
    private NodeState checkNodeState(CordVtnNode node) {
        checkNotNull(node);

        if (checkIntegrationBridge(node) && checkTunnelInterface(node) &&
                checkPhyInterface(node)) {
            return NodeState.COMPLETE;
        } else if (checkTunnelInterface(node)) {
            return NodeState.TUNNEL_INTERFACE_CREATED;
        } else if (checkIntegrationBridge(node)) {
            return NodeState.BRIDGE_CREATED;
        } else if (getOvsdbConnectionState(node)) {
            return NodeState.OVSDB_CONNECTED;
        } else {
            return NodeState.INIT;
        }
    }

    /**
     * Performs tasks after node initialization.
     * It disconnects unnecessary OVSDB connection and installs initial flow
     * rules on the device.
     *
     * @param node cordvtn node
     */
    private void postInit(CordVtnNode node) {
        disconnectOvsdb(node);

        ruleInstaller.init(node.intBrId(), node.phyPortName(), node.localIp());

        // add existing hosts to the service
        deviceService.getPorts(node.intBrId()).stream()
                .filter(port -> getPortName(port).startsWith(VPORT_PREFIX) &&
                        port.isEnabled())
                .forEach(port -> cordVtnService.addServiceVm(node, getConnectPoint(port)));

        // remove stale hosts from the service
        hostService.getHosts().forEach(host -> {
            Port port = deviceService.getPort(host.location().deviceId(), host.location().port());
            if (port == null) {
                cordVtnService.removeServiceVm(getConnectPoint(host));
            }
        });

        log.info("Finished init {}", node.hostname());
    }

    /**
     * Returns port name.
     *
     * @param port port
     * @return port name
     */
    private String getPortName(Port port) {
        return port.annotations().value("portName");
    }

    /**
     * Returns connection state of OVSDB server for a given node.
     *
     * @param node cordvtn node
     * @return true if it is connected, false otherwise
     */
    private boolean getOvsdbConnectionState(CordVtnNode node) {
        checkNotNull(node);

        OvsdbClientService ovsdbClient = getOvsdbClient(node);
        return deviceService.isAvailable(node.ovsdbId()) &&
                ovsdbClient != null && ovsdbClient.isConnected();
    }

    /**
     * Connects to OVSDB server for a given node.
     *
     * @param node cordvtn node
     */
    private void connectOvsdb(CordVtnNode node) {
        checkNotNull(node);

        if (!nodeStore.containsKey(node)) {
            log.warn("Node {} does not exist", node.hostname());
            return;
        }

        if (!getOvsdbConnectionState(node)) {
            controller.connect(node.ovsdbIp(), node.ovsdbPort());
        }
    }

    /**
     * Disconnects OVSDB server for a given node.
     *
     * @param node cordvtn node
     */
    private void disconnectOvsdb(CordVtnNode node) {
        checkNotNull(node);

        if (!nodeStore.containsKey(node)) {
            log.warn("Node {} does not exist", node.hostname());
            return;
        }

        if (getOvsdbConnectionState(node)) {
            OvsdbClientService ovsdbClient = getOvsdbClient(node);
            ovsdbClient.disconnect();
        }
    }

    /**
     * Returns OVSDB client for a given node.
     *
     * @param node cordvtn node
     * @return OVSDB client, or null if it fails to get OVSDB client
     */
    private OvsdbClientService getOvsdbClient(CordVtnNode node) {
        checkNotNull(node);

        OvsdbClientService ovsdbClient = controller.getOvsdbClient(
                new OvsdbNodeId(node.ovsdbIp(), node.ovsdbPort().toInt()));
        if (ovsdbClient == null) {
            log.trace("Couldn't find OVSDB client for {}", node.hostname());
        }
        return ovsdbClient;
    }

    /**
     * Creates an integration bridge for a given node.
     *
     * @param node cordvtn node
     */
    private void createIntegrationBridge(CordVtnNode node) {
        if (checkIntegrationBridge(node)) {
            return;
        }

        List<ControllerInfo> controllers = new ArrayList<>();
        Sets.newHashSet(clusterService.getNodes()).stream()
                .forEach(controller -> {
                    ControllerInfo ctrlInfo = new ControllerInfo(controller.ip(), OFPORT, "tcp");
                    controllers.add(ctrlInfo);
                });

        String dpid = node.intBrId().toString().substring(DPID_BEGIN);

        try {
            DriverHandler handler = driverService.createHandler(node.ovsdbId());
            BridgeConfig bridgeConfig =  handler.behaviour(BridgeConfig.class);
            bridgeConfig.addBridge(BridgeName.bridgeName(DEFAULT_BRIDGE), dpid, controllers);
        } catch (ItemNotFoundException e) {
            log.warn("Failed to create integration bridge on {}", node.hostname());
        }
    }

    /**
     * Creates tunnel interface to the integration bridge for a given node.
     *
     * @param node cordvtn node
     */
    private void createTunnelInterface(CordVtnNode node) {
        if (checkTunnelInterface(node)) {
            return;
        }

        DefaultAnnotations.Builder optionBuilder = DefaultAnnotations.builder();
        for (String key : DEFAULT_TUNNEL_OPTIONS.keySet()) {
            optionBuilder.set(key, DEFAULT_TUNNEL_OPTIONS.get(key));
        }

        TunnelDescription description = new DefaultTunnelDescription(
                null, null, VXLAN, TunnelName.tunnelName(DEFAULT_TUNNEL),
                optionBuilder.build());

        try {
            DriverHandler handler = driverService.createHandler(node.ovsdbId());
            TunnelConfig tunnelConfig =  handler.behaviour(TunnelConfig.class);
            tunnelConfig.createTunnelInterface(BridgeName.bridgeName(DEFAULT_BRIDGE), description);
        } catch (ItemNotFoundException e) {
            log.warn("Failed to create tunnel interface on {}", node.hostname());
        }
    }

    /**
     * Creates physical interface to a given node.
     *
     * @param node cordvtn node
     */
    private void createPhyInterface(CordVtnNode node) {
        if (checkPhyInterface(node)) {
            return;
        }

        try {
            DriverHandler handler = driverService.createHandler(node.ovsdbId());
            BridgeConfig bridgeConfig =  handler.behaviour(BridgeConfig.class);
            bridgeConfig.addPort(BridgeName.bridgeName(DEFAULT_BRIDGE), node.phyPortName());
        } catch (ItemNotFoundException e) {
            log.warn("Failed to add {} on {}", node.phyPortName(), node.hostname());
        }
    }

    /**
     * Checks if integration bridge exists and available.
     *
     * @param node cordvtn node
     * @return true if the bridge is available, false otherwise
     */
    private boolean checkIntegrationBridge(CordVtnNode node) {
        return (deviceService.getDevice(node.intBrId()) != null
                && deviceService.isAvailable(node.intBrId()));
    }

    /**
     * Checks if tunnel interface exists.
     *
     * @param node cordvtn node
     * @return true if the interface exists, false otherwise
     */
    private boolean checkTunnelInterface(CordVtnNode node) {
        return deviceService.getPorts(node.intBrId())
                    .stream()
                    .filter(p -> getPortName(p).contains(DEFAULT_TUNNEL) &&
                            p.isEnabled())
                    .findAny().isPresent();
    }

    /**
     * Checks if physical interface exists.
     *
     * @param node cordvtn node
     * @return true if the interface exists, false otherwise
     */
    private boolean checkPhyInterface(CordVtnNode node) {
        return deviceService.getPorts(node.intBrId())
                    .stream()
                    .filter(p -> getPortName(p).contains(node.phyPortName()) &&
                            p.isEnabled())
                    .findAny().isPresent();
    }

    /**
     * Returns connect point of a given port.
     *
     * @param port port
     * @return connect point
     */
    private ConnectPoint getConnectPoint(Port port) {
        return new ConnectPoint(port.element().id(), port.number());
    }

    /**
     * Returns connect point of a given host.
     *
     * @param host host
     * @return connect point
     */
    private ConnectPoint getConnectPoint(Host host) {
        return new ConnectPoint(host.location().deviceId(), host.location().port());
    }

    private class OvsdbHandler implements ConnectionHandler<Device> {

        @Override
        public void connected(Device device) {
            CordVtnNode node = getNodeByOvsdbId(device.id());
            if (node != null) {
                setNodeState(node, checkNodeState(node));
            } else {
                log.debug("{} is detected on unregistered node, ignore it.", device.id());
            }
        }

        @Override
        public void disconnected(Device device) {
            if (!deviceService.isAvailable(device.id())) {
                adminService.removeDevice(device.id());
            }
        }
    }

    private class BridgeHandler implements ConnectionHandler<Device> {

        @Override
        public void connected(Device device) {
            CordVtnNode node = getNodeByBridgeId(device.id());
            if (node != null) {
                setNodeState(node, checkNodeState(node));
            } else {
                log.debug("{} is detected on unregistered node, ignore it.", device.id());
            }
        }

        @Override
        public void disconnected(Device device) {
            CordVtnNode node = getNodeByBridgeId(device.id());
            if (node != null) {
                log.debug("Integration Bridge is disconnected from {}", node.hostname());
                setNodeState(node, NodeState.INCOMPLETE);
            }
        }

        /**
         * Handles port added situation.
         * If the added port is tunnel or physical port, proceed remaining node
         * initialization. Otherwise, do nothing.
         *
         * @param port port
         */
        public void portAdded(Port port) {
            CordVtnNode node = getNodeByBridgeId((DeviceId) port.element().id());
            String portName = getPortName(port);

            if (node == null) {
                log.debug("{} is added to unregistered node, ignore it.", portName);
                return;
            }

            log.debug("Port {} is added to {}", portName, node.hostname());

            if (portName.startsWith(VPORT_PREFIX)) {
                if (getNodeInitState(node)) {
                    cordVtnService.addServiceVm(node, getConnectPoint(port));
                } else {
                    log.debug("VM is detected on incomplete node, ignore it.", portName);
                }
            } else if (portName.contains(DEFAULT_TUNNEL) || portName.equals(node.phyPortName())) {
                setNodeState(node, checkNodeState(node));
            }
        }

        /**
         * Handles port removed situation.
         * If the removed port is tunnel or physical port, proceed remaining node
         * initialization.Others, do nothing.
         *
         * @param port port
         */
        public void portRemoved(Port port) {
            CordVtnNode node = getNodeByBridgeId((DeviceId) port.element().id());
            String portName = getPortName(port);

            if (node == null) {
                return;
            }

            log.debug("Port {} is removed from {}", portName, node.hostname());

            if (portName.startsWith(VPORT_PREFIX)) {
                if (getNodeInitState(node)) {
                    cordVtnService.removeServiceVm(getConnectPoint(port));
                } else {
                    log.debug("VM is vanished from incomplete node, ignore it.", portName);
                }
            } else if (portName.contains(DEFAULT_TUNNEL) || portName.equals(node.phyPortName())) {
                setNodeState(node, NodeState.INCOMPLETE);
            }
        }
    }

    private class InternalDeviceListener implements DeviceListener {

        @Override
        public void event(DeviceEvent event) {

            Device device = event.subject();
            ConnectionHandler<Device> handler =
                    (device.type().equals(SWITCH) ? bridgeHandler : ovsdbHandler);

            switch (event.type()) {
                case PORT_ADDED:
                    eventExecutor.submit(() -> bridgeHandler.portAdded(event.port()));
                    break;
                case PORT_UPDATED:
                    if (!event.port().isEnabled()) {
                        eventExecutor.submit(() -> bridgeHandler.portRemoved(event.port()));
                    }
                    break;
                case DEVICE_ADDED:
                case DEVICE_AVAILABILITY_CHANGED:
                    if (deviceService.isAvailable(device.id())) {
                        eventExecutor.submit(() -> handler.connected(device));
                    } else {
                        eventExecutor.submit(() -> handler.disconnected(device));
                    }
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Reads node configuration from config file.
     */
    private void readConfiguration() {
        CordVtnConfig config = configRegistry.getConfig(appId, CordVtnConfig.class);

        if (config == null) {
            log.warn("No configuration found");
            return;
        }

        config.cordVtnNodes().forEach(node -> {
            CordVtnNode cordVtnNode = new CordVtnNode(
                    node.hostname(),
                    node.ovsdbIp(),
                    node.ovsdbPort(),
                    node.bridgeId(),
                    node.phyPortName(),
                    node.localIp());

            addNode(cordVtnNode);
        });
    }

    private class InternalConfigListener implements NetworkConfigListener {

        @Override
        public void event(NetworkConfigEvent event) {
            if (!event.configClass().equals(CordVtnConfig.class)) {
                return;
            }

            switch (event.type()) {
                case CONFIG_ADDED:
                    log.info("Network configuration added");
                    eventExecutor.execute(CordVtnNodeManager.this::readConfiguration);
                    break;
                case CONFIG_UPDATED:
                    log.info("Network configuration updated");
                    eventExecutor.execute(CordVtnNodeManager.this::readConfiguration);
                    break;
                default:
                    break;
            }
        }
    }
}
