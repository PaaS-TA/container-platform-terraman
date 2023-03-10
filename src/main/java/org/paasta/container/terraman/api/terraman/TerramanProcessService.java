package org.paasta.container.terraman.api.terraman;

import org.apache.commons.lang3.StringUtils;
import org.paasta.container.terraman.api.common.PropertyService;
import org.paasta.container.terraman.api.common.VaultService;
import org.paasta.container.terraman.api.common.constants.Constants;
import org.paasta.container.terraman.api.common.constants.TerramanConstant;
import org.paasta.container.terraman.api.common.model.ClusterInfo;
import org.paasta.container.terraman.api.common.model.ClusterModel;
import org.paasta.container.terraman.api.common.model.InstanceModel;
import org.paasta.container.terraman.api.common.model.TerramanCommandModel;
import org.paasta.container.terraman.api.common.service.*;
import org.paasta.container.terraman.api.common.util.CommonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TerramanProcessService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TerramanProcessService.class);

    private final int errorResult = -1;
    private final VaultService vaultService;
    private final ClusterLogService clusterLogService;
    private final CommandService commandService;
    private final InstanceService instanceService;
    private final ClusterService clusterService;
    private final PropertyService propertyService;
    private final TfFileService tfFileService;


    @Autowired
    public TerramanProcessService(
            VaultService vaultService
            , ClusterLogService clusterLogService
            , CommandService commandService
            , InstanceService instanceService
            , ClusterService clusterService
            , PropertyService propertyService
            , TfFileService tfFileService
    ) {
        this.vaultService = vaultService;
        this.clusterLogService = clusterLogService;
        this.commandService = commandService;
        this.instanceService = instanceService;
        this.clusterService = clusterService;
        this.propertyService = propertyService;
        this.tfFileService = tfFileService;
    }

    public int terramanProcessSet(int mpSeq, String clusterId, String hostDir) {
        /**************************************************************************************************************************************
         * 0. terraman process setting
         * ***********************************************************************************************************************************/
        TerramanCommandModel terramanCommandModel = new TerramanCommandModel();
        String cResult = "";

        // cluster log ??????
        clusterLogService.deleteClusterLogByClusterId(clusterId);
//        try {
//
//        } catch (Exception e) {
//            LOGGER.error("cluster log ????????? ?????????????????????.");
//            clusterService.updateCluster(clusterId, TerramanConstant.CLUSTER_FAIL_STATUS);
//            return errorResult;
//        }

        // ?????? ???????????? ???????????? ??????
        terramanCommandModel.setCommand("1");
        terramanCommandModel.setDir(hostDir);
        terramanCommandModel.setUserName(TerramanConstant.DEFAULT_USER_NAME);
        terramanCommandModel.setClusterId(clusterId);
        cResult = commandService.execCommandOutput(terramanCommandModel);
        if(StringUtils.equals(Constants.RESULT_STATUS_FAIL, cResult)) {
            clusterService.updateCluster(clusterId, TerramanConstant.CLUSTER_FAIL_STATUS);
            clusterLogService.saveClusterLog(clusterId, mpSeq, TerramanConstant.TERRAFORM_CREATE_CLUSTER_DIRECTORY_ERROR);
            return errorResult;
        }
        return mpSeq;
    }

    public int terramanProcessStart(int mpSeq, String clusterId, String provider, String processGb, String host, String idRsa) {
        /**************************************************************************************************************************************
         * 1. terraman process start
         * ***********************************************************************************************************************************/
        LOGGER.info("execute terraform!!");
        this.dirCheck("1. current directory :: {}", TerramanConstant.MOVE_DIR_CLUSTER(clusterId), clusterId, host,idRsa);

        // log ??????

        clusterLogService.saveClusterLog(clusterId, mpSeq, TerramanConstant.TERRAFORM_START_LOG(provider));
        mpSeq += 1;

        // log ??????
        clusterLogService.saveClusterLog(clusterId, mpSeq, TerramanConstant.TERRAFORM_IAC_LOG);
        mpSeq += 1;

        return mpSeq;
    }

    public int terramanProcessSetTfFile(int mpSeq, String clusterId, String processGb, String host, String idRsa, String provider, int seq) {
        /**************************************************************************************************************************************
         * 2. IaaS??? ?????? provider.tf ?????? ?????? (Vault, Database)
         *
         * - command
         * INSTANCE_COPY_COMMAND = "kubectl cp -n cp-portal cp-portal-api-deployment-6b94d6945d-jzvfh:tmp/test/ /home/ubuntu/tmp/instance.tf"
         *
         * - log
         * TERRAFORM_TF_ERROR_LOG = "Provider file creation error, cluster creation aborted. errCode ::";
         * TERRAFORM_TF_LOG = "Tf file for instance configuration is complete.";
         * ************************************************************************************************************************************/
        this.dirCheck("2. current directory :: {}", TerramanConstant.MOVE_DIR_CLUSTER(clusterId), clusterId, host,idRsa);
        TerramanCommandModel terramanCommandModel = new TerramanCommandModel();
        String cResult = "";
        String fResult = Constants.RESULT_STATUS_FAIL;


        terramanCommandModel.setCommand("3");
        terramanCommandModel.setHost(host);
        terramanCommandModel.setIdRsa(idRsa);
        terramanCommandModel.setUserName(TerramanConstant.DEFAULT_USER_NAME);
        terramanCommandModel.setClusterId(clusterId);
        cResult = commandService.execCommandOutput(terramanCommandModel);
        try {
            fResult = tfFileService.createProviderFile(clusterId, provider, seq, cResult.trim(), host, idRsa, processGb);
        } catch (Exception e) {
            LOGGER.error("Exception createProviderFile");
            clusterLogService.saveClusterLog(clusterId, mpSeq, TerramanConstant.TERRAFORM_TF_ERROR_LOG + fResult);
            clusterService.updateCluster(clusterId, TerramanConstant.CLUSTER_FAIL_STATUS);
            return errorResult;
        }
        if(StringUtils.equals(fResult, Constants.RESULT_STATUS_FAIL)) {
            // log ??????
            clusterLogService.saveClusterLog(clusterId, mpSeq, TerramanConstant.TERRAFORM_TF_ERROR_LOG + fResult);
            mpSeq += 1;
            clusterService.updateCluster(clusterId, TerramanConstant.CLUSTER_FAIL_STATUS);
            return errorResult;
        }

        // log ??????
        clusterLogService.saveClusterLog(clusterId, mpSeq, TerramanConstant.TERRAFORM_TF_LOG);
        mpSeq += 1;
        return mpSeq;
    }

    public int terramanProcessInit(int mpSeq, String clusterId, String processGb, String host, String idRsa) {
        /**************************************************************************************************************************************
         * 3. terraform init ??????
         *
         * - command
         * TERRAFORM_INIT_COMMAND = "terraform init";
         *
         * - log
         * TERRAFORM_INIT_LOG = "Terraform initialization is complete.";
         * ************************************************************************************************************************************/
        this.dirCheck("3. current directory :: {}", TerramanConstant.MOVE_DIR_CLUSTER(clusterId), clusterId, host,idRsa);
        TerramanCommandModel terramanCommandModel = new TerramanCommandModel();
        String cResult = "";


        // command line ??????
        terramanCommandModel.setCommand("4");
        terramanCommandModel.setDir(TerramanConstant.MOVE_DIR_CLUSTER(clusterId));
        terramanCommandModel.setHost(host);
        terramanCommandModel.setIdRsa(idRsa);
        terramanCommandModel.setUserName(TerramanConstant.DEFAULT_USER_NAME);
        terramanCommandModel.setClusterId(clusterId);
        cResult = commandService.execCommandOutput(terramanCommandModel);
        if(StringUtils.equals(cResult, Constants.RESULT_STATUS_FAIL)) {
            LOGGER.error("terraform init ??????????????????.");
            clusterLogService.saveClusterLog(clusterId, mpSeq, TerramanConstant.TERRAFORM_INIT_FAIL_LOG);
            clusterService.updateCluster(clusterId, TerramanConstant.CLUSTER_FAIL_STATUS);
            return errorResult;
        }

        LOGGER.info("Processing terraform init.");

        // log ??????
        clusterLogService.saveClusterLog(clusterId, mpSeq, TerramanConstant.TERRAFORM_INIT_LOG);
        mpSeq += 1;
        return mpSeq;
    }

    public int terramanProcessPlan(int mpSeq, String clusterId, String processGb, String host, String idRsa) {
        /**************************************************************************************************************************************
         * 4. terraform plan ??????
         *
         * - command
         * TERRAFORM_PLAN_COMMAND = "terraform plan -var vpc_name=paasta-cp-vpc -var route_table_name=paasta-cp-routing-public";
         *
         * - log
         * TERRAFORM_PLAN_LOG = "The system has confirmed that there are no problems with the terraform plan.";
         * ************************************************************************************************************************************/
        this.dirCheck("4. current directory :: {}", TerramanConstant.MOVE_DIR_CLUSTER(clusterId), clusterId, host,idRsa);
        TerramanCommandModel terramanCommandModel = new TerramanCommandModel();
        String cResult = "";


        // command line ??????
        terramanCommandModel.setCommand("5");
        terramanCommandModel.setDir(TerramanConstant.MOVE_DIR_CLUSTER(clusterId));
        terramanCommandModel.setHost(host);
        terramanCommandModel.setIdRsa(idRsa);
        terramanCommandModel.setUserName(TerramanConstant.DEFAULT_USER_NAME);
        terramanCommandModel.setClusterId(clusterId);
        cResult = commandService.execCommandOutput(terramanCommandModel);
        if(StringUtils.equals(Constants.RESULT_STATUS_FAIL, cResult)) {
            LOGGER.error("terraform plan??? ??????????????????.");
            clusterLogService.saveClusterLog(clusterId, mpSeq, TerramanConstant.TERRAFORM_PLAN_FAIL_LOG);
            clusterService.updateCluster(clusterId, TerramanConstant.CLUSTER_FAIL_STATUS);
            return errorResult;
        }
        LOGGER.info("Processing terraform plan.");

        // log ??????
        clusterLogService.saveClusterLog(clusterId, mpSeq, TerramanConstant.TERRAFORM_PLAN_LOG);
        mpSeq += 1;
        return mpSeq;
    }

    public int terramanProcessApply(int mpSeq, String clusterId, String processGb, String host, String idRsa) {
        /**************************************************************************************************************************************
         * 5. terraform apply ??????
         *
         * - command
         * TERRAFORM_APPLY_COMMAND = "terraform apply -auto-approve -var vpc_name=paasta-cp-vpc -var route_table_name=paasta-cp-routing-public";
         *
         * - log
         * TERRAFORM_APPLY_LOG = "The system has finished configuring the instances for cluster creation.";
         * ************************************************************************************************************************************/
        this.dirCheck("5. current directory :: {}", TerramanConstant.MOVE_DIR_CLUSTER(clusterId), clusterId, host,idRsa);
        TerramanCommandModel terramanCommandModel = new TerramanCommandModel();
        String cResult = "";


        // command line ??????
        terramanCommandModel.setCommand("6");
        terramanCommandModel.setDir(TerramanConstant.MOVE_DIR_CLUSTER(clusterId));
        terramanCommandModel.setHost(host);
        terramanCommandModel.setIdRsa(idRsa);
        terramanCommandModel.setUserName(TerramanConstant.DEFAULT_USER_NAME);
        terramanCommandModel.setClusterId(clusterId);
        cResult = commandService.execCommandOutput(terramanCommandModel);
        if(StringUtils.equals(Constants.RESULT_STATUS_FAIL, cResult)) {
            clusterLogService.saveClusterLog(clusterId, mpSeq, TerramanConstant.TERRAFORM_APPLY_FAIL_LOG);
            clusterService.updateCluster(clusterId, TerramanConstant.CLUSTER_FAIL_STATUS);
            return errorResult;
        }
        LOGGER.info("Instance ????????? ?????????????????????.");

        // log ??????
        clusterLogService.saveClusterLog(clusterId, mpSeq, TerramanConstant.TERRAFORM_APPLY_LOG);
        mpSeq += 1;
        return mpSeq;
    }

    public int terramanProcessGetInstanceIp(int mpSeq, String clusterId, String processGb, String host, String idRsa, String provider, String clusterName) {
        /**************************************************************************************************************************************
         * 6. Infra ?????? ??? ????????? Instance IP ????????????
         *
         * - log
         * TERRAFORM_SUCCESS_LOG = "It succeeded in loading the configuration information of the newly created instance.";
         * ************************************************************************************************************************************/
        this.dirCheck("6. current directory :: {}", TerramanConstant.MOVE_DIR_CLUSTER(clusterId), clusterId, host,idRsa);
        TerramanCommandModel terramanCommandModel = new TerramanCommandModel();
        String cResult = "";
        boolean connFlag = false;


        InstanceModel instanceInfo = instanceService.getInstance(clusterId, provider, host, idRsa, processGb);
        if(instanceInfo == null) {
            clusterLogService.saveClusterLog(clusterId, mpSeq, TerramanConstant.TERRAFORM_NOT_EXISTS_INSTANCE_ERROR);
            clusterService.updateCluster(clusterId, TerramanConstant.CLUSTER_FAIL_STATUS);
            return errorResult;
        }

        if(StringUtils.isBlank(instanceInfo.getPrivateIp())) {
            clusterLogService.saveClusterLog(clusterId, mpSeq, TerramanConstant.TERRAFORM_NOT_EXISTS_PRIVATE_IP_ERROR);
            clusterService.updateCluster(clusterId, TerramanConstant.CLUSTER_FAIL_STATUS);
            return errorResult;
        }

        try {
            terramanCommandModel.setCommand("2");
            terramanCommandModel.setHost(instanceInfo.getPublicIp());
            terramanCommandModel.setIdRsa(TerramanConstant.CLUSTER_PRIVATE_KEY(clusterName));
            terramanCommandModel.setUserName(TerramanConstant.DEFAULT_USER_NAME);
            terramanCommandModel.setClusterId(clusterId);

            for(int i = 0; i<100; i++) {
                Thread.sleep(10000);

                cResult = commandService.execCommandOutput(terramanCommandModel);
                LOGGER.info("ssh connection checking... :: {}", CommonUtils.loggerReplace(cResult));
                if(StringUtils.isNotBlank(cResult)) {
                    if(!StringUtils.equals(cResult, Constants.RESULT_STATUS_FAIL)) {
                        break;
                    } else if ( StringUtils.contains(cResult, Constants.RESULT_STATUS_TIME_OUT) ) {
                        connFlag = true;
                        break;
                    } else if ( StringUtils.contains(cResult, Constants.RESULT_STATUS_TIME_OUT2) ) {
                        connFlag = true;
                        break;
                    } else if ( StringUtils.contains(cResult, Constants.RESULT_STATUS_AUTH_FAIL) ) {
                        connFlag = true;
                        break;
                    } else if ( StringUtils.contains(cResult, Constants.RESULT_STATUS_FILE_NOT_FOUND) ) {
                        connFlag = true;
                        break;
                    }
                }
            }
            LOGGER.info("connFlag :: {}", CommonUtils.loggerReplace(connFlag));
            if (connFlag) {
                clusterLogService.saveClusterLog(clusterId, mpSeq, TerramanConstant.TERRAFORM_SSH_CONNECTION_TIME_OUT);
                clusterService.updateCluster(clusterId, TerramanConstant.CLUSTER_FAIL_STATUS);
                return errorResult;
            }

            LOGGER.info("ssh connection complete");
        } catch (Exception e) {
            LOGGER.info("ssh connection fail");
        }


        clusterLogService.saveClusterLog(clusterId, mpSeq, TerramanConstant.TERRAFORM_SUCCESS_LOG);
        mpSeq += 1;
        return mpSeq;
    }

    public int terramanProcessSetKubespray(int mpSeq, String clusterId, String processGb, String host, String idRsa, String provider, String clusterName) {
        /**************************************************************************************************************************************
         * 7. Kubespray ???????????? ??? kubespray_var.sh ?????? ????????????
         *
         * - command
         * TERRAFORM_CHANGE_DIRECTORY_COMMAND = "cd /paas-ta-container-platform-deployment/standalone/aws";
         * TERRAFORM_KUBESPRAY_COMMAND = "#!/bin/bash \\n\\n";
         *
         * - log
         * KUBESPRAY_CONFIG_LOG = "Configuration information update for cluster configuration has been completed.";
         * ************************************************************************************************************************************/
        this.dirCheck("7. current directory :: {}", TerramanConstant.MOVE_DIR_KUBESPRAY, clusterId, host,idRsa);
        TerramanCommandModel terramanCommandModel = new TerramanCommandModel();
        String cResult = "";


        List<InstanceModel> instanceList = instanceService.getInstances(clusterId, provider, host, idRsa, processGb);
        if(instanceList.size() > 0) {
            int workerCnt = instanceList.size()-1;
            int workerSeq = 1;
            StringBuilder sb = new StringBuilder();
            sb.append(TerramanConstant.TERRAFORM_KUBESPRAY_COMMAND);
            for(InstanceModel obj : instanceList) {
                String line = "";
                if( obj.getResourceName().contains("master") ) {
                    line = "export MASTER_NODE_HOSTNAME=" + obj.getInstanceName()
                            + "\\n"
                            + "export MASTER_NODE_PUBLIC_IP=" + obj.getPublicIp()
                            + "\\n"
                            + "export MASTER_NODE_PRIVATE_IP=" + obj.getPrivateIp();
                }
                sb.append(line);
            }

            sb.append("\\n\\n" + "export WORKER_NODE_CNT=" + workerCnt + "\\n");

            for(InstanceModel obj : instanceList) {
                String line = "";
                if( !obj.getResourceName().contains("master") ) {
                    line = "\\n"
                            + TerramanConstant.KUBERSPRAY_VARS_EXPORT_WORKER + workerSeq
                            + TerramanConstant.KUBERSPRAY_VARS_HOSTNAME + obj.getInstanceName()
                            + "\\n"
                            + TerramanConstant.KUBERSPRAY_VARS_EXPORT_WORKER + workerSeq
                            + TerramanConstant.KUBERSPRAY_VARS_PUBLIC_IP + obj.getPublicIp()
                            + "\\n"
                            + TerramanConstant.KUBERSPRAY_VARS_EXPORT_WORKER + workerSeq
                            + TerramanConstant.KUBERSPRAY_VARS_PRIVATE_IP + obj.getPrivateIp();
                    workerSeq++;
                }
                sb.append(line);
            }

            sb.append("\\n\\n" + TerramanConstant.KUBERSPRAY_VARS_PRIVATE_KEY + clusterName + "-key");

            terramanCommandModel.setCommand("7");
            terramanCommandModel.setHost(host);
            terramanCommandModel.setIdRsa(idRsa);
            terramanCommandModel.setUserName(TerramanConstant.DEFAULT_USER_NAME);
            terramanCommandModel.setClusterId(clusterId);
            terramanCommandModel.setContents(sb.toString());
            cResult = commandService.execCommandOutput(terramanCommandModel);
            if(StringUtils.equals(Constants.RESULT_STATUS_FAIL, cResult)) {
                clusterLogService.saveClusterLog(clusterId, mpSeq, TerramanConstant.TERRAFORM_CREATE_CLUSTER_FILE_ERROR);
                clusterService.updateCluster(clusterId, TerramanConstant.CLUSTER_FAIL_STATUS);
                return errorResult;
            }
        } else {
            clusterLogService.saveClusterLog(clusterId, mpSeq, TerramanConstant.TERRAFORM_NOT_EXISTS_INSTANCES_ERROR);
            clusterService.updateCluster(clusterId, TerramanConstant.CLUSTER_FAIL_STATUS);
            return errorResult;
        }
        // log ??????
        clusterLogService.saveClusterLog(clusterId, mpSeq, TerramanConstant.KUBESPRAY_CONFIG_LOG);
        mpSeq += 1;
        return mpSeq;
    }

    public int terramanProcessExecKubespray(int mpSeq, String clusterId, String host, String idRsa) {
        /**************************************************************************************************************************************
         * 8. source deploy_kubespray.sh ????????????
         *
         * - command
         * KUBESPRAY_CHMOD_COMMAND = "chmod +x /home/ubuntu/paas-ta-container-platform-deployment/standalone/single_control_plane/deploy-cp-cluster.sh";
         * CLUSTER_KUBESPRAY_DEPLOY_COMMAND = "source /home/ubuntu/paas-ta-container-platform-deployment/standalone/single_control_plane/deploy-cp-cluster.sh";
         *
         * - log
         * KUBESPRAY_DEPLOY_LOG = "The provisioning of the cluster is complete.";
         * ************************************************************************************************************************************/
        this.dirCheck("8. current directory :: {}", TerramanConstant.MOVE_DIR_KUBESPRAY, clusterId, host,idRsa);
        TerramanCommandModel terramanCommandModel = new TerramanCommandModel();
        terramanCommandModel.setCommand("8");
        terramanCommandModel.setDir(TerramanConstant.MOVE_DIR_KUBESPRAY);
        terramanCommandModel.setHost(host);
        terramanCommandModel.setIdRsa(idRsa);
        terramanCommandModel.setUserName(TerramanConstant.DEFAULT_USER_NAME);
        terramanCommandModel.setClusterId(clusterId);
        String cResult = "";


        cResult = commandService.execCommandOutput(terramanCommandModel);
        LOGGER.info("terraform change mode :: {}", CommonUtils.loggerReplace(cResult));
        if(StringUtils.equals(Constants.RESULT_STATUS_FAIL, cResult)) {
            clusterLogService.saveClusterLog(clusterId, mpSeq, TerramanConstant.TERRAFORM_CHANGE_MODE_ERROR);
            clusterService.updateCluster(clusterId, TerramanConstant.CLUSTER_FAIL_STATUS);
            return errorResult;
        }

        terramanCommandModel.setCommand("9");
        cResult = commandService.execCommandOutput(terramanCommandModel);
        LOGGER.info("terraform deploy cluster :: {}", CommonUtils.loggerReplace(cResult));
        if(StringUtils.equals(Constants.RESULT_STATUS_FAIL, cResult)) {
            clusterLogService.saveClusterLog(clusterId, mpSeq, TerramanConstant.TERRAFORM_DEPLOY_CLUSTER_ERROR);
            clusterService.updateCluster(clusterId, TerramanConstant.CLUSTER_FAIL_STATUS);
            return errorResult;
        }
        return mpSeq;
    }

    public int terramanProcessCreateVault(int mpSeq, String clusterId, String processGb, String host, String idRsa, String provider, String clusterName) {
        /**************************************************************************************************************************************
         * 9. ???????????? ?????? vault ??????
         * clusterId = clusterId
         * clusterApiUrl = https://{ publicIp }:6443
         * clusterToken =
         *  - kubectl create serviceaccount k8sadmin -n kube-system
         *  - kubectl create clusterrolebinding k8sadmin --clusterrole=cluster-admin --serviceaccount=kube-system:k8sadmin
         *  - kubectl describe serviceaccount k8sadmin -n kube-system | grep 'Mountable secrets'      -->     SECRET_NAME ??? ??????
         *  - kubectl describe secret {SECRET_NAME} -n kube-system | grep -E '^token' | cut -f2 -d':' | tr -d " "
         * ************************************************************************************************************************************/
        this.dirCheck("9. current directory :: {}", TerramanConstant.MOVE_DIR_KUBESPRAY, clusterId, host,idRsa);
        TerramanCommandModel terramanCommandModel = new TerramanCommandModel();
        String cResult = "";
        String accountCreate = "";
        String accountBinding = "";
        String chkCli = "";

        InstanceModel instanceInfo = instanceService.getInstance(clusterId, provider, host, idRsa, processGb);

        terramanCommandModel.setCommand("17");
        terramanCommandModel.setHost(instanceInfo.getPublicIp());
        terramanCommandModel.setIdRsa(TerramanConstant.CLUSTER_PRIVATE_KEY(clusterName));
        terramanCommandModel.setUserName(TerramanConstant.DEFAULT_USER_NAME);
        terramanCommandModel.setClusterId(clusterId);
        chkCli = commandService.execCommandOutput(terramanCommandModel);
        LOGGER.info("Cluster Check one :: {}", CommonUtils.loggerReplace(chkCli));

        terramanCommandModel.setCommand("18");
        chkCli = commandService.execCommandOutput(terramanCommandModel);
        LOGGER.info("Cluster Check two :: {}", CommonUtils.loggerReplace(chkCli));

        terramanCommandModel.setCommand("10");
        for(int i=0; i<5; i++) {
            accountCreate = commandService.execCommandOutput(terramanCommandModel);
            LOGGER.info("Account Create :: {}", CommonUtils.loggerReplace(accountCreate.trim()));

            if( (!StringUtils.isBlank(accountCreate.trim()))
                    && !(StringUtils.equals(Constants.RESULT_STATUS_FAIL, accountCreate.trim()))
                    && !(StringUtils.contains(accountCreate.trim(), Constants.RESULT_STATUS_TIME_OUT))
            ) {
                break;
            } else if( (!StringUtils.isBlank(accountCreate.trim()))
                    && ( StringUtils.equals(Constants.RESULT_STATUS_FAIL, accountCreate.trim())
                    || StringUtils.contains(accountCreate.trim(), Constants.RESULT_STATUS_TIME_OUT) )
            ) {
                LOGGER.info(CommonUtils.loggerReplace(TerramanConstant.TERRAFORM_CREATE_SERVICE_ACCOUNT_ERROR));
                clusterLogService.saveClusterLog(clusterId, mpSeq, TerramanConstant.TERRAFORM_CREATE_SERVICE_ACCOUNT_ERROR);
                clusterService.updateCluster(clusterId, TerramanConstant.CLUSTER_FAIL_STATUS);
                return errorResult;
            }
        }

        terramanCommandModel.setCommand("11");

        for(int i=0; i<5; i++) {
            accountBinding = commandService.execCommandOutput(terramanCommandModel);
            LOGGER.info("Account Binding :: {}", CommonUtils.loggerReplace(accountBinding.trim()));

            if( (!StringUtils.isBlank(accountBinding.trim()))
                    && !(StringUtils.equals(Constants.RESULT_STATUS_FAIL, accountBinding.trim()))
                    && !(StringUtils.contains(accountBinding.trim(), Constants.RESULT_STATUS_TIME_OUT))
            ) {
                break;
            } else if( (!StringUtils.isBlank(accountBinding.trim()))
                    && ( StringUtils.equals(Constants.RESULT_STATUS_FAIL, accountBinding.trim())
                    || StringUtils.contains(accountBinding.trim(), Constants.RESULT_STATUS_TIME_OUT) )
            ) {
                LOGGER.info(CommonUtils.loggerReplace(TerramanConstant.TERRAFORM_BIND_ROLE_ERROR));
                clusterLogService.saveClusterLog(clusterId, mpSeq, TerramanConstant.TERRAFORM_BIND_ROLE_ERROR);
                clusterService.updateCluster(clusterId, TerramanConstant.CLUSTER_FAIL_STATUS);
                return errorResult;
            }
        }

        terramanCommandModel.setCommand("13");

        for(int i=0; i<5; i++) {
            cResult = commandService.execCommandOutput(terramanCommandModel);
            LOGGER.info("Service Account Token :: {}", CommonUtils.loggerReplace(cResult.trim()));

            if( (!StringUtils.isBlank(cResult.trim()))
                    && !(StringUtils.equals(Constants.RESULT_STATUS_FAIL, cResult.trim()))
                    && !(StringUtils.contains(cResult.trim(), Constants.RESULT_STATUS_TIME_OUT))
            ) {
                break;
            } else if( (!StringUtils.isBlank(cResult.trim()))
                    && ( StringUtils.equals(Constants.RESULT_STATUS_FAIL, cResult.trim())
                    || StringUtils.contains(cResult.trim(), Constants.RESULT_STATUS_TIME_OUT) )
            ) {
                LOGGER.info(CommonUtils.loggerReplace(TerramanConstant.TERRAFORM_GET_CLUSTER_TOKEN_ERROR));
                clusterLogService.saveClusterLog(clusterId, mpSeq, TerramanConstant.TERRAFORM_GET_CLUSTER_TOKEN_ERROR);
                clusterService.updateCluster(clusterId, TerramanConstant.CLUSTER_FAIL_STATUS);
                return errorResult;
            }
        }

        if(StringUtils.isNotBlank(cResult.trim())) {
            Object resultClusterInfo = vaultService.write(
                    propertyService.getVaultClusterTokenPath().replace("{id}", clusterId)
                    , new ClusterInfo(
                            clusterId
                            , propertyService.getVaultClusterApiUrl().replace("{ip}", instanceInfo.getPublicIp())
                            , ( StringUtils.isNotBlank(cResult) ? cResult.trim() : cResult )
                    )
            );

            if(resultClusterInfo == null) {
                clusterLogService.saveClusterLog(clusterId, mpSeq, TerramanConstant.TERRAFORM_CREATE_TOKEN_ERROR);
                clusterService.updateCluster(clusterId, TerramanConstant.CLUSTER_FAIL_STATUS);
                return errorResult;
            }
        }
        LOGGER.info("cluster token ?????? ?????????????????????.");
        return mpSeq;
    }

    public int terramanProcessClusterStatusUpdate(int mpSeq, String clusterId) {
        /**************************************************************************************************************************************
         * 10. ???????????? ?????? ?????? ?????? --> DB ????????????
         * ************************************************************************************************************************************/

        ClusterModel updateResult = clusterService.updateCluster(clusterId, TerramanConstant.CLUSTER_COMPLETE_STATUS);
        if(updateResult == null) {
            clusterLogService.saveClusterLog(clusterId, mpSeq, TerramanConstant.TERRAFORM_COMPLETE_CLUSTER_ERROR);
            clusterService.updateCluster(clusterId, TerramanConstant.CLUSTER_FAIL_STATUS);
            return errorResult;
        }
        return mpSeq;
    }

    private void dirCheck(String message, String dir, String clusterId, String host, String idRsa) {
        TerramanCommandModel terramanCommandModel = new TerramanCommandModel();
        terramanCommandModel.setCommand("2");
        terramanCommandModel.setDir(dir);
        terramanCommandModel.setHost(host);
        terramanCommandModel.setIdRsa(idRsa);
        terramanCommandModel.setUserName(TerramanConstant.DEFAULT_USER_NAME);
        terramanCommandModel.setClusterId(clusterId);
        LOGGER.info(CommonUtils.loggerReplace(message), CommonUtils.loggerReplace(commandService.execCommandOutput(terramanCommandModel)));
    }
}
