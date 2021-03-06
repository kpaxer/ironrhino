package org.ironrhino.core.remoting.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.PreDestroy;

import org.ironrhino.core.event.InstanceLifecycleEvent;
import org.ironrhino.core.event.InstanceShutdownEvent;
import org.ironrhino.core.event.InstanceStartupEvent;
import org.ironrhino.core.remoting.ExportServicesEvent;
import org.ironrhino.core.remoting.Remoting;
import org.ironrhino.core.remoting.ServiceRegistry;
import org.ironrhino.core.util.AppInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;

public abstract class AbstractServiceRegistry implements ServiceRegistry {

	protected Logger logger = LoggerFactory.getLogger(getClass());

	@Autowired
	private ConfigurableApplicationContext ctx;

	protected Map<String, List<String>> importServices = new ConcurrentHashMap<>();

	protected Map<String, Object> exportServices = new HashMap<>();

	protected String host;

	public Map<String, List<String>> getImportServices() {
		return importServices;
	}

	@Override
	public Map<String, Object> getExportServices() {
		return exportServices;
	}

	public void init() {
		host = AppInfo.getHostAddress() + ":" + (AppInfo.getHttpPort() > 0 ? AppInfo.getHttpPort() : DEFAULT_PORT);
		prepare();
		String[] beanNames = ctx.getBeanDefinitionNames();
		for (String beanName : beanNames) {
			BeanDefinition bd = ctx.getBeanFactory().getBeanDefinition(beanName);
			if (!bd.isSingleton())
				continue;
			String beanClassName = bd.getBeanClassName();
			if (beanClassName == null)
				continue;
			Class<?> clazz = null;
			try {
				clazz = Class.forName(beanClassName);
			} catch (Exception e) {
				logger.error(e.getMessage(), e);
				continue;
			}
			if (beanClassName.startsWith("org.ironrhino.core.remoting.client.") && beanClassName.endsWith("Client")) {
				// remoting_client
				String serviceName = (String) bd.getPropertyValues().getPropertyValue("serviceInterface").getValue();
				importServices.put(serviceName, new ArrayList<String>());
			} else {
				export(clazz, beanName, beanClassName);
			}
		}
		for (String serviceName : exportServices.keySet())
			register(serviceName);
		for (String serviceName : importServices.keySet())
			lookup(serviceName);
		onReady();
	}

	private void export(Class<?> clazz, String beanName, String beanClassName) {
		if (!clazz.isInterface()) {
			Remoting remoting = clazz.getAnnotation(Remoting.class);
			if (remoting != null) {
				Class<?>[] classes = remoting.value();
				if (classes.length == 0) {
					Class<?>[] interfaces = clazz.getInterfaces();
					if (interfaces.length > 0)
						classes = interfaces;
				}
				if (classes.length == 0) {
					logger.warn("@Remoting on concrete class [{}] must assign interfaces to export services",
							clazz.getName());
				} else {
					for (Class<?> inte : classes) {
						if (!inte.isInterface()) {
							logger.warn("class [{}] in @Remoting on class [{}] must be interface", inte.getName(),
									clazz.getName());
						} else if (!inte.isAssignableFrom(clazz)) {
							logger.warn(" class [{}] must implements interface [{}] in @Remoting", clazz.getName(),
									inte.getName());
						} else {
							exportServices.put(inte.getName(), ctx.getBean(beanName));
							logger.info(" exported service [{}] for bean [{}#{}]", inte.getName(), beanClassName,
									beanName);
						}
					}
				}
			}
			Class<?>[] interfaces = clazz.getInterfaces();
			if (interfaces != null) {
				for (Class<?> inte : interfaces) {
					export(inte, beanName, beanClassName);
				}
			}
		} else {
			Remoting remoting = clazz.getAnnotation(Remoting.class);
			if (remoting != null) {
				exportServices.put(clazz.getName(), ctx.getBean(beanName));
				logger.info(" exported service [{}] for bean [{}#{}]", clazz.getName(), beanClassName, beanName);
			}
			for (Class<?> c : clazz.getInterfaces())
				export(c, beanName, beanClassName);
		}
	}

	@Override
	public void evict(String host) {
		for (Map.Entry<String, List<String>> entry : importServices.entrySet()) {
			List<String> list = entry.getValue();
			if (!list.isEmpty())
				list.remove(host);
		}
	}

	@Override
	public String discover(String serviceName) {
		List<String> hosts = getImportServices().get(serviceName);
		if (hosts != null && hosts.size() > 0) {
			String host = hosts.get(ThreadLocalRandom.current().nextInt(hosts.size()));
			onDiscover(serviceName, host);
			return host;
		} else {
			return null;
		}

	}

	@Override
	public void register(String serviceName) {
		doRegister(serviceName, host);
	}

	@Override
	public void unregister(String serviceName) {
		doUnregister(serviceName, host);
	}

	protected void onDiscover(String serviceName, String host) {
		logger.info("discovered " + serviceName + "@" + host);
	}

	protected void onRegister(String serviceName, String host) {
		logger.info("registered " + serviceName + "@" + host);
	}

	protected void onUnregister(String serviceName, String host) {
		logger.info("unregistered " + serviceName + "@" + host);
	}

	protected void prepare() {

	}

	protected void onReady() {

	}

	protected void lookup(String serviceName) {

	}

	protected void doRegister(String serviceName, String host) {

	}

	protected void doUnregister(String serviceName, String host) {

	}

	@PreDestroy
	public void destroy() {
		for (String serviceName : exportServices.keySet())
			unregister(serviceName);
	}

	@EventListener
	public void onApplicationEvent(InstanceLifecycleEvent event) {
		if (handle(event))
			return;
		if (event instanceof InstanceStartupEvent && event.isLocal())
			init();
		else if (event instanceof InstanceShutdownEvent && !event.isLocal()) {
			String host = event.getHost();
			if (event.getHttpPort() > 0)
				host += ":" + event.getHttpPort();
			evict(host);
		} else if (event instanceof ExportServicesEvent) {
			if (event.isLocal())
				return;
			ExportServicesEvent ev = (ExportServicesEvent) event;
			String host = ev.getHost();
			for (String serviceName : ev.getExportServices()) {
				List<String> hosts = importServices.get(serviceName);
				if (hosts != null && !hosts.contains(host))
					hosts.add(host);
			}
		}
	}

	protected boolean handle(InstanceLifecycleEvent event) {
		return false;
	}

}
