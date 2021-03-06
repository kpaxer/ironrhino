package org.ironrhino.core.spring.configuration;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.ironrhino.core.util.AppInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.classreading.AnnotationMetadataReadingVisitor;

import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;

class ServiceImplementationCondition implements Condition {

	private Logger logger = LoggerFactory.getLogger(getClass());

	private static Properties services = new Properties();

	private static Set<String> set = new HashSet<>();

	static {
		Resource resource = new ClassPathResource("services.properties");
		if (resource.exists()) {
			try (InputStream is = resource.getInputStream()) {
				services.load(is);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		resource = new ClassPathResource("services." + AppInfo.getStage().name() + ".properties");
		if (resource.exists()) {
			try (InputStream is = resource.getInputStream()) {
				services.load(is);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
		if (context.getEnvironment() != null) {
			Map<String, Object> attrs = metadata
					.getAnnotationAttributes(ServiceImplementationConditional.class.getName());
			if (attrs != null) {
				String serviceInterfaceName = null;
				String className = ((AnnotationMetadataReadingVisitor) metadata).getClassName();
				Class<?> serviceInterface = (Class<?>) attrs.get("serviceInterface");
				if (serviceInterface != void.class) {
					serviceInterfaceName = serviceInterface.getName();
				} else {
					try {
						ClassPool classPool = ClassPool.getDefault();
						classPool.insertClassPath(new ClassClassPath(this.getClass()));
						CtClass cc = classPool.get(className);
						while (!cc.getName().equals(Object.class.getName())) {
							CtClass[] interfaces = cc.getInterfaces();
							if (interfaces.length > 0) {
								serviceInterfaceName = interfaces[0].getName();
								break;
							}
							cc = cc.getSuperclass();
						}
					} catch (Throwable e) {
						e.printStackTrace();
					}
				}
				if (serviceInterfaceName != null) {
					String implementationClassName = services.getProperty(serviceInterfaceName);
					if (StringUtils.isNotBlank(implementationClassName)) {
						boolean matched = implementationClassName.equals(className);
						if (matched) {
							String key = serviceInterfaceName + "=" + implementationClassName;
							if (!set.contains(key)) {
								logger.info("Select implementation {} for service {}", implementationClassName,
										serviceInterfaceName);
								set.add(key);
							}
						}
						return matched;
					}
				}
				String[] profiles = (String[]) attrs.get("profiles");
				if (profiles.length == 0 || context.getEnvironment().acceptsProfiles(profiles)) {
					return true;
				}
				return false;
			}
		}
		return true;
	}

}
