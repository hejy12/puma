package com.dianping.puma.checker;

import com.dianping.puma.config.Config;
import com.dianping.puma.core.constant.Operation;
import com.dianping.puma.core.entity.PumaTask;
import com.dianping.puma.core.model.PumaTaskOperation;
import com.dianping.puma.core.monitor.Event;
import com.dianping.puma.core.monitor.EventListener;
import com.dianping.puma.core.monitor.PumaTaskOperationEvent;
import com.dianping.puma.core.service.PumaTaskService;
import com.dianping.puma.server.TaskExecutor;
import com.dianping.puma.server.TaskExecutorContainer;
import com.dianping.puma.server.builder.TaskExecutorBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;

@Service("pumaTaskOperationChecker")
public class PumaTaskOperationChecker implements EventListener {

	public static final Logger LOG = LoggerFactory.getLogger(PumaTaskOperationChecker.class);

	@Autowired
	private Config pumaServerConfig;

	@Autowired
	private PumaTaskService pumaTaskService;

	@Autowired
	private TaskExecutorBuilder taskExecutorBuilder;

	@Autowired
	private TaskExecutorContainer taskExecutorContainer;

	@PostConstruct
	public void init() {
		String pumaServerName = pumaServerConfig.getName();

		// Throws puma task service exceptions.
		List<PumaTask> pumaTasks = pumaTaskService.findByPumaServerName(pumaServerName);

		// Swallows puma task executors exceptions.
		for (PumaTask pumaTask : pumaTasks) {
			try {
				TaskExecutor taskExecutor = taskExecutorBuilder.build(pumaTask);
				taskExecutorContainer.submit(taskExecutor);
			} catch (Exception e) {
				LOG.error("Initialize puma task `{}` error: {}.", pumaTask.getName(), e.getMessage());
			}
		}
	}

	@Override
	public void onEvent(Event event) {
		LOG.info("Receive puma task event!");

		if (event instanceof PumaTaskOperationEvent) {
			LOG.info("Receive puma task operation event.");

			PumaTaskOperationEvent pumaTaskOperationEvent = (PumaTaskOperationEvent) event;
			PumaTaskOperation pumaTaskOperation = pumaTaskOperationEvent.getOperation();
			Operation operation = pumaTaskOperation.getOperation();

			switch (operation) {
			case CREATE:
				LOG.info("Receive puma task operation event: CREATE.");
				taskExecutorContainer.createEvent(pumaTaskOperationEvent);
				break;
			case UPDATE:
				LOG.info("Receive puma task operation event: UPDATE.");
				taskExecutorContainer.updateEvent(pumaTaskOperationEvent);
				break;
			case REMOVE:
				LOG.info("Receive puma task operation event: REMOVE.");
				taskExecutorContainer.removeEvent(pumaTaskOperationEvent);
				break;
			default:
				LOG.error("Receive illegal puma task operation event `{}`.", operation);
			}
		} else {
			LOG.error("Receive illegal puma task event `{}`.", event);
		}
	}
}