package ekoshkin.teamcity.clouds.kubernetes;

import ekoshkin.teamcity.clouds.kubernetes.auth.KubeAuthStrategyProvider;
import ekoshkin.teamcity.clouds.kubernetes.connector.KubeApiConnector;
import ekoshkin.teamcity.clouds.kubernetes.connector.KubeApiConnectorImpl;
import ekoshkin.teamcity.clouds.kubernetes.podSpec.PodSpecProviders;
import ekoshkin.teamcity.clouds.kubernetes.web.KubeProfileEditController;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by ekoshkin (koshkinev@gmail.com) on 27.05.17.
 */
public class KubeCloudClientFactory implements CloudClientFactory {

    public static final String DISPLAY_NAME = "Kubernetes";
    public static final String ID = "kube"; //should be 6 chars maximum

    private final PluginDescriptor myPluginDescriptor;
    private KubeAuthStrategyProvider myAuthStrategies;
    private PodSpecProviders myPodSpecProviders;

    public KubeCloudClientFactory(@NotNull final CloudRegistrar registrar,
                                  @NotNull final PluginDescriptor pluginDescriptor,
                                  @NotNull final KubeAuthStrategyProvider authStrategies,
                                  @NotNull final PodSpecProviders podSpecProviders) {
        myPluginDescriptor = pluginDescriptor;
        myAuthStrategies = authStrategies;
        myPodSpecProviders = podSpecProviders;
        registrar.registerCloudFactory(this);
    }

    @NotNull
    @Override
    public String getCloudCode() {
        return ID;
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Nullable
    @Override
    public String getEditProfileUrl() {
        return myPluginDescriptor.getPluginResourcesPath(KubeProfileEditController.EDIT_KUBE_HTML);
    }

    @NotNull
    @Override
    public Map<String, String> getInitialParameterValues() {
        return Collections.emptyMap();
    }

    @NotNull
    @Override
    public PropertiesProcessor getPropertiesProcessor() {
        return new KubeProfilePropertiesProcessor();
    }

    @Override
    public boolean canBeAgentOfType(@NotNull AgentDescription agentDescription) {
        final Map<String, String> map = agentDescription.getAvailableParameters();
        return map.containsKey(KubeAgentProperties.IMAGE_NAME) && map.containsKey(KubeAgentProperties.INSTANCE_NAME);
    }

    @NotNull
    @Override
    public CloudClientEx createNewClient(@NotNull CloudState cloudState, @NotNull CloudClientParameters cloudClientParameters) {
        final KubeCloudClientParameters kubeClientParams = KubeCloudClientParameters.create(cloudClientParameters);
        final KubeApiConnector apiConnector = new KubeApiConnectorImpl(kubeClientParams, myAuthStrategies.get(kubeClientParams.getAuthStrategy()));
        List<KubeCloudImage> images = CollectionsUtil.convertCollection(kubeClientParams.getImages(), kubeCloudImageData -> {
            KubeCloudImageImpl kubeCloudImage = new KubeCloudImageImpl(kubeCloudImageData, apiConnector);
            //TODO: defer this
            kubeCloudImage.populateInstances();
            return kubeCloudImage;
        });
        return new KubeCloudClient(apiConnector, images, kubeClientParams, myPodSpecProviders);
    }
}
