package org.springframework.cloud;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.logging.Logger;

import javax.activation.DataSource;

import org.springframework.cloud.app.ApplicationInstanceInfo;
import org.springframework.cloud.service.CompositeServiceInfo;
import org.springframework.cloud.service.ServiceConnectorConfig;
import org.springframework.cloud.service.ServiceConnectorCreator;
import org.springframework.cloud.service.ServiceInfo;
import org.springframework.cloud.service.ServiceInfo.ServiceLabel;
import org.springframework.cloud.service.ServiceInfo.ServiceProperty;

/**
 * The main user-level API access to application and services for the app in which this instance is embedded in.
 *
 * The class connects application and the underlying cloud. Besides passing along information about the application
 * instance, it allows simple access for using services. It uses {@link ServiceInfo}s obtained using the
 * underlying {@link CloudConnector} and allows translating those to service connector such as a {@link DataSource}.
 *
 * It also passes along information obtained through {@link CloudConnector} to let application take control on how
 * to use bound services.
 *
 * <p>
 * NOTE: Users or cloud providers shouldn't need to instantiate an instance of this class (constructor has package-access only for
 * unit-testing purpose). Instead, they can obtain an appropriate instance through {@link CloudFactory}
 * </p>
 *
 * @author Ramnivas Laddad
 *
 */
public class Cloud {
	private final CloudConnector cloudConnector;
	private final ServiceConnectorCreatorRegistry serviceConnectorCreatorRegistry = new ServiceConnectorCreatorRegistry();

	/**
	 * Package-access constructor.
	 *
	 * @param cloudConnector the underlying connector
	 * @param serviceConnectorCreators service connector creators
	 */
	Cloud(CloudConnector cloudConnector, List<ServiceConnectorCreator<?, ? extends ServiceInfo>> serviceConnectorCreators) {
		this.cloudConnector = cloudConnector;

		for (ServiceConnectorCreator<?, ? extends ServiceInfo> serviceCreator : serviceConnectorCreators) {
			registerServiceConnectorCreator(serviceCreator);
		}
	}

	/**
	 * @see CloudConnector#getApplicationInstanceInfo()
	 *
	 * @return information about the application instance
	 */
	public ApplicationInstanceInfo getApplicationInstanceInfo() {
		return cloudConnector.getApplicationInstanceInfo();
	}

	/**
	 * Get {@link ServiceInfo} for the given service id
	 *
	 * @param serviceId service id
	 * @return info for the serviceId
	 */
	public ServiceInfo getServiceInfo(String serviceId) {
		for (ServiceInfo serviceInfo : getServiceInfos()) {
			if (serviceInfo.getId().equals(serviceId)) {
				return serviceInfo;
			}
		}
		throw new CloudException("No service with id " + serviceId + " found");
	}

	/**
	 * @see CloudConnector#getServiceInfos()
	 * @return information about all services bound to the application
	 */
	public List<ServiceInfo> getServiceInfos() {
		return flatten(cloudConnector.getServiceInfos());
	}

	/**
	 * Get {@link ServiceInfo}s for the bound services that could be mapped to the given service connector type.
	 *
	 * <p>
	 * For example, if the connector type is {@link DataSource}, then the method will return all {@link ServiceInfo} objects
	 * matching bound relational database services.
	 * <p>
	 *
	 * @param <T> The class of the connector to find services for.
	 * @param serviceConnectorType service connector type.
	 *            Passing null returns all {@link ServiceInfo}s (matching that of {@link Cloud#getServiceInfos()}
	 * @return information about services bound to the application that could be transformed into the given connector type
	 */
	public <T> List<ServiceInfo> getServiceInfos(Class<T> serviceConnectorType) {
		List<ServiceInfo> allServiceInfos = getServiceInfos();
		List<ServiceInfo> matchingServiceInfos = new ArrayList<>();

		for (ServiceInfo serviceInfo : allServiceInfos) {
			if (serviceConnectorCreatorRegistry.canCreate(serviceConnectorType, serviceInfo)) {
				matchingServiceInfos.add(serviceInfo);
			}
		}

		return matchingServiceInfos;
	}

	/**
	 * Get all {@link ServiceInfo}s for the given service info type.
	 * 
	 * <p>
	 * Unlike {@link #getServiceInfos(Class)} which checks if the service info
	 * can be mapped to the given service connector type, this method only
	 * checks the type of the service info.
	 * 
	 * @param <T> the class of service info to return
	 * @param serviceInfoType
	 *            service info type
	 * @return a list of service info of the given type
	 */
	@SuppressWarnings("unchecked")
	public <T extends ServiceInfo> List<T> getServiceInfosByType(Class<T> serviceInfoType) {
		List<ServiceInfo> allServiceInfos = getServiceInfos();

		List<T> matchingServiceInfos = new ArrayList<>();
		for (ServiceInfo serviceInfo : allServiceInfos) {
			if (serviceInfoType.isAssignableFrom(serviceInfo.getClass())) {
				matchingServiceInfos.add((T) serviceInfo);
			}
		}

		return matchingServiceInfos;
	}

	/**
	 * Get the singleton {@link ServiceInfo} for the given service info type.
	 * 
	 * @param <T> the class of service info to return
	 * @param serviceInfoType
	 *            service info type
	 * @return the single service info of the given type
	 * @throws CloudException
	 *             if there are either 0 or more than 1 service info of the
	 *             given type.
	 */
	public <T extends ServiceInfo> T getSingletonServiceInfoByType(Class<T> serviceInfoType) {
		List<T> serviceInfos = getServiceInfosByType(serviceInfoType);
		if (serviceInfos.size() != 1) {
			throw new CloudException(
					"No unique service info " + serviceInfoType + " found. Expected 1, found " + serviceInfos.size());
		}
		return serviceInfos.get(0);
	}

	/**
	 * Get a service connector for the given service id, the connector type, configured with the given config
	 *
	 *
	 * @param <SC> The class of the service connector to return.
	 * @param serviceId the service id
	 * @param serviceConnectorType The expected class of service connector such as, DataSource.class.
	 * @param serviceConnectorConfig service connector configuration (such as pooling parameters).
	 * @return a service connector of the specified type with the given configuration applied
	 *
	 */
	public <SC> SC getServiceConnector(String serviceId, Class<SC> serviceConnectorType,
		ServiceConnectorConfig serviceConnectorConfig) {
		ServiceInfo serviceInfo = getServiceInfo(serviceId);

		return getServiceConnector(serviceInfo, serviceConnectorType, serviceConnectorConfig);
	}

	/**
	 * Get the singleton service connector for the given connector type, configured with the given config
	 *
	 * @param <SC> The class of the service connector to return.
	 * @param serviceConnectorType The expected class of service connector such as, DataSource.class.
	 * @param serviceConnectorConfig service connector configuration (such as pooling parameters).
	 * @return the single service connector of the specified type with the given configuration applied
	 *
	 */
	public <SC> SC getSingletonServiceConnector(Class<SC> serviceConnectorType, ServiceConnectorConfig serviceConnectorConfig) {
		List<ServiceInfo> matchingServiceInfos = getServiceInfos(serviceConnectorType);

		if (matchingServiceInfos.size() != 1) {
			throw new CloudException("No unique service matching " + serviceConnectorType + " found. Expected 1, found "
				+ matchingServiceInfos.size());
		}

		ServiceInfo matchingServiceInfo = matchingServiceInfos.get(0);

		return getServiceConnector(matchingServiceInfo, serviceConnectorType, serviceConnectorConfig);
	}

	/**
	 * Register a new service connector creator
	 *
	 * @param serviceConnectorCreator the service connector to register
	 */
	public void registerServiceConnectorCreator(ServiceConnectorCreator<?, ? extends ServiceInfo> serviceConnectorCreator) {
		serviceConnectorCreatorRegistry.registerCreator(serviceConnectorCreator);
	}

	/**
	 * Get properties for app and services.
	 *
	 *
	 *
	 * <p>
	 * Application properties always include <code>cloud.application.app-id</code> and <code>cloud.application.instance-id</code>
	 * with values bound to application id and instance id. The rest of the properties are cloud-provider specific, but take the
	 * <code>cloud.application.&lt;property-name&gt;</code> form. <pre>
	 * cloud.application.app-id = helloworld
	 * cloud.application.instance-id = instance-0-0fab098f
	 * cloud.application.&lt;property-name&gt; = &lt;property-value&gt;
	 * </pre>
	 *
	 * <p>
	 * Service specific properties are exposed for each bound service, with each key starting in <code>cloud.services</code>. Like
	 * application properties, these too are cloud and service specific. Each key for a specific service starts with
	 * <code>cloud.services.&lt;service-id&gt;</code> <pre>
	 * cloud.services.customerDb.type = mysql-5.1
	 * cloud.services.customerDb.plan = free
	 * cloud.services.customerDb.connection.hostname = ...
	 * cloud.services.customerDb.connection.port = ...
	 * etc...
	 * </pre>
	 *
	 * <p>
	 * If a there is only a single service of a given type (as defined by the {link ServiceInfo.ServiceLabel}
	 * annoation's value of the corresponding {@link ServiceInfo} class), that service is aliased
	 * to the service type. Keys for such properties start in <code>cloud.services.&lt;service-type&gt;</code>.
	 * For example, if there is only a single MySQL service bound to the application, the service properties
	 * will also be exposed starting with '<code>cloud.services.mysql</code>' key: <pre>
	 * cloud.services.mysql.type = mysql-5.1
	 * cloud.services.mysql.plan = free
	 * cloud.services.mysql.connection.hostname = ...
	 * cloud.services.mysql.connection.port = ...
	 * etc...
	 * </pre>
	 *
	 * @return the properties object
	 */
	public Properties getCloudProperties() {
		Map<String, List<ServiceInfo>> mappedServiceInfos = new HashMap<>();
		for (ServiceInfo serviceInfo : getServiceInfos()) {
			String key = getServiceLabel(serviceInfo);
			List<ServiceInfo> serviceInfosForLabel = mappedServiceInfos.computeIfAbsent(key, k -> new ArrayList<>());
			serviceInfosForLabel.add(serviceInfo);
		}

		final String servicePropKeyLead = "cloud.services.";
		Properties cloudProperties = new Properties();
		for (Entry<String, List<ServiceInfo>> mappedServiceInfo : mappedServiceInfos.entrySet()) {
			List<ServiceInfo> serviceInfos = mappedServiceInfo.getValue();

			for (ServiceInfo serviceInfo : serviceInfos) {
				String idBasedKey = servicePropKeyLead + serviceInfo.getId();
				cloudProperties.putAll(getServiceProperties(idBasedKey, serviceInfo));

				// If there is only one service for a given label, put props with that label instead of just id
				if (serviceInfos.size() == 1) {
					String labelBasedKey = servicePropKeyLead + mappedServiceInfo.getKey();
					cloudProperties.putAll(getServiceProperties(labelBasedKey, serviceInfo));
				}
			}
		}

		cloudProperties.putAll(getAppProperties());

		return cloudProperties;
	}

	private <SC> SC getServiceConnector(ServiceInfo serviceInfo, Class<SC> serviceConnectorType,
		ServiceConnectorConfig serviceConnectorConfig) {
		ServiceConnectorCreator<SC, ServiceInfo> serviceConnectorCreator = serviceConnectorCreatorRegistry.getServiceCreator(
			serviceConnectorType, serviceInfo);
		return serviceConnectorCreator.create(serviceInfo, serviceConnectorConfig);
	}

	private Properties getAppProperties() {
		final String appPropLeadKey = "cloud.application.";

		Properties appProperties = new Properties();
		addProperty(appProperties, appPropLeadKey + "instance-id", getApplicationInstanceInfo().getInstanceId());
		addProperty(appProperties, appPropLeadKey + "app-id", getApplicationInstanceInfo().getAppId());
		if (getApplicationInstanceInfo().getProperties() != null) {
			for (Map.Entry<String, Object> entry : getApplicationInstanceInfo().getProperties().entrySet()) {
				if (entry.getValue() != null) {
					addProperty(appProperties, appPropLeadKey + entry.getKey(), entry.getValue());
				}
			}
		}

		return appProperties;
	}

	private void addProperty(Properties appProperties, String key, Object value) {
		if (value != null) {
			appProperties.put(key, value);
		}
	}

	private Properties getServiceProperties(String keyLead, ServiceInfo serviceInfo) {
		Properties cloudProperties = new Properties();

		try {
			BeanInfo beanInfo = Introspector.getBeanInfo(serviceInfo.getClass());
			PropertyDescriptor[] propDescriptors = beanInfo.getPropertyDescriptors();
			for (PropertyDescriptor propDescriptor : propDescriptors) {
				ServiceProperty propAnnotation = propDescriptor.getReadMethod().getAnnotation(ServiceProperty.class);
				String key = keyLead;

				if (propAnnotation != null) {
					if (!propAnnotation.category().isEmpty()) {
						key = key + "." + propAnnotation.category();
					}
					if (!propAnnotation.name().isEmpty()) {
						key = key + "." + propAnnotation.name();
					} else {
						key = key + "." + propDescriptor.getName().toLowerCase();
					}

					Object value = propDescriptor.getReadMethod().invoke(serviceInfo);

					if (value != null) {
						cloudProperties.put(key, value);
					}
				}
			}
		} catch (Exception e) {
			throw new CloudException(e);
		}

		return cloudProperties;
	}

	private static String getServiceLabel(ServiceInfo serviceInfo) {
		Class<? extends ServiceInfo> serviceInfoClass = serviceInfo.getClass();

		ServiceLabel labelAnnotation = serviceInfoClass.getAnnotation(ServiceInfo.ServiceLabel.class);

		if (labelAnnotation == null) {
			return null;
		} else {
			return labelAnnotation.value();
		}
	}

	private static List<ServiceInfo> flatten(List<ServiceInfo> serviceInfos) {
		List<ServiceInfo> flattened = new ArrayList<>();

		for (ServiceInfo serviceInfo : serviceInfos) {
			if (serviceInfo instanceof CompositeServiceInfo) {
				// recursively flatten any CompositeServiceInfos
				CompositeServiceInfo compositeServiceInfo = (CompositeServiceInfo)serviceInfo;
				flattened.addAll(flatten(compositeServiceInfo.getServiceInfos()));
			} else {
				flattened.add(serviceInfo);
			}
		}

		return flattened;
	}


}

class ServiceConnectorCreatorRegistry {
	private static final Logger logger = Logger.getLogger(Cloud.class.getName());

	private final List<ServiceConnectorCreator<?, ? extends ServiceInfo>> serviceConnectorCreators = new ArrayList<>();

	public void registerCreator(ServiceConnectorCreator<?, ? extends ServiceInfo> serviceConnectorCreator) {
		serviceConnectorCreators.add(serviceConnectorCreator);
	}

	public <SC, SI extends ServiceInfo> ServiceConnectorCreator<SC, SI> getServiceCreator(Class<SC> serviceConnectorType,
		SI serviceInfo) {
		ServiceConnectorCreator<SC, SI> serviceConnectorCreator = getServiceCreatorOrNull(serviceConnectorType, serviceInfo);

		if (serviceConnectorCreator != null) {
			return serviceConnectorCreator;
		} else {
			throw new CloudException("No suitable ServiceConnectorCreator found: "
				+ "service id=" + serviceInfo.getId() + ", "
				+ "service info type=" + serviceInfo.getClass().getName() + ", "
				+ "connector type=" + serviceConnectorType);
		}
	}

	public <SC, SI extends ServiceInfo> boolean canCreate(Class<SC> serviceConnectorType, SI serviceInfo) {
		return getServiceCreatorOrNull(serviceConnectorType, serviceInfo) != null;
	}

	public boolean accept(ServiceConnectorCreator<?, ? extends ServiceInfo> creator, Class<?> serviceConnectorType,
		ServiceInfo serviceInfo) {
		boolean typeBasedAccept = serviceConnectorType == null ||
                serviceConnectorType.isAssignableFrom(creator.getServiceConnectorType());
		boolean infoBasedAccept = serviceInfo == null ||
                creator.getServiceInfoType().isAssignableFrom(serviceInfo.getClass());

		return typeBasedAccept && infoBasedAccept;
	}

	@SuppressWarnings("unchecked")
	private <SC, SI extends ServiceInfo> ServiceConnectorCreator<SC, SI> getServiceCreatorOrNull(Class<SC> serviceConnectorType,
		SI serviceInfo) {
		for (ServiceConnectorCreator<?, ? extends ServiceInfo> serviceConnectorCreator : serviceConnectorCreators) {
			logger.fine("Trying connector creator type " + serviceConnectorCreator);
			if (accept(serviceConnectorCreator, serviceConnectorType, serviceInfo)) {
				return (ServiceConnectorCreator<SC, SI>) serviceConnectorCreator;
			}
		}
		return null;
	}
}
