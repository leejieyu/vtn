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
package org.opencord.cordvtn.api;

import org.openstack4j.model.network.Network;
import org.openstack4j.model.network.Port;
import org.openstack4j.model.network.Subnet;

/**
 * Service for administering the inventory of virtual network and service network.
 */
public interface CordVtnAdminService extends CordVtnService {

    /**
     * Creates vtn port with given service port information.
     *
     * @param servicePort the new service port
     */
    void createVtnPort(ServicePort servicePort);

    /**
     * Updates vtn port with given service port information.
     *
     * @param servicePort the updated service port
     */
    void updateVtnPort(ServicePort servicePort);

    /**
     * Removes vtn port with given port id.
     *
     * @param portId port id
     */
    void removeVtnPort(PortId portId);

    /**
     * Creates vtn network with given service network information.
     *
     * @param serviceNet the new service network
     */
    void createVtnNetwork(ServiceNetwork serviceNet);

    /**
     * Updates the vtn network with given service network information.
     *
     * @param serviceNet the updated service network
     */
    void updateVtnNetwork(ServiceNetwork serviceNet);

    /**
     * Removes the vtn network.
     *
     * @param netId network id
     */
    void removeVtnNetwork(NetworkId netId);

    /**
     * Creates a port.
     *
     * @param port port
     */
    void createPort(Port port);

    /**
     * Updates the port.
     *
     * @param port the updated port
     */
    void updatePort(Port port);

    /**
     * Removes the port with the given port id.
     *
     * @param portId port id
     */
    void removePort(PortId portId);

    /**
     * Creates a network.
     *
     * @param network network
     */
    void createNetwork(Network network);

    /**
     * Updates the network.
     *
     * @param network the updated network
     */
    void updateNetwork(Network network);

    /**
     * Removes the network with the given network id.
     *
     * @param netId network id
     */
    void removeNetwork(NetworkId netId);

    /**
     * Creates a subnet.
     *
     * @param subnet subnet id
     */
    void createSubnet(Subnet subnet);

    /**
     * Updates the subnet.
     *
     * @param subnet the updated subnet
     */
    void updateSubnet(Subnet subnet);

    /**
     * Removes the subnet with the given subnet id.
     *
     * @param subnetId subnet id
     */
    void removeSubnet(SubnetId subnetId);
}
