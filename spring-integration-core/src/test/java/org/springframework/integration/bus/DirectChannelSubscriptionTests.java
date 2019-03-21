/*
 * Copyright 2002-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.bus;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.config.annotation.MessagingAnnotationPostProcessor;
import org.springframework.integration.context.IntegrationContextUtils;
import org.springframework.integration.endpoint.EventDrivenConsumer;
import org.springframework.integration.handler.AbstractReplyProducingMessageHandler;
import org.springframework.integration.handler.ServiceActivatingHandler;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.integration.test.util.TestUtils.TestApplicationContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.PollableChannel;
import org.springframework.messaging.support.GenericMessage;

/**
 * @author Mark Fisher
 * @author Artem Bilan
 */
public class DirectChannelSubscriptionTests {

	private TestApplicationContext context = TestUtils.createTestApplicationContext();

	private DirectChannel sourceChannel = new DirectChannel();

	private PollableChannel targetChannel = new QueueChannel();


	@Before
	public void setupChannels() {
		this.context.registerChannel("sourceChannel", this.sourceChannel);
		this.context.registerChannel("targetChannel", this.targetChannel);
	}

	@After
	public void tearDown() {
		this.context.close();
	}


	@Test
	public void sendAndReceiveForRegisteredEndpoint() {
		ServiceActivatingHandler serviceActivator = new ServiceActivatingHandler(new TestBean(), "handle");
		serviceActivator.setOutputChannel(this.targetChannel);
		context.registerBean("testServiceActivator", serviceActivator);
		EventDrivenConsumer endpoint = new EventDrivenConsumer(this.sourceChannel, serviceActivator);
		context.registerEndpoint("testEndpoint", endpoint);
		context.refresh();
		this.sourceChannel.send(new GenericMessage<>("foo"));
		Message<?> response = this.targetChannel.receive();
		assertEquals("foo!", response.getPayload());
	}

	@Test
	public void sendAndReceiveForAnnotatedEndpoint() {
		MessagingAnnotationPostProcessor postProcessor = new MessagingAnnotationPostProcessor();
		postProcessor.setBeanFactory(this.context.getBeanFactory());
		postProcessor.afterPropertiesSet();
		TestEndpoint endpoint = new TestEndpoint();
		postProcessor.postProcessAfterInitialization(endpoint, "testEndpoint");
		this.context.refresh();
		this.sourceChannel.send(new GenericMessage<>("foo"));
		Message<?> response = this.targetChannel.receive();
		assertEquals("foo-from-annotated-endpoint", response.getPayload());
	}

	@Test(expected = MessagingException.class)
	public void exceptionThrownFromRegisteredEndpoint() {
		AbstractReplyProducingMessageHandler handler = new AbstractReplyProducingMessageHandler() {

			@Override
			public Object handleRequestMessage(Message<?> message) {
				throw new RuntimeException("intentional test failure");
			}
		};
		handler.setOutputChannel(targetChannel);
		EventDrivenConsumer endpoint = new EventDrivenConsumer(sourceChannel, handler);
		this.context.registerEndpoint("testEndpoint", endpoint);
		this.context.refresh();
		this.sourceChannel.send(new GenericMessage<>("foo"));
	}

	@Test(expected = MessagingException.class)
	public void exceptionThrownFromAnnotatedEndpoint() {
		QueueChannel errorChannel = new QueueChannel();
		this.context.registerChannel(IntegrationContextUtils.ERROR_CHANNEL_BEAN_NAME, errorChannel);
		MessagingAnnotationPostProcessor postProcessor = new MessagingAnnotationPostProcessor();
		postProcessor.setBeanFactory(this.context.getBeanFactory());
		postProcessor.afterPropertiesSet();
		FailingTestEndpoint endpoint = new FailingTestEndpoint();
		postProcessor.postProcessAfterInitialization(endpoint, "testEndpoint");
		this.context.refresh();
		this.sourceChannel.send(new GenericMessage<>("foo"));
	}


	static class TestBean {

		public Message<?> handle(Message<?> message) {
			return new GenericMessage<>(message.getPayload() + "!");
		}

	}


	@MessageEndpoint
	public static class TestEndpoint {

		@ServiceActivator(inputChannel = "sourceChannel", outputChannel = "targetChannel")
		public Message<?> handle(Message<?> message) {
			return new GenericMessage<>(message.getPayload() + "-from-annotated-endpoint");
		}

	}


	@MessageEndpoint
	public static class FailingTestEndpoint {

		@ServiceActivator(inputChannel = "sourceChannel", outputChannel = "targetChannel")
		public Message<?> handle(Message<?> message) {
			throw new RuntimeException("intentional test failure");
		}

	}

}
