/**
 * Copyright (C) 2018 - Jan Mares <jan.mares@dtforce.com>
 * Copyright (C) 2016 - Ankur Chauhan <ankur@malloc64.com>
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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.CallerData;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.contrib.json.JsonLayoutBase;
import com.google.common.collect.ImmutableMap;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * Google cloud logging v2 json layout
 */
public class GoogleCloudLoggingV2Layout extends JsonLayoutBase<ILoggingEvent>
{

	public static final String SOURCE_LOCATION_FIELD_KEY = "logging.googleapis.com/sourceLocation";

	/**
	 * Add shift in types to use all stackdriver types. See:
	 *
	 * https://cloud.google.com/logging/docs/reference/v2/rest/v2/LogEntry#LogSeverity
	 */
	public static final Map<Level, String> levelMapping = ImmutableMap.<Level, String>builder()
		.put(Level.TRACE, "DEBUG")
		.put(Level.DEBUG, "INFO")
		.put(Level.INFO, "NOTICE")
		.put(Level.WARN, "WARNING")
		.put(Level.ERROR, "ERROR")
		.build();

	private final ThreadLocal<MapBuffer> preAllocatedBuffer = new ThreadLocal<>();

	private final ThrowableProxyConverter tpc;

	private Map<String, String> serviceContext;

	public GoogleCloudLoggingV2Layout()
	{
		appendLineSeparator = true;
		tpc = new ThrowableProxyConverter();
		tpc.setOptionList(Collections.singletonList("full"));
	}

	@Override
	public void start()
	{
		tpc.start();
		super.start();

		serviceContext = ImmutableMap.<String, String>builder()
			.put("service", System.getProperty("application.name", "default"))
			.put("version", System.getProperty("application.version", "default"))
			.build();
	}

	@Override
	public void stop()
	{
		tpc.stop();
		super.stop();
	}

	@Override
	protected Map toJsonMap(ILoggingEvent event)
	{
		Map<Object, Object> root = getPreAllocatedBuffer().getRoot();

		root.put("severity", getSeverity(event));
		root.put("timestamp", getTime(event));
		root.put("serviceContext", getServiceContext());
		root.put("message", getMessage(event));

		Map<String, Object> sourceLocation = getSourceLocation(event);
		if (sourceLocation != null && !sourceLocation.isEmpty()) {
			root.put(SOURCE_LOCATION_FIELD_KEY, sourceLocation);
		}

		root.put("thread", event.getThreadName());
		root.put("logger", event.getLoggerName());

		if (event.getMDCPropertyMap() != null && !event.getMDCPropertyMap().isEmpty()) {
			root.put("details", event.getMDCPropertyMap());
		}

		return root;
	}

	private String getMessage(ILoggingEvent event)
	{
		String message = event.getFormattedMessage();

		String stackTrace = tpc.convert(event);
		if (stackTrace == null || stackTrace.length() == 0) {
			return message;
		}
		return message + "\n" + stackTrace;
	}

	private Map<String, Object> getSourceLocation(ILoggingEvent event)
	{
		StackTraceElement[] cda = event.getCallerData();
		Map<String, Object> sourceLocation = getPreAllocatedBuffer().getSourceLocation();
		if (cda != null && cda.length > 0) {
			StackTraceElement ste = cda[0];

			sourceLocation.put(
				"function",
				ste.getClassName() + "." + ste.getMethodName() + (ste.isNativeMethod() ? "(Native Method)" : "")
			);
			if (ste.getFileName() != null) {
				String pkg = ste.getClassName().replaceAll("\\.", "/");
				pkg = pkg.substring(0, pkg.lastIndexOf("/") + 1);
				sourceLocation.put("file", pkg + ste.getFileName());
			}
			sourceLocation.put("line", ste.getLineNumber());
		} else {
			sourceLocation.put("file", CallerData.NA);
			sourceLocation.put("line", CallerData.LINE_NA);
			sourceLocation.put("function", CallerData.NA);
		}
		return sourceLocation;
	}

	private Map<String, Object> getTime(ILoggingEvent event)
	{
		Map<String, Object> time = getPreAllocatedBuffer().getTime();
		Instant ts = Instant.ofEpochMilli(event.getTimeStamp());
		time.put("seconds", ts.getEpochSecond());
		time.put("nanos", ts.getNano());
		return time;
	}

	private static String getSeverity(final ILoggingEvent event)
	{
		return levelMapping.getOrDefault(event.getLevel(), "DEFAULT");
	}

	private Map<String, String> getServiceContext()
	{
		return this.serviceContext;
	}

	private MapBuffer getPreAllocatedBuffer()
	{
		if (preAllocatedBuffer.get() == null) {
			preAllocatedBuffer.set(new MapBuffer());
		}
		return preAllocatedBuffer.get();
	}

}
