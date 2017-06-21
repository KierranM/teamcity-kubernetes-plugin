package ekoshkin.teamcity.clouds.kubernetes;

import com.google.common.collect.Maps;
import ekoshkin.teamcity.clouds.kubernetes.connector.KubeApiConnector;
import ekoshkin.teamcity.clouds.kubernetes.podSpec.PodTemplateProvider;
import ekoshkin.teamcity.clouds.kubernetes.podSpec.PodTemplateProviders;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClientException;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.serverSide.AgentDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by ekoshkin (koshkinev@gmail.com) on 27.05.17.
 */
public class KubeCloudClient implements CloudClientEx {
    private final KubeApiConnector myApiConnector;
    private final ConcurrentHashMap<String, KubeCloudImage> myImageNameToImageMap;
    private final ConcurrentHashMap<String, KubeCloudImage> myImageIdToImageMap;
    private final KubeCloudClientParametersImpl myKubeClientParams;
    private final PodTemplateProviders myPodTemplateProviders;
    private CloudErrorInfo myCurrentError = null;

    public KubeCloudClient(@NotNull final KubeApiConnector apiConnector,
                           @NotNull List<KubeCloudImage> images,
                           @NotNull KubeCloudClientParametersImpl kubeClientParams,
                           @NotNull PodTemplateProviders podTemplateProviders) {
        myApiConnector = apiConnector;
        myImageNameToImageMap = new ConcurrentHashMap<>(Maps.uniqueIndex(images, kubeCloudImage -> kubeCloudImage.getName()));
        myImageIdToImageMap = new ConcurrentHashMap<>(Maps.uniqueIndex(images, kubeCloudImage -> kubeCloudImage.getId()));
        myKubeClientParams = kubeClientParams;
        myPodTemplateProviders = podTemplateProviders;
    }

    @Override
    public boolean isInitialized() {
        //TODO: wait while all images populate list of their instances
        return true;
    }

    @Override
    public void dispose() {
    }

    @NotNull
    @Override
    public CloudInstance startNewInstance(@NotNull CloudImage cloudImage, @NotNull CloudInstanceUserData cloudInstanceUserData) throws QuotaException {
        final KubeCloudImage kubeCloudImage = (KubeCloudImage) cloudImage;
        PodTemplateProvider podTemplateProvider = myPodTemplateProviders.get(kubeCloudImage.getPodSpecMode());
        final Pod podTemplate = podTemplateProvider.getPodTemplate(cloudInstanceUserData, kubeCloudImage, myKubeClientParams);

        try {
            final Pod newPod = myApiConnector.createPod(podTemplate);
            myCurrentError = null;
            final KubeCloudInstanceImpl newInstance = new KubeCloudInstanceImpl(kubeCloudImage, newPod, myApiConnector);
            kubeCloudImage.addInstance(newInstance);
            return newInstance;
        } catch (KubernetesClientException ex){
            myCurrentError = new CloudErrorInfo("Failed to start pod", ex.getMessage(), ex);
            throw ex;
        }
    }

    @Override
    public void restartInstance(@NotNull CloudInstance cloudInstance) {
        throw new UnsupportedOperationException("Restart not implemented");
    }

    @Override
    public void terminateInstance(@NotNull CloudInstance cloudInstance) {
        KubeCloudInstance kubeCloudInstance = (KubeCloudInstance) cloudInstance;
        kubeCloudInstance.terminate();
        //TODO: update instance counter
    }

    @Nullable
    @Override
    public CloudImage findImageById(@NotNull String imageId) throws CloudException {
        return myImageIdToImageMap.get(imageId);
    }

    @Nullable
    @Override
    public CloudInstance findInstanceByAgent(@NotNull AgentDescription agentDescription) {
        final String imageName = agentDescription.getAvailableParameters().get(KubeAgentProperties.IMAGE_NAME);
        if (imageName != null) {
            final KubeCloudImage cloudImage = myImageNameToImageMap.get(imageName);
            if (cloudImage != null) {
                return cloudImage.findInstanceById(agentDescription.getAvailableParameters().get(KubeAgentProperties.INSTANCE_NAME));
            }
        }
        return null;
    }

    @NotNull
    @Override
    public Collection<? extends CloudImage> getImages() throws CloudException {
        return Collections.unmodifiableCollection(myImageNameToImageMap.values());
    }

    @Nullable
    @Override
    public CloudErrorInfo getErrorInfo() {
        return myCurrentError;
    }

    @Override
    public boolean canStartNewInstance(@NotNull CloudImage cloudImage) {
        //TODO: count instances, look into kubeapi
        //TODO: introdice limit
        //TODO: check cluster resource quota -> https://kubernetes.io/docs/tasks/administer-cluster/apply-resource-quota-limit/
        return true;
    }

    @Nullable
    @Override
    public String generateAgentName(@NotNull AgentDescription agentDescription) {
        return agentDescription.getAvailableParameters().get(KubeAgentProperties.INSTANCE_NAME);
    }
}