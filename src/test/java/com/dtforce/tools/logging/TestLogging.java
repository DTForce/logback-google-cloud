/**
 * Copyright (C) 2018 - Jan Mares <jan.mares@dtforce.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dtforce.tools.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

@SuppressWarnings("unchecked")
public class TestLogging
{
	private static final org.slf4j.Logger log;

	private final Gson gson = new Gson();

	static {
		// set up new properties object
		// from file "myProperties.txt"
		InputStream propFile = TestLogging.class.getResourceAsStream("/application.properties");
		Properties p = new Properties(System.getProperties());
		try {
			p.load(propFile);
		} catch (IOException e) {
			e.printStackTrace();
		}

		// set the system properties
		System.setProperties(p);
		// display new properties
		System.getProperties().list(System.out);

		log = org.slf4j.LoggerFactory.getLogger(TestLogging.class);
	}

	private TestingAppender<ILoggingEvent> testingAppender;

	@Before
	public void setUp()
	{
		ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)
			LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);

		testingAppender = (TestingAppender<ILoggingEvent>) root.getAppender("TEST_APPENDER");
		testingAppender.getQueueAndClear();
	}

	@Test
	public void testLine()
	{
		log.trace("My log.");
		log.debug("My log.");
		log.info("My log.");
		log.warn("My log.");
		log.error("My log.");

		Queue<String> queue = testingAppender.getQueueAndClear();
		assertThat(queue.size()).isEqualTo(5);
		for (String s : queue) {
			assertValidEntry(s, map -> assertThat(map).contains(entry("message", "My log.")));
		}
	}

	@Test
	public void testMDC()
	{
		try (MDC.MDCCloseable ignored = MDC.putCloseable("additionalInfo", "zcx")) {
			log.trace("My log.");
			log.debug("My log.");
			log.info("My log.");
			log.warn("My log.");
			log.error("My log.");
		}

		Queue<String> queue = testingAppender.getQueueAndClear();
		assertThat(queue.size()).isEqualTo(5);
		for (String s : queue) {
			assertValidEntry(s, map -> {
				assertThat(map).contains(entry("message", "My log."));
				assertThat((Map)map.get("details")).contains(entry("additionalInfo", "zcx"));
			});
		}
	}

	@Test
	public void testLoads()
	{
		long stopWatch = System.currentTimeMillis();

		for (int i = 0; i < 100000; i++) {
			log.trace("My log. {}", i);
			log.debug("My log. {}", i);
			log.info("My log. {}", i);
			log.warn("My log. {}", i);
			log.error("My log. {}", i);
		}

		long millis = stopWatch - System.currentTimeMillis();
		System.out.print("Time to log entry (avg. microseconds): ");
		System.out.println(millis / 500);

		Queue<String> queue = testingAppender.getQueueAndClear();
		assertThat(queue.size()).isEqualTo(500000);
		int i = 0;
		for (String s : queue) {
			final int finalI = i;
			assertValidEntry(s, map -> assertThat((String)map.get("message")).isEqualTo("My log. " + (finalI / 5)));
			i++;
		}
	}

	@Test
	public void testLoadsParallel() throws InterruptedException
	{
		ExecutorService executorService = Executors.newFixedThreadPool(4);

		long stopWatch = System.currentTimeMillis();

		for (int j = 0; j < 100000; j++) {
			final int i = j;
			executorService.submit(() -> {
				MDC.put("iterator", String.valueOf(i));
				log.trace("My log. {}", i);
				log.debug("My log. {}", i);
				log.info("My log. {}", i);
				log.warn("My log. {}", i);
				log.error("My log. {}", i);
				MDC.remove("iterator");
			});
		}

		long millis = 0;

		executorService.shutdown();
		boolean allDone = false;
		for (int i = 0; i < 30; i++) {
			allDone = executorService.awaitTermination(1, TimeUnit.SECONDS);
			if (allDone) {
				millis = stopWatch - System.currentTimeMillis();
				break;
			}
		}
		assertThat(allDone).isTrue();

		System.out.print("Time to log entry (avg. microseconds): ");
		System.out.println(millis / 500);

		Queue<String> queue = testingAppender.getQueueAndClear();
		assertThat(queue.size()).isEqualTo(500000);
		for (String s : queue) {
			assertValidEntry(s, map -> {
				String strIterator = (String)((Map)map.get("details")).get("iterator");
				assertThat((String)map.get("message")).isEqualTo("My log. " + strIterator);
			});
		}
	}

	private void assertValidEntry(String entry, Consumer<Map> assertMapLambda)
	{
		Map entryMap = gson.fromJson(entry, Map.class);
		assertThat(entryMap).containsKeys(
			"severity",
			"timestamp",
			"serviceContext",
			"message",
			"thread"
		);

		assertThat((Map)entryMap.get("timestamp")).containsKeys("seconds", "nanos");

		// https://cloud.google.com/logging/docs/reference/v2/rest/v2/LogEntry#LogSeverity
		assertThat((String)entryMap.get("severity")).isIn(
			ImmutableSet.of(
				"DEFAULT", "DEBUG", "INFO", "NOTICE", "WARNING", "ERROR", "CRITICAL", "ALERT", "EMERGENCY"
			)
		);

		assertThat((Map)entryMap.get("serviceContext"))
			.contains(entry("service", "someApp"))
			.contains(entry("version", "v1.0.1"));

		assertMapLambda.accept(entryMap);
	}

}
