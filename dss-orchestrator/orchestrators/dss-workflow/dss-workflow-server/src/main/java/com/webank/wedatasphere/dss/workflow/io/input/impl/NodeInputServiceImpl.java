/*
 * Copyright 2019 WeBank
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.webank.wedatasphere.dss.workflow.io.input.impl;


import com.webank.wedatasphere.dss.common.entity.Resource;
import com.webank.wedatasphere.dss.contextservice.service.ContextService;
import com.webank.wedatasphere.dss.contextservice.service.impl.ContextServiceImpl;
import com.webank.wedatasphere.dss.standard.app.sso.Workspace;
import com.webank.wedatasphere.dss.workflow.common.entity.DSSFlow;
import com.webank.wedatasphere.dss.workflow.common.parser.NodeParser;
import com.webank.wedatasphere.dss.workflow.entity.CommonAppConnNode;
import com.webank.wedatasphere.dss.workflow.io.input.NodeInputService;
import com.webank.wedatasphere.dss.workflow.service.BMLService;
import com.webank.wedatasphere.dss.workflow.service.WorkflowNodeService;
import org.apache.linkis.server.BDPJettyServerHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Service
public class NodeInputServiceImpl implements NodeInputService {
    @Autowired
    private BMLService bmlService;

    @Autowired
    private NodeParser nodeParser;

    @Autowired
    private WorkflowNodeService nodeService;

    private static ContextService contextService = ContextServiceImpl.getInstance();

    private final Logger logger = LoggerFactory.getLogger(this.getClass());


    @Override
    public String uploadResourceToBml(String userName, String nodeJson, String inputResourcePath, String projectName) throws IOException {
        List<Resource> resources = nodeParser.getNodeResource(nodeJson);
        if (resources != null && resources.size() > 0) {
            resources.forEach(resource -> {
                if (resource.getVersion() != null && resource.getFileName() != null && resource.getResourceId() != null) {
                    InputStream resourceInputStream = readResource(userName, resource, inputResourcePath);
                    Map<String, Object> bmlReturnMap = bmlService.upload(userName,
                            resourceInputStream, UUID.randomUUID().toString() + ".json", projectName);
                    resource.setResourceId(bmlReturnMap.get("resourceId").toString());
                    resource.setVersion(bmlReturnMap.get("version").toString());
                } else {
                    logger.warn("Illegal resource information");
                    logger.warn("username:{},fileName:{},version:{},resourceId:{}", userName, resource.getFileName(), resource.getVersion(), resource.getResourceId());
                }
            });
        }
        return nodeParser.updateNodeResource(nodeJson, resources);
    }

    private InputStream readResource(String userName, Resource resource, String flowResourcePath) {
        String readPath = flowResourcePath + resource.getResourceId() + "_" + resource.getVersion() + ".re";
        return bmlService.readLocalResourceFile(userName, readPath);
    }

    @Override
    public String uploadAppConnResource(String userName, String projectName, DSSFlow dssFlow, String nodeJson, String flowContextId, String appConnResourcePath, Workspace workspace, String orcVersion) throws IOException {
        Map<String, Object> nodeJsonMap = BDPJettyServerHelper.jacksonJson().readValue(nodeJson, Map.class);
        String nodeType = nodeJsonMap.get("jobType").toString();


        String nodeId = nodeJsonMap.get("id").toString();

        Map<String, Object> nodeContent = (LinkedHashMap<String, Object>) nodeJsonMap.get("jobContent");
        CommonAppConnNode appConnNode = new CommonAppConnNode();
        appConnNode.setId(nodeId);
        appConnNode.setNodeType(nodeType);
        appConnNode.setJobContent(nodeContent);
        appConnNode.setFlowId(dssFlow.getId());
        appConnNode.setProjectId(dssFlow.getProjectID());

        Map<String, Object> nodeExportContent = null;

        if (nodeService != null) {
            logger.info("appConn NodeService is exist");
            String nodeResourcePath = appConnResourcePath + File.separator + nodeId + ".appconnre";
            File file = new File(nodeResourcePath);
            if (file.exists()) {
                InputStream resourceInputStream = bmlService.readLocalResourceFile(userName, nodeResourcePath);
                Map<String, Object> bmlReturnMap = bmlService.upload(userName, resourceInputStream, UUID.randomUUID().toString() + ".json",
                        projectName);
                try {
                    nodeExportContent = nodeService.importNode(userName, appConnNode, bmlReturnMap, workspace, orcVersion);
                } catch (Exception e) {
                    logger.error("failed to import node ", e);
                }
                if (nodeExportContent != null) {
                    if (nodeExportContent.get("project_id") != null) {
                        Long newProjectId = Long.parseLong(nodeExportContent.get("project_id").toString());
                        logger.warn(String.format("new appConn node add into dss,dssProjectId: %s,newProjectId: %s", appConnNode.getProjectId(), newProjectId));
                        nodeExportContent.remove("project_id");
                    }
                    nodeJsonMap.replace("jobContent", nodeExportContent);
                    appConnNode.setJobContent(nodeExportContent);
                    return BDPJettyServerHelper.jacksonJson().writeValueAsString(nodeJsonMap);
                }
            }else{
                logger.error("appConn node resource file does not exists."+nodeId);
            }
        }


        return nodeJson;
    }

    @Override
    public String updateNodeSubflowID(String nodeJson, long subflowID) throws IOException {

        return nodeParser.updateSubFlowID(nodeJson, subflowID);
    }

}