package com.dianping.puma.servlet;


import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.log4j.Logger;

import com.dianping.puma.ComponentContainer;
import com.dianping.puma.core.util.PumaThreadUtils;

import com.dianping.puma.server.TaskManager;
public class PumaListener implements ServletContextListener {

	private static Logger log = Logger.getLogger(PumaListener.class);

	private TaskManager taskManager;

	private static final String BEAN_SERVERMANAGER = "taskManager";
	
	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		taskManager.stopServers();
	}

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		taskManager =  ComponentContainer.SPRING.lookup(BEAN_SERVERMANAGER);
		log.info("init spring config success.");
		Runtime.getRuntime().addShutdownHook(
				PumaThreadUtils.createThread(new Runnable() {
					@Override
					public void run() {
						taskManager.stopServers();
					}
				}, "ShutdownHook", false));
	}

}