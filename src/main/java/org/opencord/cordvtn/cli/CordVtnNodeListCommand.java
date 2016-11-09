/*
 * Copyright 2015-present Open Networking Laboratory
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
package org.opencord.cordvtn.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;
import org.opencord.cordvtn.impl.CordVtnNodeManager;
import org.opencord.cordvtn.api.node.CordVtnNode;

import java.util.Collections;
import java.util.List;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;

/**
 * Lists all nodes registered to the service.
 */
@Command(scope = "onos", name = "cordvtn-nodes",
        description = "Lists all nodes registered in CORD VTN service")
public class CordVtnNodeListCommand extends AbstractShellCommand {

    private static final String COMPLETE = "COMPLETE";
    private static final String INCOMPLETE = "INCOMPLETE";
    private static final String FORMAT = "%-30s%-20s%-20s%-15s%-24s%s";

    @Override
    protected void execute() {
        CordVtnNodeManager nodeManager = AbstractShellCommand.get(CordVtnNodeManager.class);
        List<CordVtnNode> nodes = nodeManager.getNodes();
        Collections.sort(nodes, CordVtnNode.CORDVTN_NODE_COMPARATOR);

        if (outputJson()) {
            try {
                print("%s", mapper().writeValueAsString(json(nodeManager, nodes)));
            } catch (JsonProcessingException e) {
                print("Failed to list networks in JSON format");
            }
        } else {
            print(FORMAT, "Hostname", "Management IP", "Data IP", "Data Iface",
                  "Br-int", "State");

            for (CordVtnNode node : nodes) {
                print(FORMAT, node.hostname(),
                      node.hostMgmtIp().cidr(),
                      node.dataIp().cidr(),
                      node.dataIface(),
                      node.integrationBridgeId().toString(),
                      getState(nodeManager, node));
            }
            print("Total %s nodes", nodeManager.getNodeCount());
        }
    }

    private JsonNode json(CordVtnNodeManager nodeManager, List<CordVtnNode> nodes) {
        ArrayNode result = mapper().enable(INDENT_OUTPUT).createArrayNode();
        for (CordVtnNode node : nodes) {
            result.add(mapper().createObjectNode()
                               .put("hostname", node.hostname())
                               .put("managementIp", node.hostMgmtIp().cidr())
                               .put("dataIp", node.dataIp().cidr())
                               .put("dataInterface", node.dataIface())
                               .put("bridgeId", node.integrationBridgeId().toString())
                               .put("state", getState(nodeManager, node)));
        }
        return result;
    }

    private String getState(CordVtnNodeManager nodeManager, CordVtnNode node) {
        return nodeManager.isNodeInitComplete(node) ? COMPLETE : INCOMPLETE;
    }
}
