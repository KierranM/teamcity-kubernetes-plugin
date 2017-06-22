package ekoshkin.teamcity.clouds.kubernetes.podSpec;

import ekoshkin.teamcity.clouds.kubernetes.KubeCloudClientParameters;
import ekoshkin.teamcity.clouds.kubernetes.KubeCloudException;
import ekoshkin.teamcity.clouds.kubernetes.KubeCloudImage;
import ekoshkin.teamcity.clouds.kubernetes.KubeContainerEnvironment;
import ekoshkin.teamcity.clouds.kubernetes.auth.KubeAuthStrategyProvider;
import ekoshkin.teamcity.clouds.kubernetes.connector.KubeApiConnectorImpl;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.UUID;

/**
 * Created by ekoshkin (koshkinev@gmail.com) on 15.06.17.
 */
public class DeploymentPodTemplateProvider implements PodTemplateProvider {
    private KubeAuthStrategyProvider myAuthStrategies;

    public DeploymentPodTemplateProvider(KubeAuthStrategyProvider authStrategies) {
        myAuthStrategies = authStrategies;
    }

    @NotNull
    @Override
    public String getId() {
        return "deployment-base";
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return "Use pod template from deployment";
    }

    @Nullable
    @Override
    public String getDescription() {
        return null;
    }

    @NotNull
    @Override
    public Pod getPodTemplate(@NotNull CloudInstanceUserData cloudInstanceUserData, @NotNull KubeCloudImage kubeCloudImage, @NotNull KubeCloudClientParameters kubeClientParams) {
        String sourceDeploymentName = kubeCloudImage.getSourceDeploymentName();
        if(StringUtil.isEmpty(sourceDeploymentName))
            throw new KubeCloudException("Deployment name is not set in kubernetes cloud image " + kubeCloudImage.getId());

        KubeApiConnectorImpl kubeApiConnector = new KubeApiConnectorImpl(kubeClientParams, myAuthStrategies.get(kubeClientParams.getAuthStrategy()));

        //TODO:cache api call result
        Deployment sourceDeployment = kubeApiConnector.getDeployment(sourceDeploymentName);
        if(sourceDeployment == null)
            throw new KubeCloudException("Can't find source deployment by name " + sourceDeploymentName);

        final String agentNameProvided = cloudInstanceUserData.getAgentName();
        final String agentName = StringUtil.isEmpty(agentNameProvided) ? UUID.randomUUID().toString() : agentNameProvided;
        final String serverAddress = cloudInstanceUserData.getServerAddress();

        PodTemplateSpec podTemplateSpec = sourceDeployment.getSpec().getTemplate();

        ObjectMeta metadata = podTemplateSpec.getMetadata();
        metadata.setName(agentName);
        metadata.setNamespace(kubeClientParams.getNamespace());
        PodSpec spec = podTemplateSpec.getSpec();
        for (Container container : spec.getContainers()){
            container.setName(agentName);
            container.setEnv(Arrays.asList(
                    new EnvVar(KubeContainerEnvironment.AGENT_NAME, agentName, null),
                    new EnvVar(KubeContainerEnvironment.SERVER_URL, serverAddress, null),
                    new EnvVar(KubeContainerEnvironment.OFFICIAL_IMAGE_SERVER_URL, serverAddress, null),
                    new EnvVar(KubeContainerEnvironment.IMAGE_NAME, kubeCloudImage.getName(), null),
                    new EnvVar(KubeContainerEnvironment.INSTANCE_NAME, agentName, null)));
        }
        return new PodBuilder()
                .withMetadata(metadata)
                .withSpec(spec)
                .build();
    }
}