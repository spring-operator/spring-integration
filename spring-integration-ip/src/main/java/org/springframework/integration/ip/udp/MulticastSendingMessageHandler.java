/*
 * Copyright 2001-2016 the original author or authors.
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

package org.springframework.integration.ip.udp;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;

/**
 * A {@link MessageHandler} implementation that maps a Message into
 * a UDP datagram packet and sends that to the specified multicast address
 * (224.0.0.0 to 239.255.255.255) and port.
 *
 * The only difference between this and its super class is the
 * ability to specify how many acknowledgments are required to
 * determine success.
 *
 * @author Gary Russell
 * @since 2.0
 */
public class MulticastSendingMessageHandler extends UnicastSendingMessageHandler {

	private int timeToLive = -1;

	private String localAddress;

	private volatile MulticastSocket multicastSocket;

	/**
	 * Constructs a MulticastSendingMessageHandler to send data to the multicast address/port.
	 * @param address The multicast address.
	 * @param port The port.
	 */
	public MulticastSendingMessageHandler(String address, int port) {
		super(address, port);
	}

	/**
	 * Constructs a MulticastSendingMessageHandler to send data to the multicast address/port
	 * and enables setting the lengthCheck option (if set, a length is prepended to the packet and checked
	 * at the destination).
	 * @param address The multicast address.
	 * @param port The port.
	 * @param lengthCheck Enable the lengthCheck option.
	 */
	public MulticastSendingMessageHandler(String address, int port, boolean lengthCheck) {
		super(address, port, lengthCheck);
	}


	/**
	 * Constructs a MulticastSendingMessageHandler to send data to the multicast address/port
	 * and enables setting the acknowledge option, where the destination sends a receipt acknowledgment.
	 * @param address The multicast address.
	 * @param port The port.
	 * @param acknowledge Whether or not acknowledgments are required.
	 * @param ackHost The host to which acknowledgments should be sent; required if acknowledge is true.
	 * @param ackPort The port to which acknowledgments should be sent; required if acknowledge is true.
	 * @param ackTimeout How long to wait (milliseconds) for an acknowledgment.
	 */
	public MulticastSendingMessageHandler(String address, int port,
			boolean acknowledge, String ackHost, int ackPort, int ackTimeout) {
		super(address, port, acknowledge, ackHost, ackPort, ackTimeout);
	}

	/**
	 * Constructs a MulticastSendingMessageHandler to send data to the multicast address/port
	 * and enables setting the acknowledge option, where the destination sends a receipt acknowledgment.
	 * @param address The multicast address.
	 * @param port The port.
	 * @param lengthCheck Enable the lengthCheck option.
	 * @param acknowledge Whether or not acknowledgments are required.
	 * @param ackHost The host to which acknowledgments should be sent; required if acknowledge is true.
	 * @param ackPort The port to which acknowledgments should be sent; required if acknowledge is true.
	 * @param ackTimeout How long to wait (milliseconds) for an acknowledgment.
	 */
	public MulticastSendingMessageHandler(String address, int port,
			boolean lengthCheck, boolean acknowledge, String ackHost,
			int ackPort, int ackTimeout) {
		super(address, port, lengthCheck, acknowledge, ackHost, ackPort, ackTimeout);
	}

	@Override
	protected synchronized DatagramSocket getSocket() throws IOException {
		if (getTheSocket() == null) {
			createSocket();
		}
		return getTheSocket();
	}

	private void createSocket() throws IOException {
		MulticastSocket socket;
		if (this.isAcknowledge()) {
			int ackPort = this.getAckPort();
			if (this.localAddress == null) {
				socket = ackPort == 0 ? new MulticastSocket() : new MulticastSocket(ackPort);
			}
			else {
				InetAddress whichNic = InetAddress.getByName(this.localAddress);
				socket = new MulticastSocket(new InetSocketAddress(whichNic, ackPort));
			}
			if (getSoReceiveBufferSize() > 0) {
				socket.setReceiveBufferSize(this.getSoReceiveBufferSize());
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Listening for acks on port: " + socket.getLocalPort());
			}
			setSocket(socket);
			updateAckAddress();
		}
		else {
			socket = new MulticastSocket();
			setSocket(socket);
		}
		if (this.timeToLive >= 0) {
			socket.setTimeToLive(this.timeToLive);
		}
		setSocketAttributes(socket);
		this.multicastSocket = socket;
		if (this.localAddress != null) {
			InetAddress whichNic = InetAddress.getByName(this.localAddress);
			socket.setInterface(whichNic);
		}
	}


	/**
	 * If acknowledge = true; how many acks needed for success.
	 * @param minAcksForSuccess The minimum number of acks that will represent success.
	 */
	public void setMinAcksForSuccess(int minAcksForSuccess) {
		this.setAckCounter(minAcksForSuccess);
	}

	/**
	 * Set the underlying {@link MulticastSocket} time to live property.
	 * @param timeToLive {@link MulticastSocket#setTimeToLive(int)}
	 */
	public void setTimeToLive(int timeToLive) {
		this.timeToLive = timeToLive;
	}

	@Override
	public void setLocalAddress(String localAddress) {
		this.localAddress = localAddress;
	}

	@Override
	protected void convertAndSend(Message<?> message) throws Exception {
		super.convertAndSend(message);
		if (logger.isDebugEnabled()) {
			logger.debug("Sent packet to " + this.multicastSocket.getInterface());
		}
	}

}
