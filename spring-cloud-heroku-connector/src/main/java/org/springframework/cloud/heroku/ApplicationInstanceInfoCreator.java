package org.springframework.cloud.heroku;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.springframework.cloud.app.ApplicationInstanceInfo;
import org.springframework.cloud.app.BasicApplicationInstanceInfo;
import org.springframework.cloud.util.EnvironmentAccessor;

/**
 * Application instance info creator.
 * <p>
 * Relies on SPRING_CLOUD_APP_NAME environment being set (using commands such as
 * <code>heroku config:add SPRING_CLOUD_APP_NAME=myappname --app myappname</code>
 *  
 * @author Ramnivas Laddad
 *
 */
public class ApplicationInstanceInfoCreator {
	private static final Logger logger = Logger.getLogger(ApplicationInstanceInfoCreator.class.getName());

	private final EnvironmentAccessor environment;

	public ApplicationInstanceInfoCreator(EnvironmentAccessor environmentAccessor) {
		this.environment = environmentAccessor;
	}
	
	public ApplicationInstanceInfo createApplicationInstanceInfo() {
		String appname = environment.getEnvValue("SPRING_CLOUD_APP_NAME");
		if (appname == null) {
			logger.warning("Environment variable SPRING_CLOUD_APP_NAME not set. App name set to <unknown>");
			appname = "<unknown>";
		}
		
		String dyno = environment.getEnvValue("DYNO");

		Map<String,Object> appProperties = new HashMap<>();
		appProperties.put("port", environment.getEnvValue("PORT"));
		appProperties.put("host", environment.getHost());
		
		return new BasicApplicationInstanceInfo(dyno, appname, appProperties);
	}
}
