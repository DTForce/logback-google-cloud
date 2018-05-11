/**
 * Copyright (C) 2018 - Jan Mares <jan.mares@dtforce.com>
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dtforce.tools.logging;

import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TestingAppender<E> extends AppenderBase<E>
{

	private Encoder<E> encoder;

	private Queue<String> globalQueue = new ConcurrentLinkedQueue<>();

	public void setLayout(Layout<E> layout)
	{
		LayoutWrappingEncoder<E> lwe = new LayoutWrappingEncoder<>();
		lwe.setLayout(layout);
		lwe.setContext(context);
		this.encoder = lwe;
	}

	@Override
	protected void append(E eventObject)
	{
		if (!isStarted()) {
			return;
		}

		byte[] byteArray = this.encoder.encode(eventObject);
		globalQueue.add(new String(byteArray));
	}

	public void stop()
	{
		super.stop();
	}

	public Queue<String> getQueueAndClear()
	{
		final Queue<String> globalQueue = this.globalQueue;
		this.globalQueue = new ConcurrentLinkedQueue<>();
		return globalQueue;
	}

}
