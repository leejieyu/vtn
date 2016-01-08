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

import java.util.List;

/**
 * Service for provisioning overlay virtual networks on compute nodes.
 */
public interface CordVtnService {

    String CORDVTN_APP_ID = "org.onosproject.cordvtn";
    /**
     * Adds a new node to the service.
     *
     * @param node cordvtn node
     */
    void addNode(CordVtnNode node);

    /**
     * Deletes a node from the service.
     *
     * @param node cordvtn node
     */
    void deleteNode(CordVtnNode node);

    /**
     * Initiates node to serve virtual tenant network.
     *
     * @param node cordvtn node
     */
    void initNode(CordVtnNode node);

    /**
     * Returns the number of the nodes known to the service.
     *
     * @return number of nodes
     */
    int getNodeCount();

    /**
     * Returns node initialization state.
     *
     * @param node cordvtn node
     * @return true if initial node setup is completed, otherwise false
     */
    boolean getNodeInitState(CordVtnNode node);

    /**
     * Returns all nodes known to the service.
     *
     * @return list of nodes
     */
    List<CordVtnNode> getNodes();

    /**
     * Creates dependencies for a given tenant service.
     *
     * @param tServiceId id of the service which has a dependency
     * @param pServiceId id of the service which provide dependency
     */
    void createServiceDependency(CordServiceId tServiceId, CordServiceId pServiceId);

    /**
     * Removes all dependencies from a given tenant service.
     *
     * @param tServiceId id of the service which has a dependency
     * @param pServiceId id of the service which provide dependency
     */
    void removeServiceDependency(CordServiceId tServiceId, CordServiceId pServiceId);
}
